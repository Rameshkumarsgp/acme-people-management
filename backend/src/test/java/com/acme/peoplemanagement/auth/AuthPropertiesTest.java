package com.acme.peoplemanagement.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AuthPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void startsWhenCredentialsAreConfigured() {
        contextRunner
                .withPropertyValues("acme.auth.username=hr", "acme.auth.password=secret")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void usesDefaultThrottleSettings() {
        contextRunner
                .withPropertyValues("acme.auth.username=hr", "acme.auth.password=secret")
                .run(context -> {
                    final AuthProperties properties = context.getBean(AuthProperties.class);
                    assertThat(properties.maxFailedAttempts()).isEqualTo(5);
                    assertThat(properties.lockoutWindow()).isEqualTo(Duration.ofSeconds(60));
                });
    }

    @Test
    void refusesToStartWithoutAUsername() {
        contextRunner
                .withPropertyValues("acme.auth.password=secret")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesToStartWithoutAPassword() {
        contextRunner
                .withPropertyValues("acme.auth.username=hr")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesToStartWithABlankPassword() {
        contextRunner
                .withPropertyValues("acme.auth.username=hr", "acme.auth.password=   ")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesToStartWhenTheFailureLimitIsNotPositive() {
        contextRunner
                .withPropertyValues(
                        "acme.auth.username=hr", "acme.auth.password=secret", "acme.auth.max-failed-attempts=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void doesNotRevealThePasswordWhenPrinted() {
        final AuthProperties properties = new AuthProperties("hr", "s3cret-value", 5, Duration.ofSeconds(60));

        assertThat(properties.toString()).contains("hr").doesNotContain("s3cret-value");
    }

    @Configuration
    @EnableConfigurationProperties(AuthProperties.class)
    static class PropertiesConfiguration {
    }
}
