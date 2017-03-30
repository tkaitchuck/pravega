package com.emc.pravega.stream.lowlevel;

import com.emc.pravega.stream.Stream;
import java.util.List;

public interface ReaderSelector {

    ByteStreamReader aquireReader();
    
    void releaseReader(ByteStreamReader toRelease);
    
    List<Stream> listStreams();
    
}
