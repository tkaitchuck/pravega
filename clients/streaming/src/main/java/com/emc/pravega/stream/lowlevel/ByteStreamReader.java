package com.emc.pravega.stream.lowlevel;

import com.emc.pravega.stream.Stream;
import java.nio.ByteBuffer;

public interface ByteStreamReader {
    
    /**
     * Get the stream this reader corresponds with.
     */
    Stream getStream();
    
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
