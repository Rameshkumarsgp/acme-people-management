package com.acme.peoplemanagement.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class ProblemResponseWriterTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final ProblemResponseWriter writer = new ProblemResponseWriter(jsonMapper);

    @Test
    void writesTheStatusAndAProblemJsonContentType() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, HttpStatus.UNAUTHORIZED, "Valid credentials are required.");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
    }

    @Test
    void takesTheTitleFromTheStatus() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, HttpStatus.TOO_MANY_REQUESTS, "Try again later.");

        assertThat(body(response)).containsEntry("title", "Too Many Requests").containsEntry("status", 429);
    }

    @Test
    void keepsTheBodyValidWhenTheDetailContainsQuotes() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, HttpStatus.UNAUTHORIZED, "The user \"hr\" was rejected.");

        assertThat(body(response)).containsEntry("detail", "The user \"hr\" was rejected.");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(final MockHttpServletResponse response) throws Exception {
        return jsonMapper.readValue(response.getContentAsString(), Map.class);
    }
}
