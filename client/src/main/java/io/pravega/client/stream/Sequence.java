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
package io.pravega.client.stream;

import io.pravega.client.stream.impl.segment.SequenceImpl;
import java.io.Serializable;

/**
 * A wrapper for two numbers. Where one is treated as 'high order' and the other as 'low order' to
 * break ties when the former is the same.
 */
public interface Sequence extends Comparable<Sequence>, Serializable {
    public static final Sequence MAX_VALUE = create(Long.MAX_VALUE, Long.MAX_VALUE);
    public static final Sequence ZERO = create(0L, 0L);
    public static final Sequence MIN_VALUE = create(Long.MIN_VALUE, Long.MIN_VALUE);
    
    public long getHighOrder();
    
    public long getLowOrder();
    
    public default int compareTo(Sequence o) {
        int result = Long.compare(getHighOrder(), o.getHighOrder());
        if (result == 0) {
            return Long.compare(getLowOrder(), o.getLowOrder());
        }
        return result;
    }
    
    public static Sequence create(long highOrder, long lowOrder) {
        return SequenceImpl.create(highOrder, lowOrder);
    }
    
    public SequenceImpl asImpl();
    
}