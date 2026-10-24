package com.acme.peoplemanagement.directory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.acme.peoplemanagement.directory.errors.InvalidDirectoryRequestException;
import org.junit.jupiter.api.Test;

class SearchPatternTest {

    @Test
    void meansNoSearchWhenTheTextIsMissingOrBlank() {
        assertThat(SearchPattern.of(null)).isEmpty();
        assertThat(SearchPattern.of("")).isEmpty();
        assertThat(SearchPattern.of("   ")).isEmpty();
    }

    @Test
    void trimsTheTextAndMatchesAnywhereInTheValue() {
        assertThat(SearchPattern.of("  rao ")).contains("%rao%");
    }

    @Test
    void escapesTheCharactersThatLikeTreatsAsWildcards() {
        assertThat(SearchPattern.of("100%")).contains("%100\\%%");
        assertThat(SearchPattern.of("john_doe")).contains("%john\\_doe%");
        assertThat(SearchPattern.of("a\\b")).contains("%a\\\\b%");
    }

    @Test
    void acceptsAHundredCharactersAndRejectsOneMore() {
        assertThat(SearchPattern.of("x".repeat(100))).isPresent();
        assertThatExceptionOfType(InvalidDirectoryRequestException.class)
                .isThrownBy(() -> SearchPattern.of("x".repeat(101)))
                .withMessage("Search text must be at most 100 characters.")
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("q"));
    }
}
