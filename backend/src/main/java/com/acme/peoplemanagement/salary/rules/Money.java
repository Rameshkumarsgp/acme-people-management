package com.acme.peoplemanagement.salary.rules;

/** An amount in minor units (the smallest unit of the currency, for example paise) and the currency it is paid in. */
public record Money(long amountMinor, String currencyCode) {

    /**
     * The largest integer that a double and a JavaScript number both hold exactly. The check on
     * salary_entry.amount_minor states the same value.
     */
    public static final long MAX_AMOUNT_MINOR = 9_007_199_254_740_991L;
}
