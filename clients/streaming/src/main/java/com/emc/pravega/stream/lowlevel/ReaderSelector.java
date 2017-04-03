package com.emc.pravega.stream.lowlevel;

import com.emc.pravega.stream.Stream;
import java.util.List;

public interface ReaderSelector {

    /**
     * List the streams that are being read from.
     */
    List<Stream> listStreams();

    /**
     * Acquire a reader object for exclusive use until it is released by calling {@link #releaseReader(ByteStreamReader)}
     * (Release need not be called by the same process that acquired the reader.)
     * 
     * @return A reader object or null if none are available.
     */
    ByteStreamReader aquireReader();
    
    /**
     * Return a reader object so that others may obtain it by calling {@link #aquireReader()}. The
     * reader will be released at whatever offset it was at. If the reader has come to the end of
     * it's segment this will release readers for the segments that follow it (assuming all of their
     * Predecessors have similarly been released at the end of their segment)
     * 
     * @param toRelease The reader to be released with its offset (set to/left at) the desired location.
     */
    void releaseReader(ByteStreamReader toRelease);

}
