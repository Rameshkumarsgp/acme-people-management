package com.acme.peoplemanagement.directory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.acme.peoplemanagement.directory.errors.InvalidDirectoryRequestException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DirectorySortTest {

    @Test
    void sortsByNameAscendingWhenNothingIsGiven() {
        assertThat(DirectorySort.parse(null, null).orderBy()).isEqualTo("lower(e.full_name) asc, e.id");
        assertThat(DirectorySort.parse("  ", null).orderBy()).isEqualTo("lower(e.full_name) asc, e.id");
    }

    @Test
    void mapsEachFieldToItsOwnFixedExpression() {
        final Map<String, String> expressions = Map.of(
                "fullName", "lower(e.full_name)",
                "department", "lower(d.name)",
                "jobTitle", "lower(jt.name)",
                "country", "lower(c.name)",
                "hireDate", "e.hire_date",
                "salary", "cs.amount_minor");

        expressions.forEach((field, expression) ->
                assertThat(DirectorySort.parse(field + ",asc", "INR").orderBy()).isEqualTo(expression + " asc, e.id"));
    }

    @Test
    void readsTheDirectionAndDefaultsToAscending() {
        assertThat(DirectorySort.parse("hireDate,desc", null).orderBy()).isEqualTo("e.hire_date desc, e.id");
        assertThat(DirectorySort.parse("hireDate,DESC", null).orderBy()).isEqualTo("e.hire_date desc, e.id");
        assertThat(DirectorySort.parse("hireDate", null).orderBy()).isEqualTo("e.hire_date asc, e.id");
    }

    @Test
    void alwaysBreaksTiesByIdAscending() {
        assertThat(DirectorySort.parse("country,desc", null).orderBy()).endsWith(", e.id");
    }

    @Test
    void rejectsAFieldThatIsNotOnTheList() {
        assertRejected("password,asc", null, "sort", "Sort by one of fullName, department, jobTitle, country,"
                + " hireDate, salary.");
        assertRejected("e.id; drop table employee", null, "sort", "Sort by one of fullName, department, jobTitle,"
                + " country, hireDate, salary.");
    }

    @Test
    void rejectsADirectionThatIsNeitherAscNorDesc() {
        assertRejected("fullName,sideways", null, "sort", "The direction must be asc or desc.");
    }

    @Test
    void rejectsADirectionLeftEmpty() {
        assertRejected("fullName,", null, "sort", "The direction must be asc or desc.");
    }

    @Test
    void rejectsMoreThanAFieldAndADirection() {
        assertRejected("fullName,asc,extra", null, "sort", "The direction must be asc or desc.");
    }

    @Test
    void rejectsSortingBySalaryWithoutACurrency() {
        final String message = "Sorting by salary needs a currency, because salaries in different currencies "
                + "cannot be compared.";
        assertRejected("salary,desc", null, "sort", message);
        assertRejected("salary,desc", " ", "sort", message);
    }

    @Test
    void sortsBySalaryWhenACurrencyIsGiven() {
        assertThat(DirectorySort.parse("salary,desc", "INR").orderBy()).isEqualTo("cs.amount_minor desc, e.id");
    }

    private static void assertRejected(
            final String sort, final String currency, final String field, final String message) {
        assertThatExceptionOfType(InvalidDirectoryRequestException.class)
                .isThrownBy(() -> DirectorySort.parse(sort, currency))
                .withMessage(message)
                .satisfies(exception -> assertThat(exception.field()).isEqualTo(field));
    }
}
