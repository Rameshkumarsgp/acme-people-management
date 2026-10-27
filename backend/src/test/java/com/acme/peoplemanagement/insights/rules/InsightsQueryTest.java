package com.acme.peoplemanagement.insights.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.acme.peoplemanagement.readmodel.EmployeeFilters;
import com.acme.peoplemanagement.readmodel.InvalidQueryException;
import com.acme.peoplemanagement.readmodel.StatusFilter;
import org.junit.jupiter.api.Test;

class InsightsQueryTest {

    @Test
    void sumsEveryActiveEmployeeWhenNothingIsGiven() {
        final InsightsQuery query = InsightsQuery.from(new InsightsParams(null, null, null, null, null, null));

        assertThat(query.dimension()).isEqualTo(Dimension.OVERALL);
        assertThat(query.filters()).isEqualTo(new EmployeeFilters(null, null, null, null, StatusFilter.ACTIVE));
    }

    @Test
    void carriesTheDimensionAndFiltersThrough() {
        final InsightsQuery query = InsightsQuery.from(new InsightsParams("COUNTRY", "IN", 3, 12, "INR", "ALL"));

        assertThat(query.dimension()).isEqualTo(Dimension.COUNTRY);
        assertThat(query.filters()).isEqualTo(new EmployeeFilters("IN", 3, 12, "INR", StatusFilter.ALL));
    }

    @Test
    void rejectsAnUnknownStatus() {
        assertThatExceptionOfType(InvalidQueryException.class)
                .isThrownBy(() -> InsightsQuery.from(new InsightsParams(null, null, null, null, null, "GONE")))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("status"));
    }
}
