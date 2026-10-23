package com.acme.peoplemanagement.dataload.rules;

import com.acme.peoplemanagement.salary.rules.Money;
import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Converts the major-unit amounts a spreadsheet holds (1250000.00) to minor units and back, without rounding. */
public final class MajorUnits {

    private static final String FIELD = "salary";
    private static final Pattern PLAIN_NUMBER = Pattern.compile("\\d+(\\.\\d+)?");
    private static final BigDecimal MAX_AMOUNT_MINOR = BigDecimal.valueOf(Money.MAX_AMOUNT_MINOR);

    private MajorUnits() {
    }

    public static long toMinor(final String text, final int minorUnitDigits) {
        if (!PLAIN_NUMBER.matcher(text).matches()) {
            throw rejected("Amount must be a plain number such as 1250000.00, with no thousands separators.");
        }
        final BigDecimal amount = new BigDecimal(text);
        if (amount.stripTrailingZeros().scale() > minorUnitDigits) {
            throw rejected(decimalsMessage(minorUnitDigits));
        }
        final BigDecimal minor = amount.movePointRight(minorUnitDigits);
        if (minor.signum() == 0) {
            throw rejected("Amount must be greater than zero.");
        }
        if (minor.compareTo(MAX_AMOUNT_MINOR) > 0) {
            throw rejected("Amount is too large.");
        }
        return minor.longValueExact();
    }

    public static String format(final long amountMinor, final int minorUnitDigits) {
        return BigDecimal.valueOf(amountMinor, minorUnitDigits).toPlainString();
    }

    private static String decimalsMessage(final int minorUnitDigits) {
        return minorUnitDigits == 0
                ? "This currency has no decimal places."
                : "This currency allows at most " + minorUnitDigits + " decimal places.";
    }

    private static RowRejectedException rejected(final String reason) {
        return new RowRejectedException(FIELD, reason);
    }
}
