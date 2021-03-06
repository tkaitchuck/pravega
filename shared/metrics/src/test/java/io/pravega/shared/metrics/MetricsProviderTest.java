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

import io.pravega.common.Timer;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The type Yammer provider test.
 */
@Slf4j
public class MetricsProviderTest {

    private final StatsLogger statsLogger = MetricsProvider.createStatsLogger("");
    private final DynamicLogger dynamicLogger = MetricsProvider.getDynamicLogger();

    @Before
    public void setUp() {
        MetricsProvider.initialize(MetricsConfig.builder()
                                                .with(MetricsConfig.ENABLE_STATISTICS, true)
                                                .build());
    }

    /**
     * Test Event and Value registered and worked well with OpStats.
     */
    @Test
    public void testOpStatsData() {
        Timer startTime = new Timer();
        OpStatsLogger opStatsLogger = statsLogger.createStats("testOpStatsLogger");
        // register 2 event: 1 success, 1 fail.
        opStatsLogger.reportSuccessEvent(startTime.getElapsed());
        opStatsLogger.reportFailEvent(startTime.getElapsed());
        opStatsLogger.reportSuccessValue(startTime.getElapsedMillis());
        opStatsLogger.reportFailValue(startTime.getElapsedMillis());

        opStatsLogger.reportSuccessValue(1);
        opStatsLogger.reportFailValue(1);
        opStatsLogger.reportSuccessValue(1);

        OpStatsData statsData = opStatsLogger.toOpStatsData();
        // 2 = 2 event + 2 value
        assertEquals(4, statsData.getNumSuccessfulEvents());
        assertEquals(3, statsData.getNumFailedEvents());
    }

    /**
     * Test counter registered and  worked well with StatsLogger.
     */
    @Test
    public void testCounter() {
        Counter testCounter = statsLogger.createCounter("testCounter");
        testCounter.add(17);
        assertEquals(17, testCounter.get());

        // test dynamic counter
        int sum = 0;
        for (int i = 1; i < 10; i++) {
            sum += i;
            dynamicLogger.incCounterValue("dynamicCounter", i);
            assertEquals(sum, MetricsProvider.YAMMERMETRICS.getCounters().get("DYNAMIC.dynamicCounter.Counter").getCount());
        }
    }

    /**
     * Test Meter registered and  worked well with StatsLogger.
     */
    @Test
    public void testMeter() {
        Meter testMeter = statsLogger.createMeter("testMeter");
        testMeter.recordEvent();
        testMeter.recordEvent();
        assertEquals(2, testMeter.getCount());
        testMeter.recordEvents(27);
        assertEquals(29, testMeter.getCount());

        // test dynamic meter
        int sum = 0;
        for (int i = 1; i < 10; i++) {
            sum += i;
            dynamicLogger.recordMeterEvents("dynamicMeter", i);
            assertEquals(sum, MetricsProvider.YAMMERMETRICS.getMeters().get("DYNAMIC.dynamicMeter.Meter").getCount());
        }
    }

    /**
     * Test gauge registered and  worked well with StatsLogger.
     */
    @Test
    public void testGauge() {
        AtomicInteger value = new AtomicInteger(1);
        statsLogger.registerGauge("testGauge", value::get);

        for (int i = 1; i < 10; i++) {
            value.set(i);
            dynamicLogger.reportGaugeValue("dynamicGauge", i);
            assertEquals(i, MetricsProvider.YAMMERMETRICS.getGauges().get("testGauge").getValue());
            assertEquals(i, MetricsProvider.YAMMERMETRICS.getGauges().get("DYNAMIC.dynamicGauge.Gauge").getValue());
        }
    }

    /**
     * Test that we can transition from stats enabled, to disabled, to enabled.
     */
    @Test
    public void testMultipleInitialization() {
        MetricsConfig config = MetricsConfig.builder()
                                            .with(MetricsConfig.ENABLE_STATISTICS, false)
                                            .build();
        MetricsProvider.initialize(config);
        statsLogger.createCounter("counterDisabled");

        assertEquals(null, MetricsProvider.YAMMERMETRICS.getCounters().get("counterDisabled"));

        config = MetricsConfig.builder()
                              .with(MetricsConfig.ENABLE_STATISTICS, true)
                              .build();
        MetricsProvider.initialize(config);
        statsLogger.createCounter("counterEnabled");

        Assert.assertNotNull(MetricsProvider.YAMMERMETRICS.getCounters().get("counterEnabled"));
    }

    /**
     * Test that we can transition from stats enabled, to disabled, to enabled.
     */
    @Test
    public void testContinuity() {
        statsLogger.createCounter("continuity-counter");
        MetricsConfig config = MetricsConfig.builder()
                                            .with(MetricsConfig.ENABLE_STATISTICS, false)
                                            .build();
        MetricsProvider.initialize(config);

        Assert.assertNotNull(null, MetricsProvider.YAMMERMETRICS.getCounters().get("continuity-counter"));
    }

    /**
     * Test transition back to null provider.
     */
    @Test
    public void testTransitionBackToNullProvider() {
        MetricsConfig config = MetricsConfig.builder()
                                            .with(MetricsConfig.ENABLE_STATISTICS, false)
                                            .build();
        MetricsProvider.initialize(config);

        Counter counter = statsLogger.createCounter("continuity-counter");
        counter.add(1L);
        assertEquals(0L, counter.get());

        config = MetricsConfig.builder()
                              .with(MetricsConfig.ENABLE_STATISTICS, true)
                              .build();
        MetricsProvider.initialize(config);

        counter.add(1L);
        assertEquals(1L, counter.get());
    }
}
