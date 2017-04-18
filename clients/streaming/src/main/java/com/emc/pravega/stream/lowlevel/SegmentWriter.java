package com.emc.pravega.stream.lowlevel;

import java.nio.ByteBuffer;

public interface SegmentWriter {
    
    /**
     * Asynchronously and atomically write the data provided. 
     */
    void write(ByteBuffer data);
    
    /**
     * Asynchronously and atomically write the data provided if an only if the total data written
     * before it is 'atOffset'
     */
    void write(ByteBuffer data, long atOffset);
    
    /**
     * Returns the total number of bytes that will have been stored once all buffers have been flushed.
     * This is useful for computing a value to pass to {@link #write(ByteBuffer, long)}
     */
    long getNondurableLength();
    
    /**
     * @return The total number of bytes that have been durably stored so far.
     */
    long getDurableLength();
    
    /**
     * Block until all outstanding writes are completed.
     */
    void flush();

}
