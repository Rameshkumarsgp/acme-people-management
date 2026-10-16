package com.acme.peoplemanagement.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.peoplemanagement.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LoginAttemptThrottleTest {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofSeconds(60);
    private static final String CLIENT = "10.0.0.1";

    private MutableClock clock;
    private LoginAttemptThrottle throttle;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-15T09:00:00Z"));
        throttle = new LoginAttemptThrottle(clock, MAX_FAILED_ATTEMPTS, WINDOW);
    }

    @Test
    void allowsAClientWithNoFailures() {
        assertThat(throttle.isBlocked(CLIENT)).isFalse();
    }

    @Test
    void allowsAClientBelowTheFailureLimit() {
        recordFailures(CLIENT, MAX_FAILED_ATTEMPTS - 1);

        assertThat(throttle.isBlocked(CLIENT)).isFalse();
    }

    @Test
    void blocksAClientThatReachesTheFailureLimit() {
        recordFailures(CLIENT, MAX_FAILED_ATTEMPTS);

        assertThat(throttle.isBlocked(CLIENT)).isTrue();
    }

    @Test
    void allowsTheClientAgainOnceTheWindowHasPassed() {
        recordFailures(CLIENT, MAX_FAILED_ATTEMPTS);

        clock.advance(WINDOW);

        assertThat(throttle.isBlocked(CLIENT)).isFalse();
    }

    @Test
    void countsOnlyFailuresInsideTheWindow() {
        recordFailures(CLIENT, MAX_FAILED_ATTEMPTS - 1);
        clock.advance(WINDOW.plusSeconds(1));

        throttle.recordFailure(CLIENT);

        assertThat(throttle.isBlocked(CLIENT)).isFalse();
    }

    @Test
    void tracksEachClientSeparately() {
        recordFailures(CLIENT, MAX_FAILED_ATTEMPTS);

        assertThat(throttle.isBlocked("10.0.0.2")).isFalse();
    }

    @Test
    void reportsTheTimeLeftUntilTheClientIsAllowedAgain() {
        recordFailures(CLIENT, MAX_FAILED_ATTEMPTS);
        clock.advance(Duration.ofSeconds(20));

        assertThat(throttle.retryAfter(CLIENT)).isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    void reportsNoWaitForAClientThatIsNotBlocked() {
        assertThat(throttle.retryAfter(CLIENT)).isEqualTo(Duration.ZERO);
    }

    @Test
    void forgetsClientsWhoseFailuresHaveAllExpired() {
        throttle.recordFailure("10.0.0.3");
        clock.advance(WINDOW.plusSeconds(1));

        throttle.recordFailure(CLIENT);

        assertThat(trackedClients()).containsExactly(CLIENT);
    }

    private Set<Object> trackedClients() {
        final Map<?, ?> failuresByClient = Objects.requireNonNull(
                (Map<?, ?>) ReflectionTestUtils.getField(throttle, "failuresByClient"),
                "the throttle no longer has a failuresByClient field");
        return new HashSet<>(failuresByClient.keySet());
    }

    private void recordFailures(final String clientKey, final int count) {
        for (int attempt = 0; attempt < count; attempt++) {
            throttle.recordFailure(clientKey);
        }
    }
}
