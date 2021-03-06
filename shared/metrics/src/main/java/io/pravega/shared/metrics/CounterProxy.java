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

import java.util.concurrent.atomic.AtomicReference;

public class CounterProxy implements Counter {
    private final AtomicReference<Counter> instance = new AtomicReference<>();

    CounterProxy(Counter counter) {
        instance.set(counter);
    }

    void setCounter(Counter counter) {
        instance.set(counter);
    }

    @Override
    public void clear() {
        instance.get().clear();
    }

    @Override
    public void inc() {
        instance.get().inc();
    }

    @Override
    public void dec() {
        instance.get().dec();
    }

    @Override
    public void add(long delta) {
        instance.get().add(delta);
    }

    @Override
    public long get() {
        return instance.get().get();
    }

    @Override
    public String getName() {
        return instance.get().getName();
    }
}
