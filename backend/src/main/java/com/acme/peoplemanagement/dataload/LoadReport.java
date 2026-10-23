package com.acme.peoplemanagement.dataload;

import java.util.List;

/** What a load did: how many rows became employees, and the rows that did not. */
record LoadReport(int loaded, List<RejectedRow> rejected) {

    LoadReport {
        rejected = List.copyOf(rejected);
    }

    boolean hasRejections() {
        return !rejected.isEmpty();
    }
}
