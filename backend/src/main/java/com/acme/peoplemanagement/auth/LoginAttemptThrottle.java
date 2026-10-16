package com.acme.peoplemanagement.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Blocks a client after too many failed login attempts inside a time window.
 * Only failures within the window count, so a client is allowed again once the oldest failures expire.
 * Questions never change state. Expired failures are forgotten when the next failure is recorded.
 */
public final class LoginAttemptThrottle {

    private final Clock clock;
    private final int maxFailedAttempts;
    private final Duration window;
    private final Map<String, Deque<Instant>> failuresByClient = new HashMap<>();

    public LoginAttemptThrottle(final Clock clock, final int maxFailedAttempts, final Duration window) {
        this.clock = clock;
        this.maxFailedAttempts = maxFailedAttempts;
        this.window = window;
    }

    public synchronized void recordFailure(final String clientKey) {
        forgetExpiredFailures();
        failuresByClient.computeIfAbsent(clientKey, key -> new ArrayDeque<>()).addLast(clock.instant());
    }

    public synchronized boolean isBlocked(final String clientKey) {
        return recentFailures(clientKey).size() >= maxFailedAttempts;
    }

    public synchronized Duration retryAfter(final String clientKey) {
        final List<Instant> failures = recentFailures(clientKey);
        if (failures.size() < maxFailedAttempts) {
            return Duration.ZERO;
        }
        final Instant oldestFailureThatBlocks = failures.get(failures.size() - maxFailedAttempts);
        return Duration.between(clock.instant(), oldestFailureThatBlocks.plus(window));
    }

    private List<Instant> recentFailures(final String clientKey) {
        final Instant windowStart = clock.instant().minus(window);
        return failuresByClient.getOrDefault(clientKey, new ArrayDeque<>()).stream()
                .filter(failure -> failure.isAfter(windowStart))
                .toList();
    }

    private void forgetExpiredFailures() {
        final Instant windowStart = clock.instant().minus(window);
        failuresByClient.values().forEach(failures -> failures.removeIf(failure -> !failure.isAfter(windowStart)));
        failuresByClient.values().removeIf(Deque::isEmpty);
    }
}
