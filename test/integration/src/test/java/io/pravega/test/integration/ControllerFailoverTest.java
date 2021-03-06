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
package io.pravega.test.integration;

import io.pravega.client.admin.StreamManager;
import io.pravega.test.common.TestingServerStarter;
import io.pravega.test.integration.demo.ControllerWrapper;
import io.pravega.service.contracts.StreamSegmentStore;
import io.pravega.service.server.host.handler.PravegaConnectionListener;
import io.pravega.service.server.store.ServiceBuilder;
import io.pravega.service.server.store.ServiceBuilderConfig;
import io.pravega.client.stream.ScalingPolicy;
import io.pravega.client.stream.StreamConfiguration;
import io.pravega.test.common.TestUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.test.TestingServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;

/**
 * Tests for validating controller fail over behaviour.
 */
@Slf4j
public class ControllerFailoverTest {
    private static final String SCOPE = "testScope";
    private static final String STREAM = "testStream";

    private final int servicePort = TestUtils.getAvailableListenPort();
    private TestingServer zkTestServer;
    private PravegaConnectionListener server;

    @Before
    public void setup() {
        // 1. Start ZK
        try {
            zkTestServer = new TestingServerStarter().start();
        } catch (Exception e) {
            Assert.fail("Failed starting ZK test server");
        }

        // 2. Start Pravega SSS
        ServiceBuilder serviceBuilder = ServiceBuilder.newInMemoryBuilder(ServiceBuilderConfig.getDefaultConfig());
        try {
            serviceBuilder.initialize();
        } catch (Exception e) {
            Assert.fail("Failed starting Pravega host");
        }
        StreamSegmentStore store = serviceBuilder.createStreamSegmentService();
        server = new PravegaConnectionListener(false, servicePort, store);
        server.startListening();
    }

    @After
    public void cleanup() throws Exception {
        if (server != null) {
            server.close();
        }
        if (zkTestServer != null) {
            zkTestServer.close();
        }
    }

    @Test(timeout = 120000)
    public void testSessionExpiryToleranceMinimalServices() {
        final int controllerPort = TestUtils.getAvailableListenPort();
        final String serviceHost = "localhost";
        final int containerCount = 4;
        final ControllerWrapper controllerWrapper = new ControllerWrapper(zkTestServer.getConnectString(), false,
                false, controllerPort, serviceHost, servicePort, containerCount, -1);
        testSessionExpiryTolerance(controllerWrapper, controllerPort);
    }

    @Test(timeout = 120000)
    public void testSessionExpiryToleranceAllServices() {
        final int controllerPort = TestUtils.getAvailableListenPort();
        final String serviceHost = "localhost";
        final int containerCount = 4;
        final ControllerWrapper controllerWrapper = new ControllerWrapper(zkTestServer.getConnectString(), false,
                false, controllerPort, serviceHost, servicePort, containerCount, TestUtils.getAvailableListenPort());
        testSessionExpiryTolerance(controllerWrapper, controllerPort);
    }

    private void testSessionExpiryTolerance(final ControllerWrapper controllerWrapper, final int controllerPort) {

        try {
            controllerWrapper.awaitRunning();
        } catch (IllegalStateException e) {
            log.error("Received interrupt while awaiting start of controllerWrapper", e);
            Assert.fail("Failed starting controllerWrapper");
            return;
        }

        // Simulate ZK session timeout
        try {
            controllerWrapper.forceClientSessionExpiry();
        } catch (Exception e) {
            log.error("Error while simulating client session expiry", e);
            Assert.fail();
        }

        // Now, that session has expired, lets do some operations.
        try {
            controllerWrapper.awaitPaused();
        } catch (IllegalStateException e) {
            log.error("Error waiting for starter termination", e);
            Assert.fail();
        }

        try {
            controllerWrapper.awaitRunning();
        } catch (IllegalStateException e) {
            log.error("Error waiting for starter ready", e);
            Assert.fail();
        }

        URI controllerURI = URI.create("tcp://localhost:" + controllerPort);
        StreamManager streamManager = StreamManager.create(controllerURI);

        // Create scope
        streamManager.createScope(SCOPE);

        // Create stream
        StreamConfiguration streamConfiguration = StreamConfiguration.builder()
                .scope(SCOPE)
                .streamName(STREAM)
                .scalingPolicy(ScalingPolicy.fixed(1))
                .build();
        streamManager.createStream(SCOPE, STREAM, streamConfiguration);

        streamManager.sealStream(SCOPE, STREAM);

        streamManager.deleteStream(SCOPE, STREAM);

        streamManager.deleteScope(SCOPE);

        try {
            controllerWrapper.close();
        } catch (Exception e) {
            log.error("Error closing controllerWrapper", e);
            Assert.fail();
        }

        controllerWrapper.awaitTerminated();
    }

    @Test(timeout = 30000)
    public void testStop() {
        final int controllerPort = TestUtils.getAvailableListenPort();
        final String serviceHost = "localhost";
        final int containerCount = 4;
        final ControllerWrapper controllerWrapper = new ControllerWrapper(zkTestServer.getConnectString(), false,
                false, controllerPort, serviceHost, servicePort, containerCount, TestUtils.getAvailableListenPort());

        try {
            controllerWrapper.awaitRunning();
        } catch (IllegalStateException e) {
            log.error("Received interrupt while awaiting start of controllerWrapper", e);
            Assert.fail("Failed starting controllerWrapper");
            return;
        }

        try {
            controllerWrapper.close();
        } catch (Exception e) {
            Assert.fail("Failed stopping controllerWrapper");
        }
    }
}
