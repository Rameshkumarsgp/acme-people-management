package com.acme.peoplemanagement;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.peoplemanagement.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class MigrationsIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesTheMigrationsToAFreshDatabase() {
        final Integer trackingTables = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'databasechangelog'",
                Integer.class);

        assertThat(trackingTables).isEqualTo(1);
    }
}
