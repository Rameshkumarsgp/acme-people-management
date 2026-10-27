package com.acme.peoplemanagement.readmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmployeeFiltersTest {

    private static final String ACTIVE = "(e.termination_date is null or e.termination_date >= :asOf)";

    @Test
    void keepsOnlyTheStatusConditionWhenNoFilterIsGiven() {
        final Map<String, Object> parameters = new LinkedHashMap<>();

        final List<String> conditions = EmployeeFilters.from(null, null, null, null, null).conditions(parameters);

        assertThat(conditions).containsExactly(ACTIVE);
        assertThat(parameters).isEmpty();
    }

    @Test
    void addsNoConditionWhenEveryStatusIsWanted() {
        assertThat(EmployeeFilters.from(null, null, null, null, "ALL").conditions(new LinkedHashMap<>())).isEmpty();
    }

    @Test
    void addsEachFilterWithItsOwnNamedParameter() {
        final Map<String, Object> parameters = new LinkedHashMap<>();

        final List<String> conditions = EmployeeFilters.from("IN", 3, 12, "INR", "FORMER").conditions(parameters);

        assertThat(conditions).containsExactly("not " + ACTIVE, "e.country_code = :countryCode",
                "e.department_id = :departmentId", "e.job_title_id = :jobTitleId", "cs.currency_code = :currency");
        assertThat(parameters).containsOnly(Map.entry("countryCode", "IN"), Map.entry("departmentId", 3),
                Map.entry("jobTitleId", 12), Map.entry("currency", "INR"));
    }

    @Test
    void treatsBlankTextAsNotGivenAndTrimsTheRest() {
        final EmployeeFilters filters = EmployeeFilters.from(" ", null, null, " INR ", " all ");

        assertThat(filters.countryCode()).isNull();
        assertThat(filters.currency()).isEqualTo("INR");
        assertThat(filters.status()).isEqualTo(StatusFilter.ALL);
    }
}
