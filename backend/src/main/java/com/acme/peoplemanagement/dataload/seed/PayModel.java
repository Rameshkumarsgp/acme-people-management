package com.acme.peoplemanagement.dataload.seed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Synthetic pay for the seed: a base per job title, times a factor per country, times a tenure uplift, times noise.
 * The factors and rates are invented to give insights something to find. They are not market data and not currency
 * conversion, and the product never converts.
 */
public final class PayModel {

    public static final int MAX_NOISE_PERCENT = 10;

    private static final BigDecimal ANNUAL_UPLIFT = new BigDecimal("1.04");
    private static final int MAX_UPLIFT_YEARS = 10;
    private static final BigDecimal ROUNDING_STEP = BigDecimal.valueOf(1_000);

    private static final List<TitlePay> TITLES = List.of(
            new TitlePay("Software Engineer", "Engineering", 90_000),
            new TitlePay("QA Engineer", "Engineering", 70_000),
            new TitlePay("DevOps Engineer", "Engineering", 95_000),
            new TitlePay("Engineering Manager", "Engineering", 140_000),
            new TitlePay("Data Analyst", "Data and Analytics", 70_000),
            new TitlePay("Data Scientist", "Data and Analytics", 110_000),
            new TitlePay("Product Manager", "Product", 120_000),
            new TitlePay("Product Designer", "Design", 85_000),
            new TitlePay("UX Researcher", "Design", 80_000),
            new TitlePay("Sales Executive", "Sales", 65_000),
            new TitlePay("Account Manager", "Sales", 80_000),
            new TitlePay("Sales Manager", "Sales", 115_000),
            new TitlePay("Marketing Specialist", "Marketing", 65_000),
            new TitlePay("Content Writer", "Marketing", 50_000),
            new TitlePay("Customer Support Agent", "Customer Support", 38_000),
            new TitlePay("Support Team Lead", "Customer Support", 55_000),
            new TitlePay("Accountant", "Finance", 60_000),
            new TitlePay("Financial Analyst", "Finance", 75_000),
            new TitlePay("Finance Manager", "Finance", 110_000),
            new TitlePay("Recruiter", "Human Resources", 55_000),
            new TitlePay("HR Business Partner", "Human Resources", 75_000),
            new TitlePay("HR Manager", "Human Resources", 105_000),
            new TitlePay("Operations Analyst", "Operations", 60_000),
            new TitlePay("Operations Manager", "Operations", 100_000));

    private static final List<CountryPay> COUNTRIES = List.of(
            country("IN", "INR", 30, "0.30", "83"),
            country("US", "USD", 22, "1.00", "1"),
            country("GB", "GBP", 10, "0.78", "0.79"),
            country("DE", "EUR", 8, "0.82", "0.92"),
            country("CA", "CAD", 6, "0.85", "1.36"),
            country("FR", "EUR", 6, "0.75", "0.92"),
            country("SG", "SGD", 6, "0.90", "1.34"),
            country("AU", "AUD", 5, "0.85", "1.52"),
            country("JP", "JPY", 4, "0.65", "150"),
            country("AE", "AED", 3, "0.80", "3.67"));

    private PayModel() {
    }

    /** A job title, the department it belongs to, and its base annual pay in USD. */
    public record TitlePay(String title, String department, long baseUsd) {
    }

    /** A country, its currency, its share of the seed, its pay level against the US, and local units per USD. */
    public record CountryPay(
            String code, String currency, int weightPercent, BigDecimal payFactor, BigDecimal perUsd) {
    }

    public static List<TitlePay> titles() {
        return TITLES;
    }

    public static List<CountryPay> countries() {
        return COUNTRIES;
    }

    /** Annual pay in whole major units of the country's currency, rounded to the nearest thousand. */
    public static long majorSalary(
            final TitlePay title, final CountryPay country, final int tenureYears, final int noisePercent) {
        final BigDecimal uplift = ANNUAL_UPLIFT.pow(Math.min(tenureYears, MAX_UPLIFT_YEARS));
        final BigDecimal noise = BigDecimal.valueOf(100L + noisePercent).movePointLeft(2);
        final BigDecimal exact = BigDecimal.valueOf(title.baseUsd())
                .multiply(country.payFactor())
                .multiply(country.perUsd())
                .multiply(uplift)
                .multiply(noise);
        return exact.divide(ROUNDING_STEP, 0, RoundingMode.HALF_UP).multiply(ROUNDING_STEP).longValueExact();
    }

    private static CountryPay country(
            final String code, final String currency, final int weight, final String payFactor, final String perUsd) {
        return new CountryPay(code, currency, weight, new BigDecimal(payFactor), new BigDecimal(perUsd));
    }
}
