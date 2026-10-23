package com.acme.peoplemanagement.dataload.seed;

import com.acme.peoplemanagement.dataload.rules.Column;
import com.acme.peoplemanagement.dataload.rules.EmployeeRow;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/** Writes rows in the loader's format. The column order comes from {@link Column}, so the two cannot drift. */
public final class SeedCsvWriter {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader(Column.headers().toArray(String[]::new))
            .get();

    private SeedCsvWriter() {
    }

    public static void write(final List<EmployeeRow> rows, final Appendable out) throws IOException {
        try (CSVPrinter printer = FORMAT.print(out)) {
            for (final EmployeeRow row : rows) {
                final Map<Column, String> cells = row.cells();
                printer.printRecord(Arrays.stream(Column.values()).map(cells::get).toList());
            }
        }
    }
}
