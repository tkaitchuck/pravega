package com.emc.pravega.stream.lowlevel;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public interface SegmentReader {
    
    /**
     * Returns the total length of all data written to the segment thus far.
     */
    CompletableFuture<Long> fetchCurrentStreamLength();

    /**
     * Returns the offset the reader is at now or will be at once all the Futures returned from
     * {@link #read(int)} have completed.
     */
    long getCurrentOffset();
    
    /**
     * Read 'length' bytes from the current offset.
     */
    CompletableFuture<ByteBuffer> read(int length);
}
