package com.acme.peoplemanagement.employee.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EmployeeStatusTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    @Test
    void isActiveWithoutATerminationDate() {
        assertThat(EmployeeStatus.on(TODAY, null)).isEqualTo(EmployeeStatus.ACTIVE);
    }

    @Test
    void isActiveOnTheLastDayWorked() {
        assertThat(EmployeeStatus.on(TODAY, TODAY)).isEqualTo(EmployeeStatus.ACTIVE);
    }

    @Test
    void isActiveBeforeAFutureTerminationDate() {
        assertThat(EmployeeStatus.on(TODAY, TODAY.plusDays(1))).isEqualTo(EmployeeStatus.ACTIVE);
    }

    @Test
    void isFormerTheDayAfterTheTerminationDate() {
        assertThat(EmployeeStatus.on(TODAY, TODAY.minusDays(1))).isEqualTo(EmployeeStatus.FORMER);
    }
}
