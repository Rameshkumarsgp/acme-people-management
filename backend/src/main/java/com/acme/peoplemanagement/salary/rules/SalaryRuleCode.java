package com.acme.peoplemanagement.salary.rules;

/** The salary timeline rules that can be broken. The names are the stable codes the API returns. */
public enum SalaryRuleCode {
    SALARY_BEFORE_HIRE_DATE,
    DUPLICATE_SALARY_DATE,
    NO_CURRENT_SALARY,
    SALARY_AFTER_TERMINATION,
    TERMINATION_BEFORE_HIRE_DATE
}
