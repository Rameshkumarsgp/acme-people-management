package com.acme.peoplemanagement.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.peoplemanagement.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class CurrencyMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void loadsTheCurrenciesOfTheCountriesWeUse() {
        final List<String> codes = jdbcTemplate.queryForList(
                "select code from currency order by code", String.class);

        assertThat(codes).containsExactly("AED", "AUD", "CAD", "EUR", "GBP", "INR", "JPY", "SGD", "USD");
    }

    @Test
    void storesTheMinorUnitDigitsThatTheJdkReportsForEachCurrency() {
        final List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select code, minor_unit_digits from currency");

        assertThat(rows).isNotEmpty().allSatisfy(row -> assertThat(row.get("minor_unit_digits"))
                .isEqualTo(java.util.Currency.getInstance((String) row.get("code")).getDefaultFractionDigits()));
    }

    @Test
    void rejectsANegativeNumberOfMinorUnitDigits() {
        assertThatThrownBy(() -> insertCurrency("ZZZ", -1)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsACodeThatIsNotThreeCapitalLetters() {
        assertThatThrownBy(() -> insertCurrency("usd1", 2)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCurrency("eur", 2)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAnotherRowWithTheSameCode() {
        assertThatThrownBy(() -> insertCurrency("USD", 2)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertCurrency(final String code, final int minorUnitDigits) {
        jdbcTemplate.update("insert into currency (code, minor_unit_digits) values (?, ?)", code, minorUnitDigits);
    }
}
