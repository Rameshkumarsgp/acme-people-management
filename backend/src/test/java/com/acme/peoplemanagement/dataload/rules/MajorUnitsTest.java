package com.acme.peoplemanagement.dataload.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

class MajorUnitsTest {

    @Test
    void convertsAnAmountWithTwoDecimalsToMinorUnits() {
        assertThat(MajorUnits.toMinor("1250000.00", 2)).isEqualTo(125_000_000L);
        assertThat(MajorUnits.toMinor("1250000.5", 2)).isEqualTo(125_000_050L);
        assertThat(MajorUnits.toMinor("1250000", 2)).isEqualTo(125_000_000L);
    }

    @Test
    void keepsWholeUnitsForACurrencyWithNoDecimals() {
        assertThat(MajorUnits.toMinor("6500000", 0)).isEqualTo(6_500_000L);
    }

    @Test
    void acceptsZerosBeyondTheDecimalsTheCurrencyAllows() {
        assertThat(MajorUnits.toMinor("10.00", 0)).isEqualTo(10L);
        assertThat(MajorUnits.toMinor("1.500", 2)).isEqualTo(150L);
    }

    @Test
    void rejectsDecimalsForACurrencyWithNone() {
        assertRejected("6500000.5", 0, "This currency has no decimal places.");
        assertRejected("10.01", 0, "This currency has no decimal places.");
    }

    @Test
    void rejectsMoreDecimalsThanTheCurrencyAllowsInsteadOfCuttingThemOff() {
        assertRejected("1.234", 2, "This currency allows at most 2 decimal places.");
    }

    @Test
    void rejectsZero() {
        assertRejected("0", 2, "Amount must be greater than zero.");
        assertRejected("0.00", 2, "Amount must be greater than zero.");
    }

    @Test
    void rejectsAnythingThatIsNotAPlainNumber() {
        final String message = "Amount must be a plain number such as 1250000.00, with no thousands separators.";
        assertRejected("1,250,000.00", 2, message);
        assertRejected("-5", 2, message);
        assertRejected("abc", 2, message);
        assertRejected("", 2, message);
        assertRejected(" 5", 2, message);
        assertRejected("1e5", 2, message);
    }

    @Test
    void acceptsTheLargestAmountAndRejectsOneMore() {
        assertThat(MajorUnits.toMinor("90071992547409.91", 2)).isEqualTo(9_007_199_254_740_991L);
        assertRejected("90071992547409.92", 2, "Amount is too large.");
        assertRejected("99999999999999999999999", 2, "Amount is too large.");
    }

    @Test
    void reportsEveryAmountProblemAgainstTheSalaryField() {
        assertThatExceptionOfType(RowRejectedException.class)
                .isThrownBy(() -> MajorUnits.toMinor("abc", 2))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("salary"));
    }

    @Test
    void formatsMinorUnitsAsMajorUnitsWithTheCurrencysDecimals() {
        assertThat(MajorUnits.format(125_000_000L, 2)).isEqualTo("1250000.00");
        assertThat(MajorUnits.format(5L, 2)).isEqualTo("0.05");
        assertThat(MajorUnits.format(6_500_000L, 0)).isEqualTo("6500000");
    }

    @Test
    void formatsWhatItParses() {
        assertThat(MajorUnits.toMinor(MajorUnits.format(987_654_321L, 2), 2)).isEqualTo(987_654_321L);
    }

    private static void assertRejected(final String text, final int digits, final String reason) {
        assertThatExceptionOfType(RowRejectedException.class)
                .isThrownBy(() -> MajorUnits.toMinor(text, digits))
                .withMessage(reason);
    }
}
