package com.emc.pravega.stream.lowlevel;

import java.nio.ByteBuffer;

public interface ByteStreamReader {
    
    /**
     * Returns the total length of all data written to the stream thus far.
     */
    long fetchCurrentStreamLength();
    
    /**
     * Returns the current offset in the stream.
     */
    long getOffset();
    
    /**
     * Sets the next offset to read from.
     */
    void setOffset(long offset);
    
    /**
     * Read 'length' bytes from the current offset.
     */
    ByteBuffer read(int length);
}
