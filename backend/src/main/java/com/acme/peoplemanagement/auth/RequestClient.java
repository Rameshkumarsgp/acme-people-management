package com.acme.peoplemanagement.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/**
 * The one place that decides who a client is and whether a request is a login attempt, so the filter
 * and the entry point cannot disagree.
 */
final class RequestClient {

    private RequestClient() {
    }

    /** The connection's address. Forwarded-address headers are not trusted, because a client can forge them. */
    static String key(final HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    static boolean carriesCredentials(final HttpServletRequest request) {
        return request.getHeader(HttpHeaders.AUTHORIZATION) != null;
    }
}
