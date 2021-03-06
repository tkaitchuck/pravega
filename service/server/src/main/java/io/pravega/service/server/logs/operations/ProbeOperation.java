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
package io.pravega.service.server.logs.operations;

import io.pravega.service.server.logs.SerializationException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * No-op operation that can be used as an "operation barrier". This can be added to the Log and when it completes, the
 * caller knows that all operations up to, and including it, have been completed (successfully or not). This operation
 * cannot be serialized or recovered.
 */
public class ProbeOperation extends Operation {
    public ProbeOperation() {
        super();
    }

    @Override
    public boolean canSerialize() {
        // We cannot (and should not) process this operation in the log. It serves no real purpose except as a control
        // op (see class-level doc).
        return false;
    }

    @Override
    protected OperationType getOperationType() {
        return OperationType.Probe;
    }

    @Override
    protected void serializeContent(DataOutputStream target) throws IOException {
        throw new UnsupportedOperationException(this.getClass().getSimpleName() + " cannot be serialized.");
    }

    @Override
    protected void deserializeContent(DataInputStream source) throws IOException, SerializationException {
        throw new UnsupportedOperationException(this.getClass().getSimpleName() + " cannot be deserialized.");
    }
}
