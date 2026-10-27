package com.acme.peoplemanagement.directory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.acme.peoplemanagement.readmodel.InvalidQueryException;
import com.acme.peoplemanagement.readmodel.StatusFilter;
import org.junit.jupiter.api.Test;

class DirectoryQueryTest {

    private static final DirectoryParams NOTHING =
            new DirectoryParams(null, null, null, null, null, null, null, null, null);

    @Test
    void appliesTheDefaultsWhenNothingIsGiven() {
        final DirectoryQuery query = DirectoryQuery.from(NOTHING);

        assertThat(query.searchPattern()).isNull();
        assertThat(query.countryCode()).isNull();
        assertThat(query.departmentId()).isNull();
        assertThat(query.jobTitleId()).isNull();
        assertThat(query.currency()).isNull();
        assertThat(query.status()).isEqualTo(StatusFilter.ACTIVE);
        assertThat(query.sort().orderBy()).isEqualTo("lower(e.full_name) asc, e.id");
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(20);
    }

    @Test
    void carriesTheFiltersAndPageThrough() {
        final DirectoryQuery query = DirectoryQuery.from(new DirectoryParams(
                "rao", "IN", 3, 12, "INR", "ALL", "salary,desc", 2, 50));

        assertThat(query.searchPattern()).isEqualTo("%rao%");
        assertThat(query.countryCode()).isEqualTo("IN");
        assertThat(query.departmentId()).isEqualTo(3);
        assertThat(query.jobTitleId()).isEqualTo(12);
        assertThat(query.currency()).isEqualTo("INR");
        assertThat(query.status()).isEqualTo(StatusFilter.ALL);
        assertThat(query.sort().orderBy()).isEqualTo("cs.amount_minor desc, e.id");
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(50);
    }

    @Test
    void treatsABlankCountryAndCurrencyAsNotGiven() {
        final DirectoryQuery query = DirectoryQuery.from(
                new DirectoryParams(null, " ", null, null, "", null, null, null, null));

        assertThat(query.countryCode()).isNull();
        assertThat(query.currency()).isNull();
    }

    @Test
    void trimsTheCountryAndCurrency() {
        final DirectoryQuery query = DirectoryQuery.from(
                new DirectoryParams(null, " IN ", null, null, " INR ", null, null, null, null));

        assertThat(query.countryCode()).isEqualTo("IN");
        assertThat(query.currency()).isEqualTo("INR");
    }

    @Test
    void worksOutTheOffsetFromThePageAndSize() {
        final DirectoryQuery query = DirectoryQuery.from(
                new DirectoryParams(null, null, null, null, null, null, null, 3, 25));

        assertThat(query.offset()).isEqualTo(75);
    }

    @Test
    void acceptsTheLargestPageSizeAndRejectsOneMore() {
        assertThat(DirectoryQuery.from(withSize(100)).size()).isEqualTo(100);
        assertRejected(withSize(101), "size", "Size must be between 1 and 100.");
        assertRejected(withSize(0), "size", "Size must be between 1 and 100.");
    }

    @Test
    void acceptsTheFirstPageAndRejectsANegativePage() {
        assertThat(DirectoryQuery.from(withPage(0)).page()).isZero();
        assertRejected(withPage(-1), "page", "Page must be 0 or more.");
    }

    @Test
    void rejectsSortingBySalaryWithoutACurrencyThroughTheWholeRequest() {
        assertRejected(new DirectoryParams(null, null, null, null, null, null, "salary,asc", null, null),
                "sort", "Sorting by salary needs a currency, because salaries in different currencies "
                        + "cannot be compared.");
    }

    private static DirectoryParams withSize(final int size) {
        return new DirectoryParams(null, null, null, null, null, null, null, null, size);
    }

    private static DirectoryParams withPage(final int page) {
        return new DirectoryParams(null, null, null, null, null, null, null, page, null);
    }

    private static void assertRejected(final DirectoryParams params, final String field, final String message) {
        assertThatExceptionOfType(InvalidQueryException.class)
                .isThrownBy(() -> DirectoryQuery.from(params))
                .withMessage(message)
                .satisfies(exception -> assertThat(exception.field()).isEqualTo(field));
    }
}
