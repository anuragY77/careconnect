package com.careconnect.common.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Remembers the most recent events per key and tells whether a key reached its limit inside a
 * sliding time window. Thread safe; every change to one key happens atomically.
 *
 * <p>Memory is bounded twice: each key keeps at most {@code limit} timestamps, and at most
 * {@code maxKeys} keys are tracked. Expired entries are swept once per window, and also
 * when the table is full. If the table is still full after a sweep, a new key is simply not
 * tracked (fail open for that key) instead of growing without limit.
 *
 * <p>State lives in this JVM. That is correct for the single backend of the local Docker Compose
 * setup. Running several backend instances needs a shared store instead (see DECISIONS.md).
 */
public final class SlidingWindowCounter {

    private final Duration window;
    private final int limit;
    private final int maxKeys;
    private final ConcurrentHashMap<String, ArrayDeque<Instant>> events = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastSweep = new AtomicReference<>(Instant.MIN);

    public SlidingWindowCounter(Duration window, int limit, int maxKeys) {
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
        if (maxKeys < 1) {
            throw new IllegalArgumentException("maxKeys must be at least 1");
        }
        this.window = window;
        this.limit = limit;
        this.maxKeys = maxKeys;
    }

    /** Records one event for the key. Only the newest {@code limit} events are kept. */
    public void record(String key, Instant now) {
        sweepIfDue(now);
        if (events.size() >= maxKeys && !events.containsKey(key)) {
            sweep(now);
        }
        events.compute(key, (k, queue) -> {
            if (queue == null) {
                if (events.size() >= maxKeys) {
                    return null;
                }
                queue = new ArrayDeque<>();
            }
            dropExpired(queue, now);
            if (queue.size() >= limit) {
                queue.removeFirst();
            }
            queue.addLast(now);
            return queue;
        });
    }

    /**
     * If the key has reached its limit, returns how long until it drops below it again;
     * otherwise empty.
     */
    public Optional<Duration> blockedFor(String key, Instant now) {
        Duration[] result = new Duration[1];
        events.computeIfPresent(key, (k, queue) -> {
            dropExpired(queue, now);
            if (queue.isEmpty()) {
                return null;
            }
            if (queue.size() >= limit) {
                result[0] = Duration.between(now, queue.peekFirst().plus(window));
            }
            return queue;
        });
        return Optional.ofNullable(result[0]);
    }

    /** Number of keys currently tracked (including keys whose events have expired but were not swept yet). */
    public int trackedKeys() {
        return events.size();
    }

    private void dropExpired(ArrayDeque<Instant> queue, Instant now) {
        while (!queue.isEmpty() && !now.isBefore(queue.peekFirst().plus(window))) {
            queue.removeFirst();
        }
    }

    private void sweepIfDue(Instant now) {
        Instant previous = lastSweep.get();
        if (!now.isBefore(previous.plus(window)) && lastSweep.compareAndSet(previous, now)) {
            sweep(now);
        }
    }

    private void sweep(Instant now) {
        for (String key : events.keySet()) {
            events.computeIfPresent(key, (k, queue) -> {
                dropExpired(queue, now);
                return queue.isEmpty() ? null : queue;
            });
        }
    }
}
