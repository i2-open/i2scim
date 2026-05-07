package com.independentid.scim.test.signals;

import com.independentid.signals.StreamMaintenanceScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class StreamMaintenanceSchedulerTest {

    private StreamMaintenanceScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new StreamMaintenanceScheduler();
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    @Test
    void scheduledTaskRunsAtTheConfiguredCadence() throws Exception {
        CountDownLatch ranAtLeastTwice = new CountDownLatch(2);

        scheduler.schedulePausedRecheck("stream-a", ranAtLeastTwice::countDown, Duration.ofMillis(50));

        assertThat(ranAtLeastTwice.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void cancelStopsScheduledTask() throws Exception {
        AtomicInteger counter = new AtomicInteger();

        scheduler.schedulePausedRecheck("stream-a", counter::incrementAndGet, Duration.ofMillis(50));
        Thread.sleep(120);
        scheduler.cancelPausedRecheck("stream-a");
        int countAtCancel = counter.get();
        Thread.sleep(200);

        assertThat(counter.get()).isCloseTo(countAtCancel, org.assertj.core.data.Offset.offset(1));
    }

    @Test
    void multipleStreamsHaveIndependentTasks() throws Exception {
        CountDownLatch streamA = new CountDownLatch(1);
        CountDownLatch streamB = new CountDownLatch(1);

        scheduler.schedulePausedRecheck("stream-a", streamA::countDown, Duration.ofMillis(50));
        scheduler.schedulePausedRecheck("stream-b", streamB::countDown, Duration.ofMillis(50));

        assertThat(streamA.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(streamB.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void cancelIsIdempotentForUnscheduledKey() {
        assertThatCode(() -> scheduler.cancelPausedRecheck("never-scheduled"))
                .doesNotThrowAnyException();
    }

    @Test
    void reschedulingSameKeyReplacesPriorTask() throws Exception {
        AtomicInteger originalRunCount = new AtomicInteger();
        CountDownLatch replacementRan = new CountDownLatch(1);

        scheduler.schedulePausedRecheck("stream-a", originalRunCount::incrementAndGet, Duration.ofMillis(50));
        Thread.sleep(80);
        scheduler.schedulePausedRecheck("stream-a", replacementRan::countDown, Duration.ofMillis(50));

        assertThat(replacementRan.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void shutdownStopsAllScheduledWork() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        scheduler.schedulePausedRecheck("stream-a", counter::incrementAndGet, Duration.ofMillis(50));
        Thread.sleep(120);

        scheduler.shutdown();
        int countAtShutdown = counter.get();
        Thread.sleep(200);

        assertThat(counter.get()).isEqualTo(countAtShutdown);
    }

    @Test
    void jwksRefreshRunsAndCancelStops() throws Exception {
        AtomicInteger ran = new AtomicInteger();
        scheduler.scheduleJwksRefresh("poll:s1", ran::incrementAndGet, Duration.ofMillis(50));
        Thread.sleep(180);
        scheduler.cancelJwksRefresh("poll:s1");
        int countAtCancel = ran.get();
        Thread.sleep(200);

        assertThat(countAtCancel).isGreaterThanOrEqualTo(2);
        assertThat(ran.get()).isCloseTo(countAtCancel, org.assertj.core.data.Offset.offset(1));
    }

    @Test
    void shutdownAlsoStopsJwksRefresh() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        scheduler.scheduleJwksRefresh("poll:s1", counter::incrementAndGet, Duration.ofMillis(50));
        Thread.sleep(120);

        scheduler.shutdown();
        int countAtShutdown = counter.get();
        Thread.sleep(200);

        assertThat(counter.get()).isEqualTo(countAtShutdown);
    }
}
