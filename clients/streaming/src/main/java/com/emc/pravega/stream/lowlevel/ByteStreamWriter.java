package com.emc.pravega.stream.lowlevel;

import java.io.InputStream;
import java.nio.ByteBuffer;

public interface ByteStreamWriter {
    
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
     * Asynchronously and atomically write all of the data in the provided input stream. This
     * intended for streaming very large writes that need to be atomic. So it does not require the
     * data fit in memory, but it is higher overhead than simply calling {@link #write(ByteBuffer)}.
     * 
     * Note: This will not affect the value of {@link #getNondurableLength()} until the end of the
     * input stream has been reached.
     */
    void write(InputStream in);
    
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

    /**
     * Prohibit any future writes to this stream.
     */
    void seal();
    
    /**
     * Delete this stream and all of its data.
     */
    void delete();
    
    /**
     * Delete all data before the provided offset
     */
    void truncate(long offset);

}
