package com.acme.peoplemanagement.readmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

class StatusFilterTest {

    @Test
    void defaultsToActiveWhenNothingIsGiven() {
        assertThat(StatusFilter.parse(null)).isEqualTo(StatusFilter.ACTIVE);
        assertThat(StatusFilter.parse(" ")).isEqualTo(StatusFilter.ACTIVE);
    }

    @Test
    void readsEachStatusIgnoringCase() {
        assertThat(StatusFilter.parse("FORMER")).isEqualTo(StatusFilter.FORMER);
        assertThat(StatusFilter.parse("all")).isEqualTo(StatusFilter.ALL);
        assertThat(StatusFilter.parse("Active")).isEqualTo(StatusFilter.ACTIVE);
    }

    @Test
    void rejectsAnUnknownStatusAgainstItsField() {
        assertThatExceptionOfType(InvalidQueryException.class)
                .isThrownBy(() -> StatusFilter.parse("retired"))
                .withMessage("Status must be ACTIVE, FORMER or ALL.")
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("status"));
    }
}
