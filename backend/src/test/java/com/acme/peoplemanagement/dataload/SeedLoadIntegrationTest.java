package com.acme.peoplemanagement.dataload;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.peoplemanagement.dataload.rules.EmployeeRow;
import com.acme.peoplemanagement.dataload.seed.SeedCsvWriter;
import com.acme.peoplemanagement.dataload.seed.SeedGenerator;
import com.acme.peoplemanagement.support.FixedClockConfiguration;
import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** The seed obeys the same rules as typed data, leavers included, or the loader would reject it. */
@IntegrationTest
class SeedLoadIntegrationTest {

    private static final int COUNT = 1_500;

    @Autowired
    private EmployeeCsvLoader loader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void removeEmployees() {
        new TestEmployees(jdbcTemplate).deleteAll();
    }

    @Test
    void loadsEverySeededEmployeeIncludingTheLeaversWithoutARejection() throws IOException {
        final List<EmployeeRow> rows = SeedGenerator.generate(COUNT, 42L, FixedClockConfiguration.TODAY);
        final long leavers = rows.stream().filter(row -> row.terminationDate() != null).count();
        final StringWriter csv = new StringWriter();
        SeedCsvWriter.write(rows, csv);

        final LoadReport report = loader.load(new StringReader(csv.toString()));

        assertThat(report.rejected()).isEmpty();
        assertThat(report.loaded()).isEqualTo(COUNT);
        assertThat(leavers).isPositive();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from employee where termination_date is not null", Long.class)).isEqualTo(leavers);
    }
}
