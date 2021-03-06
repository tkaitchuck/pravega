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
package io.pravega.controller.server.eventProcessor;

import io.pravega.shared.controller.event.ControllerEvent;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for request handlers.
 *
 * @param <Request> Type of request this handler will process.
 */
@FunctionalInterface
public interface RequestHandler<Request extends ControllerEvent> {
    CompletableFuture<Void> process(Request request);
}
