package com.acme.peoplemanagement.directory.rules;

/** What the request says, as written. {@link DirectoryQuery#from} checks it and fills in the defaults. */
public record DirectoryParams(
        String q,
        String countryCode,
        Integer departmentId,
        Integer jobTitleId,
        String currency,
        String status,
        String sort,
        Integer page,
        Integer size) {
}
