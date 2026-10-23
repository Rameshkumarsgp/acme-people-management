package com.acme.peoplemanagement.dataload;

/** A row that was not loaded: where it is, which field, and why. */
record RejectedRow(int rowNumber, String field, String reason) {
}
