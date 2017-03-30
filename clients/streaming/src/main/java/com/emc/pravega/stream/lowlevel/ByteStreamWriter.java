package com.emc.pravega.stream.lowlevel;

import java.nio.ByteBuffer;
import java.util.List;

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
     * @return The total number of bytes that have been passed to write
     */
    long getWrittenLength();
    
    /**
     * @return The total number of bytes that have been durably stored so far.
     */
    long getDurableLength();
    
    /**
     * Block until all outstanding writes are completed.
     */
    void flush();
    
    /**
     * Returns a new stream that is orginized under this one.
     */
    ByteStreamWriter createChildStream(String name);
    
    /**
     * Returns the list of children of this stream.
     */
    List<String> listChildStreams();
    
    /**
     * Returns an existing child stream.
     */
    ByteStreamWriter getChildStream(String name);
    
    /**
     * Atomically moves all the data written to the child stream to the end of this stream.
     * After this operation the child stream will not exist, and this stream will have all the data.
     */
    void mergeChildStream(ByteStreamWriter child);
    
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
