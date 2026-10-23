package com.acme.peoplemanagement.dataload;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class BatchModeTest {

    @Test
    void isRequestedByTheLoadOption() {
        assertThat(BatchMode.requestedBy(new MockEnvironment().withProperty("acme.load", "employees.csv"))).isTrue();
    }

    @Test
    void isRequestedByTheSeedOption() {
        assertThat(BatchMode.requestedBy(new MockEnvironment().withProperty("acme.seed-csv", "seed.csv"))).isTrue();
    }

    @Test
    void isNotRequestedByAnOrdinaryRun() {
        assertThat(BatchMode.requestedBy(new MockEnvironment())).isFalse();
    }
}
