package com.acme.peoplemanagement.readmodel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CurrentSalarySqlTest {

    private static final String ACTIVE = "(e.termination_date is null or e.termination_date >= :asOf)";

    @Test
    void callsAnEmployeeActiveUntilTheEndOfTheirTerminationDate() {
        assertThat(CurrentSalarySql.ACTIVE).isEqualTo(ACTIVE);
    }

    @Test
    void filtersActiveFormerOrNoOneByStatus() {
        assertThat(CurrentSalarySql.statusCondition(StatusFilter.ACTIVE)).contains(ACTIVE);
        assertThat(CurrentSalarySql.statusCondition(StatusFilter.FORMER)).contains("not " + ACTIVE);
        assertThat(CurrentSalarySql.statusCondition(StatusFilter.ALL)).isEmpty();
    }

    @Test
    void takesTheLatestEntryOnOrBeforeAsOfAsTheCurrentSalary() {
        assertThat(CurrentSalarySql.JOIN)
                .contains("s.effective_date <= :asOf")
                .contains("order by s.effective_date desc limit 1")
                .contains("s.employee_id = e.id")
                .endsWith("cs on true");
    }

    @Test
    void exposesTheCurrentAmountCurrencyAndEffectiveDateUnderTheNameCs() {
        assertThat(CurrentSalarySql.JOIN).contains("select s.amount_minor, s.currency_code, s.effective_date");
    }

    @Test
    void readsEmployeesWithTheirDepartmentTitleCountryAndCurrentSalary() {
        assertThat(CurrentSalarySql.EMPLOYEES)
                .startsWith("from employee e")
                .contains("join department d on d.id = e.department_id")
                .contains("join job_title jt on jt.id = e.job_title_id")
                .contains("join country c on c.code = e.country_code")
                .endsWith(CurrentSalarySql.JOIN);
    }
}
