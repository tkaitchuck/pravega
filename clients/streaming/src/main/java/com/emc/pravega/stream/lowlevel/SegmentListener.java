package com.emc.pravega.stream.lowlevel;

import java.nio.ByteBuffer;

public interface SegmentListener {

    /**
     * Processes data from a segment. This is invoked repeatedly as data is read. This function may
     * read any or all of the data in the provided buffer. If there is data that is data remaining
     * in the buffer, it will be provided on the next call. If no bytes are read from the buffer it
     * will be assumed that more data is needed to process anything, and this method will not be
     * invoked again until more is available.
     * 
     * This method will never be invoked multiple times in parallel so delaying the return of the
     * method can be used to apply back pressure to slow down processing.
     * 
     * To stop processing all together and not have this method invoked again this method may throw
     * {@link StopListeningException}
     * 
     * @param startingOffset The offset in the segment the data in the buffer begins from.
     * @param buffer The data in the segment at startingOffset.
     * @throws StopListeningException
     */
    public void processData(long startingOffset, ByteBuffer buffer) throws StopListeningException;

}
