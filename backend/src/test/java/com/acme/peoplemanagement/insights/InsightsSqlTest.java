package com.acme.peoplemanagement.insights;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.peoplemanagement.insights.rules.InsightsParams;
import com.acme.peoplemanagement.insights.rules.InsightsQuery;
import com.acme.peoplemanagement.readmodel.CurrentSalarySql;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InsightsSqlTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 19);
    private static final String ACTIVE = "(e.termination_date is null or e.termination_date >= :asOf)";

    private static InsightsSql.Statement build(final String dimension, final String status) {
        return InsightsSql.build(
                InsightsQuery.from(new InsightsParams(dimension, null, null, null, null, status)), AS_OF);
    }

    @Test
    void groupsByTheDimensionAndTheCurrencyOfTheCurrentSalary() {
        final InsightsSql.Statement sql = build("COUNTRY", null);

        assertThat(sql.text()).startsWith("select e.country_code as group_key, c.name as group_label, "
                + "cs.currency_code as currency,");
        assertThat(sql.text()).contains("group by 1, 2, 3");
    }

    @Test
    void readsTheEmployeesWithTheirCurrentSalary() {
        assertThat(build("COUNTRY", null).text()).contains(CurrentSalarySql.EMPLOYEES);
    }

    @Test
    void computesTheSixMeasuresInTheDatabase() {
        assertThat(build("COUNTRY", null).text())
                .contains("count(*) as headcount")
                .contains("sum(cs.amount_minor::numeric) as total")
                .contains("min(cs.amount_minor) as min_amount")
                .contains("round(percentile_cont(0.5) within group (order by cs.amount_minor)::numeric) "
                        + "as median_amount")
                .contains("round(avg(cs.amount_minor::numeric)) as mean_amount")
                .contains("max(cs.amount_minor) as max_amount");
    }

    @Test
    void ordersByTheDimensionThenTheCurrency() {
        assertThat(build("COUNTRY", null).text()).endsWith("order by group_label, currency");
        assertThat(build("TENURE", null).text()).endsWith("order by group_key, currency");
    }

    @Test
    void countsActiveEmployeesByDefault() {
        final InsightsSql.Statement sql = build("COUNTRY", null);

        assertThat(sql.text()).contains(" where " + ACTIVE + " group by");
        assertThat(sql.parameters()).containsEntry("asOf", AS_OF);
    }

    @Test
    void addsNoWhereWhenEveryoneIsWanted() {
        assertThat(build("COUNTRY", "ALL").text()).contains("cs on true group by 1, 2, 3");
    }

    @Test
    void bindsEachFilterAsANamedParameter() {
        final InsightsSql.Statement sql = InsightsSql.build(InsightsQuery.from(
                new InsightsParams("DEPARTMENT", "IN", 3, 12, "INR", "ALL")), AS_OF);

        assertThat(sql.text()).contains("e.country_code = :countryCode", "e.department_id = :departmentId",
                "e.job_title_id = :jobTitleId", "cs.currency_code = :currency");
        assertThat(sql.parameters()).containsEntry("countryCode", "IN").containsEntry("departmentId", 3)
                .containsEntry("jobTitleId", 12).containsEntry("currency", "INR");
    }

    @Test
    void keepsTextFromTheClientOutOfTheSql() {
        final InsightsSql.Statement sql = InsightsSql.build(InsightsQuery.from(
                new InsightsParams("COUNTRY", "x' or '1'='1", null, null, "'; drop table employee; --", null)), AS_OF);

        assertThat(sql.text()).doesNotContain("drop table").doesNotContain("1'='1");
        assertThat(sql.parameters()).containsEntry("countryCode", "x' or '1'='1");
    }
}
