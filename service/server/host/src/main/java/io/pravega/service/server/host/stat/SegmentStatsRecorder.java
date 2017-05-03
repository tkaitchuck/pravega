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
package io.pravega.service.server.host.stat;

public interface SegmentStatsRecorder {

    /**
     * Method to notify segment create events.
     *
     * @param streamSegmentName segment.
     * @param type              type of auto scale.
     * @param targetRate        desired rate.
     */
    void createSegment(String streamSegmentName, byte type, int targetRate);

    /**
     * Method to notify segment sealed events.
     *
     * @param streamSegmentName segment.
     * @param streamSegmentName
     */
    void sealSegment(String streamSegmentName);

    /**
     * Method to notify segment policy events.
     *
     * @param streamSegmentName segment.
     * @param type              type of auto scale.
     * @param targetRate        desired rate.
     */
    void policyUpdate(String streamSegmentName, byte type, int targetRate);

    /**
     * Method to record incoming traffic.
     *
     * @param streamSegmentName segment name.
     * @param dataLength        data length.
     * @param numOfEvents       number of events.
     */
    void record(String streamSegmentName, long dataLength, int numOfEvents);

    /**
     * Method to notify merge of transaction.
     *
     * @param streamSegmentName parent segment.
     * @param dataLength        data in transactional segment.
     * @param numOfEvents       events in transactional segment.
     * @param txnCreationTime   transaction creation time.
     */
    void merge(String streamSegmentName, long dataLength, int numOfEvents, long txnCreationTime);
}
