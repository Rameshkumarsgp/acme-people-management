package com.acme.peoplemanagement.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.peoplemanagement.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class CountryMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void loadsTheTenCountriesWeUse() {
        final List<String> codes = jdbcTemplate.queryForList("select code from country order by code", String.class);

        assertThat(codes).containsExactly("AE", "AU", "CA", "DE", "FR", "GB", "IN", "JP", "SG", "US");
    }

    @Test
    void givesEveryCountryAReadableName() {
        final List<String> names = jdbcTemplate.queryForList("select name from country", String.class);

        assertThat(names).hasSize(10).allSatisfy(name -> assertThat(name).isNotBlank());
    }

    @Test
    void rejectsACodeThatIsNotTwoCapitalLetters() {
        assertThatThrownBy(() -> insertCountry("usa", "Not valid")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCountry("in", "Not valid")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsABlankName() {
        assertThatThrownBy(() -> insertCountry("ZZ", "   ")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAnotherRowWithTheSameCode() {
        assertThatThrownBy(() -> insertCountry("IN", "India again"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertCountry(final String code, final String name) {
        jdbcTemplate.update("insert into country (code, name) values (?, ?)", code, name);
    }
}
