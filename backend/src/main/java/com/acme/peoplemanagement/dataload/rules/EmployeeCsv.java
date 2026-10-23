package com.acme.peoplemanagement.dataload.rules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;

/** Reads an employee CSV into numbered lines of cells. It checks the header, and each row checks itself. */
public final class EmployeeCsv {

    private static final char BYTE_ORDER_MARK = '\uFEFF';

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
            .get();

    private EmployeeCsv() {
    }

    /** A row's cells and its number in the file, where the header is row 1. */
    public record Line(int rowNumber, Map<Column, String> cells) {

        public Line {
            cells = Map.copyOf(cells);
        }
    }

    public static List<Line> read(final Reader source) {
        try (CSVParser parser = parse(source)) {
            requireKnownHeaders(parser.getHeaderNames());
            return parser.stream()
                    .map(record -> new Line((int) record.getRecordNumber() + 1, cellsOf(record)))
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static CSVParser parse(final Reader source) throws IOException {
        final BufferedReader reader = new BufferedReader(source);
        reader.mark(1);
        if (reader.read() != BYTE_ORDER_MARK) {
            reader.reset();
        }
        try {
            return FORMAT.parse(reader);
        } catch (IllegalArgumentException exception) {
            throw new FileRejectedException("The header repeats a column name.");
        }
    }

    private static void requireKnownHeaders(final List<String> headers) {
        if (headers.isEmpty()) {
            throw new FileRejectedException("The file has no header row.");
        }
        final Set<String> known = new HashSet<>(Column.headers());
        final List<String> unknown = headers.stream().filter(header -> !known.contains(header)).toList();
        if (!unknown.isEmpty()) {
            throw new FileRejectedException("Unknown columns: " + String.join(", ", unknown) + ".");
        }
        final List<String> missing = Arrays.stream(Column.values())
                .filter(Column::required)
                .map(Column::header)
                .filter(header -> !headers.contains(header))
                .toList();
        if (!missing.isEmpty()) {
            throw new FileRejectedException("Missing required columns: " + String.join(", ", missing) + ".");
        }
    }

    private static Map<Column, String> cellsOf(final CSVRecord record) {
        final Map<Column, String> cells = new EnumMap<>(Column.class);
        for (final Column column : Column.values()) {
            cells.put(column, record.isSet(column.header()) ? record.get(column.header()) : "");
        }
        return cells;
    }
}
