package com.acme.peoplemanagement.dataload.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.peoplemanagement.dataload.rules.Column;
import com.acme.peoplemanagement.dataload.rules.EmployeeCsv;
import com.acme.peoplemanagement.dataload.rules.EmployeeRow;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeedCsvWriterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    @Test
    void writesTheHeaderInTheLoadersColumnOrder() throws IOException {
        final String csv = write(SeedGenerator.generate(3, 42L, TODAY));

        assertThat(csv.lines().findFirst().orElseThrow()).isEqualTo(String.join(",", Column.headers()));
    }

    @Test
    void writesAFileTheLoadersReaderReadsBackAsTheSameEmployees() throws IOException {
        final List<EmployeeRow> rows = SeedGenerator.generate(200, 42L, TODAY);

        final List<EmployeeCsv.Line> lines = EmployeeCsv.read(new StringReader(write(rows)));

        final List<EmployeeRow> readBack = new ArrayList<>();
        lines.forEach(line -> readBack.add(EmployeeRow.parse(line.cells())));
        assertThat(readBack).isEqualTo(rows);
        assertThat(lines.getFirst().rowNumber()).isEqualTo(2);
        assertThat(lines.getLast().rowNumber()).isEqualTo(201);
    }

    @Test
    void quotesANameThatHoldsAComma() throws IOException {
        final EmployeeRow row = new EmployeeRow("Rao, Asha", "asha@acme.example", LocalDate.of(2024, 4, 1),
                "Engineering", "Software Engineer", "IN", "INR", "1250000", null, null);

        final List<EmployeeCsv.Line> lines = EmployeeCsv.read(new StringReader(write(List.of(row))));

        assertThat(EmployeeRow.parse(lines.getFirst().cells())).isEqualTo(row);
    }

    private static String write(final List<EmployeeRow> rows) throws IOException {
        final StringWriter writer = new StringWriter();
        SeedCsvWriter.write(rows, writer);
        return writer.toString();
    }
}
