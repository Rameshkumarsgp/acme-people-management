package com.acme.peoplemanagement.auth;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

/** Writes security errors as application/problem+json, the same format as every other API error. */
final class ProblemResponseWriter {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final JsonMapper jsonMapper;

    ProblemResponseWriter(final JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    void write(final HttpServletResponse response, final HttpStatus status, final String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(PROBLEM_JSON);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(
                new ProblemBody("about:blank", status.getReasonPhrase(), status.value(), detail)));
    }

    record ProblemBody(String type, String title, int status, String detail) {
    }
}
