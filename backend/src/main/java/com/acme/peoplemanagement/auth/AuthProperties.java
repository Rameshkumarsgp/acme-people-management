package com.acme.peoplemanagement.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** The shared HR login and the failed-attempt throttle. The app refuses to start without credentials. */
@Validated
@ConfigurationProperties("acme.auth")
public record AuthProperties(
        @NotBlank String username,
        @NotBlank String password,
        @DefaultValue("5") @Positive int maxFailedAttempts,
        @DefaultValue("60s") Duration lockoutWindow) {

    /** The generated record version would print the password into any log line that shows this object. */
    @Override
    public String toString() {
        return "AuthProperties[username=" + username + ", maxFailedAttempts=" + maxFailedAttempts
                + ", lockoutWindow=" + lockoutWindow + "]";
    }
}
