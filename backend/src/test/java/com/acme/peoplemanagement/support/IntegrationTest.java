package com.acme.peoplemanagement.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * A test that starts the whole application against a real PostgreSQL in Docker.
 * Tagged so the fast test task leaves it out. Run it with the integrationTest task.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
