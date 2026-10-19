package com.acme.peoplemanagement.salary.rules;

/** A salary change would break a timeline rule. The detail is written for the person who made the change. */
public final class SalaryRuleViolation extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final SalaryRuleCode code;

    public SalaryRuleViolation(final SalaryRuleCode code, final String detail) {
        super(detail);
        this.code = code;
    }

    public SalaryRuleCode code() {
        return code;
    }
}
