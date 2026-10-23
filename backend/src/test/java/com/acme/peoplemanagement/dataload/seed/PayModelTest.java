package com.acme.peoplemanagement.dataload.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.peoplemanagement.dataload.seed.PayModel.CountryPay;
import com.acme.peoplemanagement.dataload.seed.PayModel.TitlePay;
import org.junit.jupiter.api.Test;

class PayModelTest {

    @Test
    void paysAManagerMoreThanAnAnalystInTheSameCountry() {
        final TitlePay manager = title("Finance Manager");
        final TitlePay analyst = title("Financial Analyst");

        for (final CountryPay country : PayModel.countries()) {
            assertThat(PayModel.majorSalary(manager, country, 3, 0))
                    .as(country.code())
                    .isGreaterThan(PayModel.majorSalary(analyst, country, 3, 0));
        }
    }

    @Test
    void paysMoreInTheUnitedStatesThanInIndiaForTheSameTitleInUsdTerms() {
        final TitlePay engineer = title("Software Engineer");

        assertThat(PayModel.majorSalary(engineer, country("US"), 0, 0)).isEqualTo(90_000L);
        assertThat(PayModel.majorSalary(engineer, country("IN"), 0, 0)).isEqualTo(2_241_000L);
    }

    @Test
    void paysMoreWithTenureUpToTenYears() {
        final TitlePay engineer = title("Software Engineer");
        final CountryPay unitedStates = country("US");

        assertThat(PayModel.majorSalary(engineer, unitedStates, 5, 0))
                .isGreaterThan(PayModel.majorSalary(engineer, unitedStates, 0, 0));
        assertThat(PayModel.majorSalary(engineer, unitedStates, 10, 0))
                .isGreaterThan(PayModel.majorSalary(engineer, unitedStates, 9, 0));
    }

    @Test
    void stopsTheTenureUpliftAfterTenYears() {
        final TitlePay engineer = title("Software Engineer");
        final CountryPay unitedStates = country("US");

        assertThat(PayModel.majorSalary(engineer, unitedStates, 25, 0))
                .isEqualTo(PayModel.majorSalary(engineer, unitedStates, 10, 0));
    }

    @Test
    void movesPayByTheNoiseButNeverMoreThanTenPercent() {
        final TitlePay engineer = title("Software Engineer");
        final CountryPay unitedStates = country("US");
        final long exact = PayModel.majorSalary(engineer, unitedStates, 0, 0);

        assertThat(PayModel.majorSalary(engineer, unitedStates, 0, PayModel.MAX_NOISE_PERCENT))
                .isGreaterThan(exact).isLessThanOrEqualTo(exact * 110 / 100);
        assertThat(PayModel.majorSalary(engineer, unitedStates, 0, -PayModel.MAX_NOISE_PERCENT))
                .isLessThan(exact).isGreaterThanOrEqualTo(exact * 90 / 100);
    }

    @Test
    void roundsEveryAmountToAWholeThousand() {
        for (final TitlePay title : PayModel.titles()) {
            for (final CountryPay country : PayModel.countries()) {
                final long salary = PayModel.majorSalary(title, country, 4, 7);
                assertThat(salary).isPositive().isEqualTo(salary / 1_000 * 1_000);
            }
        }
    }

    @Test
    void weighsTheCountriesToOneHundredPercent() {
        assertThat(PayModel.countries().stream().mapToInt(CountryPay::weightPercent).sum()).isEqualTo(100);
    }

    @Test
    void paysEachCountryInItsOwnCurrency() {
        assertThat(country("IN").currency()).isEqualTo("INR");
        assertThat(country("DE").currency()).isEqualTo("EUR");
        assertThat(country("FR").currency()).isEqualTo("EUR");
        assertThat(country("JP").currency()).isEqualTo("JPY");
    }

    private static TitlePay title(final String name) {
        return PayModel.titles().stream().filter(title -> title.title().equals(name)).findFirst().orElseThrow();
    }

    private static CountryPay country(final String code) {
        return PayModel.countries().stream().filter(country -> country.code().equals(code)).findFirst().orElseThrow();
    }
}
