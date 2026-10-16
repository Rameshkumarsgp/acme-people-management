package com.acme.peoplemanagement.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Answers 401 without a WWW-Authenticate challenge, so the browser never shows its own login prompt
 * or caches credentials. A request that carried credentials counts as a failed attempt.
 */
final class UnauthorizedEntryPoint implements AuthenticationEntryPoint {

    private final LoginAttemptThrottle throttle;
    private final ProblemResponseWriter problemWriter;

    UnauthorizedEntryPoint(final LoginAttemptThrottle throttle, final ProblemResponseWriter problemWriter) {
        this.throttle = throttle;
        this.problemWriter = problemWriter;
    }

    @Override
    public void commence(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final AuthenticationException authException)
            throws IOException {
        if (RequestClient.carriesCredentials(request)) {
            throttle.recordFailure(RequestClient.key(request));
        }
        problemWriter.write(response, HttpStatus.UNAUTHORIZED, "Valid credentials are required.");
    }
}
