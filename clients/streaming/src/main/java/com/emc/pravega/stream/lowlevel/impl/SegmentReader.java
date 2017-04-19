package com.emc.pravega.stream.lowlevel.impl;

import com.emc.pravega.common.concurrent.FutureHelpers;
import com.emc.pravega.common.netty.ConnectionFailedException;
import com.emc.pravega.common.netty.FailingReplyProcessor;
import com.emc.pravega.common.netty.PravegaNodeUri;
import com.emc.pravega.common.netty.WireCommands.NoSuchSegment;
import com.emc.pravega.common.netty.WireCommands.ReadSegment;
import com.emc.pravega.common.netty.WireCommands.SegmentRead;
import com.emc.pravega.common.netty.WireCommands.WrongHost;
import com.emc.pravega.common.util.Retry;
import com.emc.pravega.stream.Segment;
import com.emc.pravega.stream.impl.ConnectionClosedException;
import com.emc.pravega.stream.impl.Controller;
import com.emc.pravega.stream.impl.netty.ClientConnection;
import com.emc.pravega.stream.impl.netty.ConnectionFactory;
import com.google.common.base.Preconditions;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.concurrent.GuardedBy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SegmentReader {

    private static final int REQUEST_SIZE = 512 * 1024;
    private final ScheduledThreadPoolExecutor threadPool;
    private final ConnectionFactory connectionFactory;

    private final Object lock = new Object();
    private final OffsetBuffer buffer = new OffsetBuffer(2 * REQUEST_SIZE);
    @GuardedBy("lock")
    private CompletableFuture<ClientConnection> connection = null;
    @GuardedBy("lock")    
    private Long outstandingRequestOffset = null;

    private final ResponseProcessor responseProcessor = new ResponseProcessor();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Controller controller;
    private Segment segment;

    private final class ResponseProcessor extends FailingReplyProcessor {

        @Override
        public void connectionDropped() {
            closeConnection(new ConnectionFailedException());
            issueReadRequestIfNeeded(); //Reconnect and retry
        }

        @Override
        public void wrongHost(WrongHost wrongHost) {
            closeConnection(new ConnectionFailedException(wrongHost.toString()));
        }

        @Override
        public void noSuchSegment(NoSuchSegment noSuchSegment) {
            //TODO: It's not clear how we should be handling this case. (It should be impossible...)
            closeConnection(new IllegalArgumentException(noSuchSegment.toString()));
        }

        @Override
        public void segmentRead(SegmentRead segmentRead) {
            log.trace("Received read result {}", segmentRead);
            synchronized (lock) {
                if (outstandingRequestOffset == segmentRead.getOffset()) {
                    outstandingRequestOffset = null;
                }
            }
            buffer.fill(segmentRead.getOffset(), segmentRead.getData());
            issueReadRequestIfNeeded(); //Read more if needed.
        }
    }

    public SegmentReader(ScheduledThreadPoolExecutor threadPool, Controller controller,
            ConnectionFactory connectionFactory, Segment segment) {
        Preconditions.checkNotNull(threadPool);
        Preconditions.checkNotNull(controller);
        Preconditions.checkNotNull(connectionFactory);
        Preconditions.checkNotNull(segment);
        this.threadPool = threadPool;
        this.controller = controller;
        this.connectionFactory = connectionFactory;
        this.segment = segment;
    }

    private void close() {
        if (closed.compareAndSet(false, true)) {
            closeConnection(new ConnectionClosedException());
        }
    }

    private void closeConnection(Exception exceptionToInflightRequests) {
        log.trace("Closing connection with exception: {}", exceptionToInflightRequests.toString());
        CompletableFuture<ClientConnection> c;
        synchronized (lock) {
            c = connection;
            connection = null;
            outstandingRequestOffset = null;
        }
        if (c != null && FutureHelpers.isSuccessful(c)) {
            try {
                c.getNow(null).close();
            } catch (Exception e) {
                log.warn("Exception tearing down connection: ", e);
            }
        }
    }

    CompletableFuture<ClientConnection> getConnection() {
        synchronized (lock) {
            //Optimistic check
            if (connection != null) {
                return connection;
            }
        }
        return Retry.withExpBackoff(10, 10, 4, 10000).retryingOn(RuntimeException.class).throwingOn(Exception.class).runAsync(() -> {
            return controller.getEndpointForSegment(segment.getScopedName()).thenComposeAsync((PravegaNodeUri uri) -> {
                synchronized (lock) {
                    if (connection == null) {
                        connection = connectionFactory.establishConnection(uri, responseProcessor);
                    }
                    return connection; 
                }
            }, threadPool);
        }, threadPool);
    }

    private void issueReadRequestIfNeeded() {
        if (buffer.capacityAvailable() >= REQUEST_SIZE) {
            getConnection().thenAccept((ClientConnection c) -> {
                ReadSegment request;
                synchronized (lock) {
                    if (outstandingRequestOffset != null) {
                        return;
                    }
                    outstandingRequestOffset = buffer.getWriteOffset();
                    request = new ReadSegment(segment.getScopedName(), outstandingRequestOffset, REQUEST_SIZE);
                }
                log.info("Sending read request {}", request);
                c.sendAsync(request);
            });
        }
    }

    public void run() {
        //TODO: Loop. (Run from thread pool)
        //issueReadRequestIfNeeded()
        //invoke SegmentListener
    }

}
