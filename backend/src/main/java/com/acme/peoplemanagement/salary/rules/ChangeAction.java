package com.acme.peoplemanagement.salary.rules;

/** What happened to a salary entry. The names are stored in the change log and returned by the API. */
public enum ChangeAction {
    ADDED,
    EDITED,
    DELETED
}
