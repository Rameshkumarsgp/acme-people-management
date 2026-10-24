package com.acme.peoplemanagement.directory;

import com.acme.peoplemanagement.directory.rules.DirectoryParams;

/** Builds a directory request one field at a time, so each test names only what it cares about. */
final class DirectoryParamsBuilder {

    private String search;
    private String country;
    private Integer department;
    private Integer jobTitle;
    private String currency;
    private String status;
    private String sort;
    private Integer page;
    private Integer size;

    static DirectoryParamsBuilder params() {
        return new DirectoryParamsBuilder();
    }

    DirectoryParamsBuilder search(final String value) {
        search = value;
        return this;
    }

    DirectoryParamsBuilder country(final String value) {
        country = value;
        return this;
    }

    DirectoryParamsBuilder department(final int value) {
        department = value;
        return this;
    }

    DirectoryParamsBuilder jobTitle(final int value) {
        jobTitle = value;
        return this;
    }

    DirectoryParamsBuilder currency(final String value) {
        currency = value;
        return this;
    }

    DirectoryParamsBuilder status(final String value) {
        status = value;
        return this;
    }

    DirectoryParamsBuilder sort(final String value) {
        sort = value;
        return this;
    }

    DirectoryParamsBuilder page(final int value) {
        page = value;
        return this;
    }

    DirectoryParamsBuilder size(final int value) {
        size = value;
        return this;
    }

    DirectoryParams build() {
        return new DirectoryParams(search, country, department, jobTitle, currency, status, sort, page, size);
    }
}
