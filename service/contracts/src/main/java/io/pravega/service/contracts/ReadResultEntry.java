/**
 * Copyright (c) 2017 Dell Inc., or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.pravega.service.contracts;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Defines an Entry that makes up a ReadResult.
 */
public interface ReadResultEntry {
    /**
     * Gets a value indicating the offset in the StreamSegment that this entry starts at.
     */
    long getStreamSegmentOffset();

    /**
     * Gets a value indicating the number of bytes requested for reading.
     * NOTE: The number of bytes actually read may differ from this value.
     */
    int getRequestedReadLength();

    /**
     * Gets a value indicating the Type of this ReadResultEntry.
     */
    ReadResultEntryType getType();

    /**
     * Returns a CompletableFuture that, when completed, will contain the contents of this ReadResultEntry. Simply
     * calling this method will not trigger any actions (such as data retrieval). Use the requestContent() method to do
     * that.
     */
    CompletableFuture<ReadResultEntryContents> getContent();

    /**
     * Initiates an asynchronous action to fetch the contents of this ReadResultEntry, if necessary.
     *
     * @param timeout Timeout for the operation.
     */
    void requestContent(Duration timeout);
}
