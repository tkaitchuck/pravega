package com.emc.pravega.stream.lowlevel;

import com.emc.pravega.stream.Segment;

public interface SegmentClientFactory {

    /**
     * Creates a new writer that can write to the specified segment.
     */
     SegmentWriter createSegmentWriter(Segment segment);

    /**
     * Connects a listener to a segment starting at the specified offset.
     */
    void listenToSegment(Segment segment, long offset, SegmentListener listener);    
    
}
