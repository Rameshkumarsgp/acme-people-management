package com.acme.peoplemanagement.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.peoplemanagement.support.MutableClock;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthEndpointTest.MovableClockConfiguration.class)
class AuthEndpointTest {

    private static final String USERNAME = "hr-test";
    private static final String PASSWORD = "test-only-password";
    private static final int MAX_FAILED_ATTEMPTS = 5;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock clock;

    @Test
    void rejectsARequestWithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(clientAt("10.1.0.1")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void returnsTheUsernameForValidCredentials() throws Exception {
        callMe("10.1.0.2", USERNAME, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME));
    }

    @Test
    void rejectsAWrongPassword() throws Exception {
        callMe("10.1.0.3", USERNAME, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void requiresLoginForAnyApiPath() throws Exception {
        mockMvc.perform(get("/api/does-not-exist").with(clientAt("10.1.0.4")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exposesHealthWithoutCredentialsAndWithoutDetails() throws Exception {
        mockMvc.perform(get("/actuator/health").with(clientAt("10.1.0.5")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void blocksAClientAfterTooManyFailedAttemptsEvenWithCorrectCredentials() throws Exception {
        failLogin("10.1.0.6", MAX_FAILED_ATTEMPTS);

        callMe("10.1.0.6", USERNAME, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void allowsTheClientAgainOnceTheBlockHasExpired() throws Exception {
        failLogin("10.1.0.7", MAX_FAILED_ATTEMPTS);

        clock.advance(Duration.ofSeconds(60));

        callMe("10.1.0.7", USERNAME, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void doesNotCountRequestsWithoutCredentialsAsFailedAttempts() throws Exception {
        for (int request = 0; request < MAX_FAILED_ATTEMPTS * 2; request++) {
            mockMvc.perform(get("/api/auth/me").with(clientAt("10.1.0.8")))
                    .andExpect(status().isUnauthorized());
        }

        callMe("10.1.0.8", USERNAME, PASSWORD).andExpect(status().isOk());
    }

    private void failLogin(final String clientAddress, final int attempts) throws Exception {
        for (int attempt = 0; attempt < attempts; attempt++) {
            callMe(clientAddress, USERNAME, "wrong-password").andExpect(status().isUnauthorized());
        }
    }

    private ResultActions callMe(final String clientAddress, final String username, final String password)
            throws Exception {
        final String credentials = HttpHeaders.encodeBasicAuth(username, password, StandardCharsets.UTF_8);
        return mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .with(clientAt(clientAddress)));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor clientAt(
            final String clientAddress) {
        return request -> {
            request.setRemoteAddr(clientAddress);
            return request;
        };
    }

    @TestConfiguration
    static class MovableClockConfiguration {

        @Bean
        @Primary
        MutableClock movableClock() {
            return new MutableClock(Instant.parse("2026-10-15T09:00:00Z"));
        }
    }
}
