package com.acme.peoplemanagement.dataload.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.peoplemanagement.dataload.seed.PayModel.CountryPay;
import com.acme.peoplemanagement.dataload.seed.PayModel.TitlePay;
import com.acme.peoplemanagement.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** The generator keeps its own table, so a change to the reference data must fail here rather than in a load. */
@IntegrationTest
class SeedReferenceIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void usesExactlyTheJobTitlesInTheReferenceData() {
        assertThat(PayModel.titles().stream().map(TitlePay::title).toList())
                .containsExactlyInAnyOrderElementsOf(names("job_title"));
    }

    @Test
    void usesExactlyTheDepartmentsInTheReferenceData() {
        assertThat(PayModel.titles().stream().map(TitlePay::department).distinct().toList())
                .containsExactlyInAnyOrderElementsOf(names("department"));
    }

    @Test
    void usesExactlyTheCountriesInTheReferenceData() {
        assertThat(PayModel.countries().stream().map(CountryPay::code).toList())
                .containsExactlyInAnyOrderElementsOf(codes("country"));
    }

    @Test
    void paysOnlyInCurrenciesInTheReferenceData() {
        assertThat(codes("currency")).containsAll(PayModel.countries().stream().map(CountryPay::currency).toList());
    }

    private List<String> names(final String table) {
        return jdbcTemplate.queryForList("select name from " + table, String.class);
    }

    private List<String> codes(final String table) {
        return jdbcTemplate.queryForList("select code from " + table, String.class);
    }
}
