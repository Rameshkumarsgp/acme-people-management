package com.acme.peoplemanagement.insights.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.acme.peoplemanagement.readmodel.EmployeeFilters;
import com.acme.peoplemanagement.readmodel.InvalidQueryException;
import com.acme.peoplemanagement.readmodel.StatusFilter;
import org.junit.jupiter.api.Test;

class ExtremesQueryTest {

    private static ExtremesParams params(final String order, final String currency, final Integer limit) {
        return new ExtremesParams(order, currency, limit, null, null, null, null);
    }

    @Test
    void listsTheTenHighestPaidActiveEmployeesOfACurrencyByDefault() {
        final ExtremesQuery query = ExtremesQuery.from(params(null, "INR", null));

        assertThat(query.order()).isEqualTo(ExtremesOrder.HIGHEST);
        assertThat(query.limit()).isEqualTo(10);
        assertThat(query.filters()).isEqualTo(new EmployeeFilters(null, null, null, "INR", StatusFilter.ACTIVE));
    }

    @Test
    void carriesTheOrderLimitAndFiltersThrough() {
        final ExtremesQuery query = ExtremesQuery.from(
                new ExtremesParams("LOWEST", "INR", 50, "IN", 3, 12, "FORMER"));

        assertThat(query.order()).isEqualTo(ExtremesOrder.LOWEST);
        assertThat(query.limit()).isEqualTo(50);
        assertThat(query.filters()).isEqualTo(new EmployeeFilters("IN", 3, 12, "INR", StatusFilter.FORMER));
    }

    @Test
    void acceptsTheSmallestLimit() {
        assertThat(ExtremesQuery.from(params(null, "INR", 1)).limit()).isEqualTo(1);
    }

    @Test
    void rejectsALimitBelowOneOrAboveFifty() {
        for (final int limit : new int[] {0, -1, 51}) {
            assertThatExceptionOfType(InvalidQueryException.class)
                    .isThrownBy(() -> ExtremesQuery.from(params(null, "INR", limit)))
                    .satisfies(exception -> {
                        assertThat(exception.field()).isEqualTo("limit");
                        assertThat(exception.getMessage()).isEqualTo("Limit must be between 1 and 50.");
                    });
        }
    }

    @Test
    void needsACurrencyBecauseAmountsInDifferentCurrenciesCannotBeRanked() {
        for (final String currency : new String[] {null, "", " "}) {
            assertThatExceptionOfType(InvalidQueryException.class)
                    .isThrownBy(() -> ExtremesQuery.from(params(null, currency, null)))
                    .satisfies(exception -> {
                        assertThat(exception.field()).isEqualTo("currency");
                        assertThat(exception.getMessage()).isEqualTo("Currency is required to rank salaries.");
                    });
        }
    }
}
