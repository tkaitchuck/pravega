package com.emc.pravega.stream.lowlevel;

import com.emc.pravega.stream.Segment;

public interface SegmentManager {

    /**
     * Create a new segment
     * @param segment The segment to create.
     */
    public void createSegment(Segment segment);
    
    /**
     * Prohibit any future writes to the provided segment.
     * @param segment The segment to seal
     */
    public void sealSegment(Segment segment);
    
    /**
     * Truncate a segment such that all data before the provided offset can no-longer be read and
     * can be deleted.
     * 
     * @param segment The segment to truncate
     * @param upToOffset The offset below which data can be removed.
     */
    public void truncateSegment(Segment segment, long upToOffset);
    
    /**
     * Delete a segment and all data in it.
     * 
     * @param segment The segment to delete
     */
    public void deleteSegment(Segment segment);
    
}
