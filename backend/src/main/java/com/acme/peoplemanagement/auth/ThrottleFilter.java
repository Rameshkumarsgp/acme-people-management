package com.acme.peoplemanagement.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects requests from a blocked client before any credentials are checked, so a correct guess
 * cannot get through while the block lasts. Requests without credentials are never blocked.
 */
final class ThrottleFilter extends OncePerRequestFilter {

    private static final long MILLIS_PER_SECOND = 1000;

    private final LoginAttemptThrottle throttle;
    private final ProblemResponseWriter problemWriter;

    ThrottleFilter(final LoginAttemptThrottle throttle, final ProblemResponseWriter problemWriter) {
        this.throttle = throttle;
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
            throws ServletException, IOException {
        final String clientKey = RequestClient.key(request);
        if (RequestClient.carriesCredentials(request) && throttle.isBlocked(clientKey)) {
            final long retryAfterInSeconds =
                    Math.ceilDiv(throttle.retryAfter(clientKey).toMillis(), MILLIS_PER_SECOND);
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterInSeconds));
            problemWriter.write(response, HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed login attempts. Try again later.");
            return;
        }
        chain.doFilter(request, response);
    }
}
