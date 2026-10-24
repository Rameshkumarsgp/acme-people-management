package com.acme.peoplemanagement.dataload.seed;

import com.acme.peoplemanagement.dataload.rules.EmployeeRow;
import com.acme.peoplemanagement.dataload.seed.PayModel.CountryPay;
import com.acme.peoplemanagement.dataload.seed.PayModel.TitlePay;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Builds the same employees every time for the same random seed and the same day. */
public final class SeedGenerator {

    private static final int HIRE_WINDOW_DAYS = 10 * 365;
    private static final int TOTAL_WEIGHT_PERCENT = 100;
    private static final int LEAVER_PERCENT = 8;

    private static final List<String> FIRST_NAMES = List.of(
            "Asha", "Ravi", "Priya", "Arjun", "Meera", "Kiran", "Sanjay", "Anita", "John", "Emma",
            "Liam", "Olivia", "Noah", "Sophia", "Lucas", "Mia", "Hiroshi", "Yuki", "Fatima", "Omar");
    private static final List<String> LAST_NAMES = List.of(
            "Rao", "Sharma", "Patel", "Singh", "Nair", "Smith", "Jones", "Brown", "Taylor", "Wilson",
            "Muller", "Schmidt", "Dubois", "Martin", "Tan", "Lim", "Nguyen", "Sato", "Khan", "Haddad");

    private SeedGenerator() {
    }

    public static List<EmployeeRow> generate(final int count, final long randomSeed, final LocalDate today) {
        final Random random = new Random(randomSeed);
        final List<EmployeeRow> rows = new ArrayList<>(count);
        for (int number = 1; number <= count; number++) {
            rows.add(employee(number, random, today));
        }
        return rows;
    }

    private static EmployeeRow employee(final int number, final Random random, final LocalDate today) {
        final String first = pick(FIRST_NAMES, random);
        final String last = pick(LAST_NAMES, random);
        final TitlePay title = pick(PayModel.titles(), random);
        final CountryPay country = pickCountry(random);
        final LocalDate hireDate = today.minusDays(random.nextInt(HIRE_WINDOW_DAYS));
        final int noisePercent = random.nextInt(2 * PayModel.MAX_NOISE_PERCENT + 1) - PayModel.MAX_NOISE_PERCENT;
        final LocalDate terminationDate = terminationDate(hireDate, today, random);
        final LocalDate end = terminationDate == null ? today : terminationDate;
        final int tenureYears = (int) ChronoUnit.YEARS.between(hireDate, end);
        final long salary = PayModel.majorSalary(title, country, tenureYears, noisePercent);
        return new EmployeeRow(first + " " + last, email(first, last, number), hireDate, title.department(),
                title.title(), country.code(), country.currency(), Long.toString(salary),
                hireDate.plusYears(tenureYears), terminationDate);
    }

    /** About one in twelve employees has left: on or after the hire date and before today. Both draws always happen. */
    private static LocalDate terminationDate(final LocalDate hireDate, final LocalDate today, final Random random) {
        final boolean leaver = random.nextInt(TOTAL_WEIGHT_PERCENT) < LEAVER_PERCENT;
        final int daysEmployed = (int) ChronoUnit.DAYS.between(hireDate, today);
        final int offset = random.nextInt(Math.max(daysEmployed, 1));
        return leaver && daysEmployed > 0 ? hireDate.plusDays(offset) : null;
    }

    private static String email(final String first, final String last, final int number) {
        return (first + "." + last + number + "@acme.example").toLowerCase(Locale.ROOT);
    }

    private static <T> T pick(final List<T> options, final Random random) {
        return options.get(random.nextInt(options.size()));
    }

    private static CountryPay pickCountry(final Random random) {
        int remaining = random.nextInt(TOTAL_WEIGHT_PERCENT);
        for (final CountryPay country : PayModel.countries()) {
            remaining -= country.weightPercent();
            if (remaining < 0) {
                return country;
            }
        }
        throw new IllegalStateException("The country weights do not add up to 100.");
    }
}
