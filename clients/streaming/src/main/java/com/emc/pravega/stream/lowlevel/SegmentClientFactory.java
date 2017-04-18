package com.emc.pravega.stream.lowlevel;

import com.emc.pravega.stream.Segment;

public interface SegmentClientFactory {

    /**
     * Creates a new writer that can write to the specified segment.
     */
     SegmentWriter createSegmentWriter(Segment segment);

    /**
     * Creates a new reader that can read from the requested segment
     */
    SegmentReader createReader(Segment segment, long offset);    
    
}
