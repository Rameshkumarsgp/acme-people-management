package com.acme.peoplemanagement.reference;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.peoplemanagement.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ReferenceLookupIntegrationTest {

    @Autowired
    private ReferenceLookup lookup;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findsADepartmentById() {
        final int id = idOf("department", "Engineering");

        assertThat(lookup.department(id)).contains(new DepartmentResponse(id, "Engineering"));
    }

    @Test
    void findsAJobTitleById() {
        final int id = idOf("job_title", "Software Engineer");

        assertThat(lookup.jobTitle(id)).contains(new JobTitleResponse(id, "Software Engineer"));
    }

    @Test
    void findsACountryByCode() {
        assertThat(lookup.country("IN")).contains(new CountryResponse("IN", "India"));
    }

    @Test
    void findsACurrencyWithItsMinorUnitDigits() {
        assertThat(lookup.currency("JPY")).contains(new CurrencyResponse("JPY", 0));
    }

    @Test
    void answersEmptyForValuesThatDoNotExist() {
        assertThat(lookup.department(-1)).isEmpty();
        assertThat(lookup.jobTitle(-1)).isEmpty();
        assertThat(lookup.country("ZZ")).isEmpty();
        assertThat(lookup.currency("ZZZ")).isEmpty();
    }

    private int idOf(final String table, final String name) {
        final Integer id = jdbcTemplate.queryForObject(
                "select id from " + table + " where name = ?", Integer.class, name);
        return id == null ? -1 : id;
    }
}
