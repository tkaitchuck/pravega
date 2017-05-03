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

/**
 * An exception that is thrown whenever a Container cannot be found.
 */
public class ContainerNotFoundException extends ContainerException {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a new instance of the ContainerNotFoundException.
     *
     * @param containerId The Id of the container.
     */
    public ContainerNotFoundException(int containerId) {
        super(containerId, "Container Id does not exist.");
    }
}
