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
package io.pravega.shared.metrics;

public class NullStatsProvider implements StatsProvider {

    private final StatsLogger nullStatsLogger = new NullStatsLogger();
    private final DynamicLogger nullDynamicLogger = new NullDynamicLogger();

    @Override
    public void start() {
        // nop
    }

    @Override
    public void close() {
        // nop
    }

    @Override
    public StatsLogger createStatsLogger(String scope) {
        return nullStatsLogger;
    }

    @Override
    public DynamicLogger createDynamicLogger() {
        return nullDynamicLogger;
    }
}
