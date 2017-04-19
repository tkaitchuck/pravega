package com.emc.pravega.stream.lowlevel.impl;

import com.emc.pravega.common.util.CircularBuffer;
import java.nio.ByteBuffer;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OffsetBuffer {

    private final CircularBuffer buffer;
    private long writeOffset;
 
    public OffsetBuffer(int size) {
        buffer = new CircularBuffer(size);
    }
    
    /**
     * Fills the provided ByteBuffer from this buffer and return the offset from which the data came.
     * @param toFill The buffer to fill if possible.
     * @return The offset that the data placed into the buffer began at.
     */
    @Synchronized
    public long read(ByteBuffer toFill) {
        long result = getReadOffset();
        buffer.read(toFill);
        return result;
    }
    
    /**
     * Provides data from a given offset to the buffer. If this is at the write offset, it will be
     * added directly. If it is ahead of the write offset nothing will be added as the buffer will
     * not skip over any data. If it is behind the write offset then some data from the buffer may
     * be skipped over so that it is not duplicated.
     * 
     * @param data The data to add to this buffer.
     * @param offset The offset the data came from.
     */
    @Synchronized
    public void fill(long offset, ByteBuffer data) {
        if (writeOffset < offset) {
            log.warn("Dropping data retreived because not all of the data before it has been retreived. {}  {}",
                    writeOffset, offset);
            return;
        }
        if (writeOffset > offset) {
            int skip = (int) Math.min(writeOffset - offset, data.remaining());
            data.position(data.position() + skip);
        }
        writeOffset += buffer.fill(data);
    }
    
    @Synchronized
    public long getWriteOffset() {
        return writeOffset;
    }
    
    @Synchronized
    public long getReadOffset() {
        return writeOffset - buffer.dataAvailable();
    }
    
    @Synchronized
    public int dataAvailable() {
        return buffer.dataAvailable();
    }

    @Synchronized
    public int capacityAvailable() {
        return buffer.capacityAvailable();
    }
}
