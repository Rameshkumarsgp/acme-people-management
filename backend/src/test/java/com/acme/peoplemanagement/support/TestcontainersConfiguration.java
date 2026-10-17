package com.acme.peoplemanagement.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** A real PostgreSQL for the integration tests. Spring connects to it without any URL or password. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** The same major version as compose.yaml. */
    private static final String POSTGRES_IMAGE = "postgres:17";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }
}
