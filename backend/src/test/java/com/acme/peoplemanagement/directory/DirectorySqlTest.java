package com.acme.peoplemanagement.directory;

import static com.acme.peoplemanagement.directory.DirectoryParamsBuilder.params;
import static org.assertj.core.api.Assertions.assertThat;

import com.acme.peoplemanagement.directory.rules.DirectoryQuery;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DirectorySqlTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 19);
    private static final String ACTIVE = "(e.termination_date is null or e.termination_date >= :asOf)";

    @Test
    void addsNoWhereWhenEveryoneIsListedWithoutFilters() {
        final DirectorySql.Statements sql = build(params().status("ALL"));

        assertThat(sql.page()).contains("cs on true order by");
        assertThat(sql.count()).endsWith("cs on true");
    }

    @Test
    void listsActiveEmployeesByDefault() {
        final DirectorySql.Statements sql = build(params());

        assertThat(sql.page()).contains("where " + ACTIVE);
        assertThat(sql.count()).contains("where " + ACTIVE);
        assertThat(sql.parameters()).containsEntry("asOf", AS_OF);
    }

    @Test
    void listsFormerEmployeesAsTheOppositeOfActive() {
        final DirectorySql.Statements sql = build(params().status("FORMER"));

        assertThat(sql.page()).contains("where not " + ACTIVE);
    }

    @Test
    void showsTheStatusWithTheSameConditionTheFilterUses() {
        assertThat(build(params()).page())
                .contains("case when " + ACTIVE + " then 'ACTIVE' else 'FORMER' end as status");
    }

    @Test
    void addsEachFilterWithItsOwnNamedParameter() {
        final DirectorySql.Statements sql = build(params().status("ALL").country("IN").department(3).jobTitle(12)
                .currency("INR"));

        assertThat(sql.page()).contains("e.country_code = :countryCode", "e.department_id = :departmentId",
                "e.job_title_id = :jobTitleId", "cs.currency_code = :currency");
        assertThat(sql.parameters()).containsEntry("countryCode", "IN").containsEntry("departmentId", 3)
                .containsEntry("jobTitleId", 12).containsEntry("currency", "INR");
    }

    @Test
    void searchesNameAndEmailWithTheEscapeCharacter() {
        final DirectorySql.Statements sql = build(params().status("ALL").search("rao"));

        assertThat(sql.page()).contains("cs on true where "
                + "(e.full_name ilike :search escape '\\' or e.email ilike :search escape '\\') order by");
        assertThat(sql.parameters()).containsEntry("search", "%rao%");
    }

    @Test
    void keepsTextFromTheClientOutOfTheSql() {
        final DirectorySql.Statements sql = build(params().search("'; drop table employee; --")
                .country("x' or '1'='1"));

        assertThat(sql.page()).doesNotContain("drop table").doesNotContain("1'='1");
        assertThat(sql.count()).doesNotContain("drop table").doesNotContain("1'='1");
        assertThat(sql.parameters()).containsEntry("countryCode", "x' or '1'='1");
    }

    @Test
    void ordersByTheSortAndPagesWithLimitAndOffset() {
        final DirectorySql.Statements sql = build(params().sort("hireDate,desc").page(2).size(25));

        assertThat(sql.page()).endsWith("order by e.hire_date desc, e.id limit :limit offset :offset");
        assertThat(sql.parameters()).containsEntry("limit", 25).containsEntry("offset", 50);
    }

    @Test
    void countsTheSameRowsWithoutTheOrderOrThePage() {
        final DirectorySql.Statements sql = build(params().status("ALL").country("IN").sort("hireDate,desc"));

        assertThat(sql.count()).startsWith("select count(*) from employee e")
                .contains("e.country_code = :countryCode")
                .doesNotContain(":limit").doesNotContain(":offset").doesNotContain("e.hire_date desc");
    }

    @Test
    void readsTheCurrentSalaryFromTheLatestEntryOnOrBeforeAsOf() {
        assertThat(build(params()).page()).contains("s.effective_date <= :asOf")
                .contains("order by s.effective_date desc limit 1");
    }

    private static DirectorySql.Statements build(final DirectoryParamsBuilder builder) {
        return DirectorySql.build(DirectoryQuery.from(builder.build()), AS_OF);
    }
}
