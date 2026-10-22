package com.careconnect.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class SlidingWindowCounterTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final SlidingWindowCounter counter = new SlidingWindowCounter(WINDOW, 3, 1000);

    @Test
    void belowTheLimitIsNotBlocked() {
        counter.record("ip", T0);
        counter.record("ip", T0.plusSeconds(1));

        assertThat(counter.blockedFor("ip", T0.plusSeconds(2))).isEmpty();
    }

    @Test
    void reachingTheLimitBlocksUntilTheOldestEventExpires() {
        counter.record("ip", T0);
        counter.record("ip", T0.plus(Duration.ofMinutes(1)));
        counter.record("ip", T0.plus(Duration.ofMinutes(2)));

        assertThat(counter.blockedFor("ip", T0.plus(Duration.ofMinutes(3)))).contains(Duration.ofMinutes(7));
    }

    @Test
    void unblocksExactlyWhenTheOldestEventLeavesTheWindow() {
        counter.record("ip", T0);
        counter.record("ip", T0.plus(Duration.ofMinutes(1)));
        counter.record("ip", T0.plus(Duration.ofMinutes(2)));

        assertThat(counter.blockedFor("ip", T0.plus(WINDOW).minusMillis(1))).isPresent();
        assertThat(counter.blockedFor("ip", T0.plus(WINDOW))).isEmpty();
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 3; i++) {
            counter.record("a", T0);
        }

        assertThat(counter.blockedFor("a", T0)).isPresent();
        assertThat(counter.blockedFor("b", T0)).isEmpty();
    }

    @Test
    void unknownKeyIsNotBlockedAndNotTracked() {
        assertThat(counter.blockedFor("never-seen", T0)).isEmpty();
        assertThat(counter.trackedKeys()).isZero();
    }

    @Test
    void recordingWhileBlockedKeepsOnlyTheNewestEventsAndSlidesTheBlock() {
        for (int i = 0; i < 3; i++) {
            counter.record("ip", T0);
        }
        // Three more failures five minutes later replace the old ones.
        Instant later = T0.plus(Duration.ofMinutes(5));
        for (int i = 0; i < 3; i++) {
            counter.record("ip", later);
        }

        // Block now ends 10 minutes after the newest events, not after the first ones.
        assertThat(counter.blockedFor("ip", T0.plus(WINDOW))).contains(Duration.ofMinutes(5));
    }

    @Test
    void expiredKeysAreRemovedWhenQueried() {
        counter.record("ip", T0);

        assertThat(counter.blockedFor("ip", T0.plus(WINDOW))).isEmpty();
        assertThat(counter.trackedKeys()).isZero();
    }

    @Test
    void expiredKeysAreSweptOncePerWindow() {
        counter.record("old-1", T0);
        counter.record("old-2", T0);

        counter.record("new", T0.plus(WINDOW).plusSeconds(1));

        assertThat(counter.trackedKeys()).isEqualTo(1);
    }

    @Test
    void tableNeverGrowsPastMaxKeys() {
        SlidingWindowCounter small = new SlidingWindowCounter(WINDOW, 3, 5);

        for (int i = 0; i < 50; i++) {
            small.record("ip-" + i, T0);
        }

        assertThat(small.trackedKeys()).isEqualTo(5);
        // A key that did not fit is simply not tracked.
        assertThat(small.blockedFor("ip-49", T0)).isEmpty();
    }

    @Test
    void fullTableFreesSpaceWhenOldKeysExpire() {
        SlidingWindowCounter small = new SlidingWindowCounter(WINDOW, 1, 2);
        small.record("a", T0);
        small.record("b", T0);

        small.record("c", T0.plus(WINDOW).plusSeconds(1));

        assertThat(small.blockedFor("c", T0.plus(WINDOW).plusSeconds(1))).isPresent();
    }

    @Test
    void invalidSettingsAreRejected() {
        assertThatThrownBy(() -> new SlidingWindowCounter(Duration.ZERO, 3, 10))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SlidingWindowCounter(null, 3, 10))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SlidingWindowCounter(WINDOW, 0, 10))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SlidingWindowCounter(WINDOW, 3, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parallelRecordingIsSafeAndStaysBounded() throws Exception {
        SlidingWindowCounter shared = new SlidingWindowCounter(WINDOW, 5, 1000);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                tasks.add(pool.submit(() -> {
                    for (int i = 0; i < 2000; i++) {
                        shared.record("same-ip", T0);
                        shared.blockedFor("same-ip", T0);
                    }
                }));
            }
            for (Future<?> task : tasks) {
                task.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(shared.blockedFor("same-ip", T0)).isPresent();
        assertThat(shared.trackedKeys()).isEqualTo(1);
    }
}
