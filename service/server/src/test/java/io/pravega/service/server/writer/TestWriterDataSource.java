/**
 * Copyright (c) 2017 Dell Inc., or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.pravega.service.server.writer;

import io.pravega.common.Exceptions;
import io.pravega.common.concurrent.FutureHelpers;
import io.pravega.common.function.CallbackHelpers;
import io.pravega.common.util.SequencedItemList;
import io.pravega.service.server.UpdateableContainerMetadata;
import io.pravega.service.server.UpdateableSegmentMetadata;
import io.pravega.service.server.logs.operations.MetadataCheckpointOperation;
import io.pravega.service.server.logs.operations.Operation;
import io.pravega.service.server.logs.operations.StreamSegmentAppendOperation;
import io.pravega.service.storage.LogAddress;
import io.pravega.test.common.ErrorInjector;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import lombok.val;

/**
 * Test version of a WriterDataSource that can accumulate operations in memory (just like the real DurableLog) and only
 * depends on a metadata and a cache as external dependencies.
 * <p>
 * Note that even though it uses an UpdateableContainerMetadata, no changes to this metadata are performed (except recording truncation markers & Sequence Numbers).
 * All other changes (Segment-based) must be done externally.
 */
@ThreadSafe
class TestWriterDataSource implements WriterDataSource, AutoCloseable {
    //region Members

    private final UpdateableContainerMetadata metadata;
    private final SequencedItemList<Operation> log;
    private final ScheduledExecutorService executor;
    private final DataSourceConfig config;
    @GuardedBy("lock")
    private final HashMap<Long, AppendData> appendData;
    @GuardedBy("lock")
    private CompletableFuture<Void> waitFullyAcked;
    @GuardedBy("lock")
    private CompletableFuture<Void> addProcessed;
    private final AtomicLong lastAddedCheckpoint;
    private final AtomicBoolean ackEffective;
    private final AtomicBoolean closed;
    @GuardedBy("lock")
    private Consumer<Long> segmentMetadataRequested;
    @GuardedBy("lock")
    private ErrorInjector<Exception> readSyncErrorInjector;
    @GuardedBy("lock")
    private ErrorInjector<Exception> readAsyncErrorInjector;
    @GuardedBy("lock")
    private ErrorInjector<Exception> ackSyncErrorInjector;
    @GuardedBy("lock")
    private ErrorInjector<Exception> ackAsyncErrorInjector;
    @GuardedBy("lock")
    private ErrorInjector<Exception> getAppendDataErrorInjector;
    @GuardedBy("lock")
    private Consumer<Long> notifyStorageLengthUpdatedCallback;
    @GuardedBy("lock")
    private BiConsumer<Long, Long> completeMergeCallback;
    private final Object lock = new Object();

    //endregion

    //region Constructor

    TestWriterDataSource(UpdateableContainerMetadata metadata, ScheduledExecutorService executor, DataSourceConfig config) {
        Preconditions.checkNotNull(metadata, "metadata");
        Preconditions.checkNotNull(executor, "executor");
        Preconditions.checkNotNull(config, "config");

        this.metadata = metadata;
        this.executor = executor;
        this.config = config;
        this.appendData = new HashMap<>();
        this.log = new SequencedItemList<>();
        this.lastAddedCheckpoint = new AtomicLong(0);
        this.waitFullyAcked = null;
        this.ackEffective = new AtomicBoolean(true);
        this.closed = new AtomicBoolean(false);
    }

    //endregion

    //region AutoCloseable Implementation

    @Override
    public void close() {
        if (!this.closed.getAndSet(true)) {
            // Cancel any pending adds.
            CompletableFuture<Void> addProcessed;
            synchronized (this.lock) {
                addProcessed = this.addProcessed;
                this.addProcessed = null;
            }

            if (addProcessed != null) {
                addProcessed.cancel(true);
            }
        }
    }

    //endregion

    //region add

    public long add(Operation operation) {
        Exceptions.checkNotClosed(this.closed.get(), this);
        Preconditions.checkArgument(operation.getSequenceNumber() < 0, "Given operation already has a sequence number.");

        // If not a checkpoint op, see if we need to auto-add one.
        boolean isCheckpoint = operation instanceof MetadataCheckpointOperation;
        if (!isCheckpoint) {
            if (this.config.autoInsertCheckpointFrequency != DataSourceConfig.NO_METADATA_CHECKPOINT
                    && this.metadata.getOperationSequenceNumber() - this.lastAddedCheckpoint.get() >= this.config.autoInsertCheckpointFrequency) {
                MetadataCheckpointOperation checkpointOperation = new MetadataCheckpointOperation();
                this.lastAddedCheckpoint.set(add(checkpointOperation));
            }
        }

        // Set the Sequence Number, after the possible recursive call to add a checkpoint (to maintain Seq No order).
        operation.setSequenceNumber(this.metadata.nextOperationSequenceNumber());

        // We need to record the Truncation Marker/Point prior to actually adding the operation to the log (because it could
        // get picked up very fast by the Writer, so we need to have everything in place).
        if (isCheckpoint) {
            this.metadata.recordTruncationMarker(operation.getSequenceNumber(), new TestLogAddress(operation.getSequenceNumber()));
            this.metadata.setValidTruncationPoint(operation.getSequenceNumber());
        }

        if (!this.log.add(operation)) {
            throw new IllegalStateException("Sequence numbers out of order.");
        }

        notifyAddProcessed();
        return operation.getSequenceNumber();
    }

    /**
     * Records data for appends (to be fetched with getAppendData()).
     */
    void recordAppend(StreamSegmentAppendOperation operation) {
        AppendData ad;
        synchronized (this.lock) {
            ad = this.appendData.getOrDefault(operation.getStreamSegmentId(), null);
            if (ad == null) {
                ad = new AppendData();
                this.appendData.put(operation.getStreamSegmentId(), ad);
            }
        }

        ad.append(operation.getStreamSegmentOffset(), operation.getData());
    }

    /**
     * Clears all append data.
     */
    void clearAppendData() {
        synchronized (this.lock) {
            this.appendData.clear();
        }
    }

    //endregion

    //region WriterDataSource Implementation

    @Override
    public int getId() {
        return this.metadata.getContainerId();
    }

    @Override
    public CompletableFuture<Void> acknowledge(long upToSequenceNumber, Duration timeout) {
        Exceptions.checkNotClosed(this.closed.get(), this);
        Preconditions.checkArgument(this.metadata.isValidTruncationPoint(upToSequenceNumber), "Invalid Truncation Point. Must refer to a MetadataCheckpointOperation.");
        ErrorInjector<Exception> asyncErrorInjector;
        synchronized (this.lock) {
            ErrorInjector.throwSyncExceptionIfNeeded(this.ackSyncErrorInjector);
            asyncErrorInjector = this.ackAsyncErrorInjector;
        }

        return ErrorInjector
                .throwAsyncExceptionIfNeeded(asyncErrorInjector)
                .thenRunAsync(() -> {
                    if (this.ackEffective.get()) {
                        // ackEffective determines whether the ack operation has any effect or not.
                        this.log.truncate(upToSequenceNumber);
                        this.metadata.removeTruncationMarkers(upToSequenceNumber);
                    }

                    // See if anyone is waiting for the DataSource to be emptied out; if so, notify them.
                    CompletableFuture<Void> callback = null;
                    synchronized (this.lock) {
                        // We need to check both log size and last seq no (that's because of ackEffective that may not actually trim the log).
                        Operation last = this.log.getLast();
                        if (this.waitFullyAcked != null && (last == null || last.getSequenceNumber() <= upToSequenceNumber)) {
                            callback = this.waitFullyAcked;
                            this.waitFullyAcked = null;
                        }
                    }

                    if (callback != null) {
                        callback.complete(null);
                    }
                }, this.executor);
    }

    @Override
    public CompletableFuture<Iterator<Operation>> read(long afterSequenceNumber, int maxCount, Duration timeout) {
        Exceptions.checkNotClosed(this.closed.get(), this);
        ErrorInjector<Exception> asyncErrorInjector;
        synchronized (this.lock) {
            ErrorInjector.throwSyncExceptionIfNeeded(this.readSyncErrorInjector);
            asyncErrorInjector = this.readAsyncErrorInjector;
        }

        return ErrorInjector
                .throwAsyncExceptionIfNeeded(asyncErrorInjector)
                .thenCompose(v -> {
                    Iterator<Operation> logReadResult = this.log.read(afterSequenceNumber, maxCount);
                    if (logReadResult.hasNext()) {
                        // Result is readily available; return it.
                        return CompletableFuture.completedFuture(logReadResult);
                    } else {
                        // Result is not yet available; wait for an add and then retry the read.
                        return waitForAdd(afterSequenceNumber, timeout)
                                .thenComposeAsync(v1 -> this.read(afterSequenceNumber, maxCount, timeout), this.executor);
                    }
                });
    }

    @Override
    public void completeMerge(long targetStreamSegmentId, long sourceStreamSegmentId) {
        val callback = getCompleteMergeCallback();
        if (callback != null) {
            callback.accept(targetStreamSegmentId, sourceStreamSegmentId);
        }
    }

    @Override
    public void notifyStorageLengthUpdated(long streamSegmentId) {
        val callback = getNotifyStorageLengthUpdatedCallback();
        if (callback != null) {
            callback.accept(streamSegmentId);
        }
    }

    @Override
    public InputStream getAppendData(long streamSegmentId, long startOffset, int length) {
        synchronized (this.lock) {
            ErrorInjector.throwSyncExceptionIfNeeded(this.getAppendDataErrorInjector);
        }

        AppendData ad;
        synchronized (this.lock) {
            ad = this.appendData.getOrDefault(streamSegmentId, null);
        }

        if (ad == null) {
            return null;
        }

        return ad.read(startOffset, length);
    }

    @Override
    public boolean isValidTruncationPoint(long operationSequenceNumber) {
        return this.metadata.isValidTruncationPoint(operationSequenceNumber);
    }

    @Override
    public long getClosestValidTruncationPoint(long operationSequenceNumber) {
        return this.metadata.getClosestValidTruncationPoint(operationSequenceNumber);
    }

    @Override
    public void deleteStreamSegment(String streamSegmentName) {
        this.metadata.deleteStreamSegment(streamSegmentName);
    }

    @Override
    public UpdateableSegmentMetadata getStreamSegmentMetadata(long streamSegmentId) {
        Consumer<Long> callback;
        synchronized (this.lock) {
            callback = this.segmentMetadataRequested;
        }

        if (callback != null) {
            CallbackHelpers.invokeSafely(callback, streamSegmentId, null);
        }

        return this.metadata.getStreamSegmentMetadata(streamSegmentId);
    }

    //endregion

    //region Other Properties

    void setSegmentMetadataRequested(Consumer<Long> callback) {
        synchronized (this.lock) {
            this.segmentMetadataRequested = callback;
        }
    }

    void setReadSyncErrorInjector(ErrorInjector<Exception> injector) {
        synchronized (this.lock) {
            this.readSyncErrorInjector = injector;
        }
    }

    void setReadAsyncErrorInjector(ErrorInjector<Exception> injector) {
        synchronized (this.lock) {
            this.readAsyncErrorInjector = injector;
        }
    }

    void setAckSyncErrorInjector(ErrorInjector<Exception> injector) {
        synchronized (this.lock) {
            this.ackSyncErrorInjector = injector;
        }
    }

    void setAckAsyncErrorInjector(ErrorInjector<Exception> injector) {
        synchronized (this.lock) {
            this.ackAsyncErrorInjector = injector;
        }
    }

    void setGetAppendDataErrorInjector(ErrorInjector<Exception> injector) {
        synchronized (this.lock) {
            this.getAppendDataErrorInjector = injector;
        }
    }

    void setNotifyStorageLengthUpdatedCallback(Consumer<Long> callback) {
        synchronized (this.lock) {
            this.notifyStorageLengthUpdatedCallback = callback;
        }
    }

    Consumer<Long> getNotifyStorageLengthUpdatedCallback() {
        synchronized (this.lock) {
            return this.notifyStorageLengthUpdatedCallback;
        }
    }

    void setCompleteMergeCallback(BiConsumer<Long, Long> callback) {
        synchronized (this.lock) {
            this.completeMergeCallback = callback;
        }
    }

    BiConsumer<Long, Long> getCompleteMergeCallback() {
        synchronized (this.lock) {
            return this.completeMergeCallback;
        }
    }

    /**
     * Sets whether the acknowledgements have any effect of actually truncating the inner log.
     */
    void setAckEffective(boolean value) {
        this.ackEffective.set(value);
    }

    /**
     * Returns a CompletableFuture that will be completed when the TestWriterDataSource becomes empty.
     */
    CompletableFuture<Void> waitFullyAcked() {
        synchronized (this.lock) {
            if (this.waitFullyAcked == null) {
                // Nobody else is waiting for the DataSource to empty out.
                if (this.log.getLast() == null) {
                    // We are already empty; return a completed future.
                    return CompletableFuture.completedFuture(null);
                } else {
                    // Not empty yet; create an uncompleted Future and store it.
                    this.waitFullyAcked = new CompletableFuture<>();
                }
            }

            return this.waitFullyAcked;
        }
    }

    //endregion

    //region Helpers

    private CompletableFuture<Void> waitForAdd(long currentSeqNo, Duration timeout) {
        CompletableFuture<Void> result;
        synchronized (this.lock) {
            Operation last = this.log.getLast();
            if (last != null && last.getSequenceNumber() > currentSeqNo) {
                // An add has already been processed that meets or exceeds the given sequence number.
                result = CompletableFuture.completedFuture(null);
            } else {
                if (this.addProcessed == null) {
                    // We need to wait for an add, and nobody else is waiting for it too.
                    this.addProcessed = FutureHelpers.futureWithTimeout(timeout, this.executor);
                    FutureHelpers.onTimeout(this.addProcessed, ex -> {
                        synchronized (this.lock) {
                            if (this.addProcessed.isCompletedExceptionally()) {
                                this.addProcessed = null;
                            }
                        }
                    });
                }

                result = this.addProcessed;
            }
        }

        return result;
    }

    private void notifyAddProcessed() {
        CompletableFuture<Void> f;
        synchronized (this.lock) {
            f = this.addProcessed;
            this.addProcessed = null;
        }

        if (f != null) {
            f.complete(null);
        }
    }

    //endregion

    static class DataSourceConfig {
        static final int NO_METADATA_CHECKPOINT = -1;
        int autoInsertCheckpointFrequency;
    }

    private static class TestLogAddress extends LogAddress {
        TestLogAddress(long sequence) {
            super(sequence);
        }
    }

    @ThreadSafe
    private static class AppendData {
        @GuardedBy("this")
        private final TreeMap<Long, byte[]> data = new TreeMap<>();

        synchronized void append(long segmentOffset, byte[] data) {
            this.data.put(segmentOffset, data);
        }

        synchronized InputStream read(final long segmentOffset, final int length) {
            ArrayList<InputStream> result = new ArrayList<>();

            // Locate first entry.
            long currentOffset = segmentOffset;
            Map.Entry<Long, byte[]> entry = this.data.floorEntry(currentOffset);
            if (entry == null || entry.getKey() + entry.getValue().length <= currentOffset) {
                // Requested offset is before first entry or in a "gap".
                return null;
            }

            int entryOffset = (int) (currentOffset - entry.getKey());
            byte[] entryData = entry.getValue();
            int remainingLength = length;
            while (entryData != null && remainingLength > 0) {
                int entryLength = Math.min(remainingLength, entryData.length - entryOffset);
                result.add(new ByteArrayInputStream(entryData, entryOffset, entryLength));
                currentOffset += entryLength;
                remainingLength -= entryLength;
                entryOffset = 0;
                entryData = this.data.get(currentOffset);
            }

            if (remainingLength > 0) {
                return null;
            }

            return new SequenceInputStream(Iterators.asEnumeration(result.iterator()));
        }
    }
}

