/**
 *
 *  Copyright (c) 2017 Dell Inc., or its subsidiaries.
 *
 */
package io.pravega.stream.impl.netty;

import io.pravega.common.ExponentialMovingAverage;
import io.pravega.common.MathHelpers;
import io.pravega.shared.protocol.netty.AppendBatchSizeTracker;
import io.pravega.shared.protocol.netty.AppendSequence;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * See {@link AppendBatchSizeTracker}.
 * 
 * This implementation tracks three things:
 * 1. The time between appends
 * 2. The size of each append
 * 3. The number of unackedAppends there are outstanding
 * 
 * If the number of unacked appends is <= 1 batching is disabled. This improves latency for low volume and synchronus writers.
 * Otherwise the batch size is set to the amount of data that will be written in the next {@link #TARGET_BATCH_TIME_MILLIS}
 */
class AppendBatchSizeTrackerImpl implements AppendBatchSizeTracker {
    private static final int MAX_BATCH_TIME_MILLIS = 100;
    private static final int TARGET_BATCH_TIME_MILLIS = 10;
    private static final int MAX_BATCH_SIZE = 32 * 1024;

    private final Supplier<Long> clock;
    private final AtomicReference<AppendSequence> lastAppend;
    private final AtomicLong lastAppendTime;
    private final AtomicReference<AppendSequence> lastAcked;
    private final ExponentialMovingAverage eventSize = new ExponentialMovingAverage(1024, 0.1, true);
    private final ExponentialMovingAverage millisBetweenAppends = new ExponentialMovingAverage(10, 0.1, false);

    AppendBatchSizeTrackerImpl() {
        clock = System::currentTimeMillis;
        lastAppendTime = new AtomicLong(clock.get());
        lastAppend = new AtomicReference<AppendSequence>(null);
        lastAcked = new AtomicReference<AppendSequence>(null);
    }

    @Override
    public void recordAppend(AppendSequence eventSequence, int size) {
        long now = Math.max(lastAppendTime.get(), clock.get());
        long last = lastAppendTime.getAndSet(now);
        lastAppend.set(eventSequence);
        millisBetweenAppends.addNewSample(now - last);
        eventSize.addNewSample(size);
    }

    @Override
    public void recordAck(AppendSequence ackLevel) {
        lastAcked.set(ackLevel);
    }

    /**
     * Returns a block size that in an estimate of how much data will be written in the next {@link #TARGET_BATCH_TIME_MILLIS}
     */
    @Override
    public int getAppendBlockSize() {
        AppendSequence last = lastAppend.get();
        if (last == null || last.equals(lastAcked.get())) {
            return 0;
        }
        return (int) MathHelpers.minMax((long) (TARGET_BATCH_TIME_MILLIS / millisBetweenAppends.getCurrentValue()
                * eventSize.getCurrentValue()), 0, MAX_BATCH_SIZE);
    }

    @Override
    public int getBatchTimeout() {
        return MAX_BATCH_TIME_MILLIS;
    }
}
