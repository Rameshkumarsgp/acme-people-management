package com.acme.peoplemanagement.insights.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.acme.peoplemanagement.readmodel.InvalidQueryException;
import org.junit.jupiter.api.Test;

class ExtremesOrderTest {

    @Test
    void listsTheHighestPaidWhenNoOrderIsGiven() {
        assertThat(ExtremesOrder.parse(null)).isEqualTo(ExtremesOrder.HIGHEST);
        assertThat(ExtremesOrder.parse("")).isEqualTo(ExtremesOrder.HIGHEST);
    }

    @Test
    void readsAnOrderIgnoringCaseAndSpaces() {
        assertThat(ExtremesOrder.parse(" lowest ")).isEqualTo(ExtremesOrder.LOWEST);
    }

    @Test
    void sortsTheHighestDownAndTheLowestUp() {
        assertThat(ExtremesOrder.HIGHEST.direction()).isEqualTo("desc");
        assertThat(ExtremesOrder.LOWEST.direction()).isEqualTo("asc");
    }

    @Test
    void rejectsAnUnknownOrder() {
        assertThatExceptionOfType(InvalidQueryException.class)
                .isThrownBy(() -> ExtremesOrder.parse("MIDDLE"))
                .satisfies(exception -> {
                    assertThat(exception.field()).isEqualTo("order");
                    assertThat(exception.getMessage()).isEqualTo("Order must be HIGHEST or LOWEST.");
                });
    }
}
