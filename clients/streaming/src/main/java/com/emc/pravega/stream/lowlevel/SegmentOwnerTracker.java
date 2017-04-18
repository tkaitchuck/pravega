package com.emc.pravega.stream.lowlevel;

import com.emc.pravega.stream.Segment;
import com.emc.pravega.stream.Stream;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public interface SegmentOwnerTracker {

    /**
     * List the streams that are being selected from.
     */
    List<Stream> listStreams();
    
    /**
     * Returns a list of the segments currently owned.
     */
    Map<Segment, Long> getOwnedSegments();
    
    /**
     * Returns a map from segments to their current owners.
     */
    Map<Segment, String> getCurrentOwners();

    /**
     * Acquire a segment until it is released by calling {@link #releaseSegment(Segment, Long)}
     * (Release need not be called by the same process that acquired the segment.)
     * 
     * @return A segment and the offset within that segment that can be read from.
     */
    Entry<Segment, Long> aquireSegment();
    
    /**
     * Update the offset for a given segment.
     */
    void updateOffset(long newOffset);
    
    /**
     * Release as segment so that others may obtain it by calling {@link #aquireSegment()}.
     * 
     * The segment will be released at the provided offset, if null is passed the last offset passed
     * to {@link #updateOffset(long)} or if it was not called the offset the segment was acquired at
     * will be used.
     * 
     * If the offset provided is at or beyond the end of the segment it will be removed from the
     * selector and its successors will be added in its place. has come to the end of it's segment
     * this will release readers
     * 
     * @param segment The segment to be released
     * @param offset The offset to release the segment at.
     */
    void releaseSegment(Segment segment, Long offset);

}
