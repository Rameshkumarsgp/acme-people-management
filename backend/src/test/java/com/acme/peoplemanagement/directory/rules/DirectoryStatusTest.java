package com.acme.peoplemanagement.directory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.acme.peoplemanagement.directory.errors.InvalidDirectoryRequestException;
import org.junit.jupiter.api.Test;

class DirectoryStatusTest {

    @Test
    void defaultsToActiveWhenNothingIsGiven() {
        assertThat(DirectoryStatus.parse(null)).isEqualTo(DirectoryStatus.ACTIVE);
        assertThat(DirectoryStatus.parse(" ")).isEqualTo(DirectoryStatus.ACTIVE);
    }

    @Test
    void readsEachStatusIgnoringCase() {
        assertThat(DirectoryStatus.parse("FORMER")).isEqualTo(DirectoryStatus.FORMER);
        assertThat(DirectoryStatus.parse("all")).isEqualTo(DirectoryStatus.ALL);
        assertThat(DirectoryStatus.parse("Active")).isEqualTo(DirectoryStatus.ACTIVE);
    }

    @Test
    void rejectsAnUnknownStatusAgainstItsField() {
        assertThatExceptionOfType(InvalidDirectoryRequestException.class)
                .isThrownBy(() -> DirectoryStatus.parse("retired"))
                .withMessage("Status must be ACTIVE, FORMER or ALL.")
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("status"));
    }
}
