package com.acme.peoplemanagement.dataload.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmployeeCsvTest {

    private static final String HEADER = String.join(",", Column.headers());
    private static final String ROW = "Asha Rao,asha@acme.example,2024-04-01,Engineering,Software Engineer,"
            + "IN,INR,1250000.00,,";

    @Test
    void readsCellsByHeaderNameWhateverTheColumnOrder() {
        final List<EmployeeCsv.Line> lines = read("email,fullName,hireDate,department,jobTitle,countryCode,currency,"
                + "salary\nasha@acme.example,Asha Rao,2024-04-01,Engineering,Software Engineer,IN,INR,100.00");

        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst().cells())
                .containsEntry(Column.FULL_NAME, "Asha Rao")
                .containsEntry(Column.EMAIL, "asha@acme.example")
                .containsEntry(Column.SALARY, "100.00");
    }

    @Test
    void numbersTheRowsWithTheHeaderAsRowOne() {
        final List<EmployeeCsv.Line> lines = read(HEADER + "\n" + ROW + "\n" + ROW);

        assertThat(lines).extracting(EmployeeCsv.Line::rowNumber).containsExactly(2, 3);
    }

    @Test
    void keepsTheCellsOfALineFromBeingChanged() {
        final List<EmployeeCsv.Line> lines = read(HEADER + "\n" + ROW);

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> lines.getFirst().cells().put(Column.EMAIL, "other@acme.example"));
    }

    @Test
    void readsAFileSavedByExcel() {
        final String quotedName = "\"Rao, Asha \"\"A\"\"\"";
        final String excel = "\uFEFF" + HEADER + "\r\n" + ROW.replace("Asha Rao", quotedName) + "\r\n";

        final List<EmployeeCsv.Line> lines = read(excel);

        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst().cells()).containsEntry(Column.FULL_NAME, "Rao, Asha \"A\"");
    }

    @Test
    void trimsSpacesAroundCellsAndSkipsBlankLines() {
        final List<EmployeeCsv.Line> lines =
                read(HEADER + "\n\n" + ROW.replace("Engineering", "  Engineering ") + "\n\n");

        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst().cells()).containsEntry(Column.DEPARTMENT, "Engineering");
    }

    @Test
    void treatsAnAbsentOptionalColumnAndAShortRowAsEmptyCells() {
        final List<EmployeeCsv.Line> lines = read("fullName,email,hireDate,department,jobTitle,countryCode,currency,"
                + "salary\nAsha Rao,asha@acme.example,2024-04-01");

        assertThat(lines.getFirst().cells())
                .containsEntry(Column.SALARY_EFFECTIVE_DATE, "")
                .containsEntry(Column.TERMINATION_DATE, "")
                .containsEntry(Column.SALARY, "");
    }

    @Test
    void failsTheFileWhenARequiredColumnIsMissing() {
        assertFileRejected("fullName,email\nA,a@x", "Missing required columns: hireDate, department, jobTitle, "
                + "countryCode, currency, salary.");
    }

    @Test
    void failsTheFileOnAnUnknownColumn() {
        assertFileRejected(HEADER + ",bonus\n" + ROW + ",5", "Unknown columns: bonus.");
    }

    @Test
    void failsTheFileOnARepeatedColumn() {
        assertFileRejected(HEADER + ",email\n" + ROW + ",x", "The header repeats a column name.");
    }

    @Test
    void failsTheFileWhenItIsEmpty() {
        assertFileRejected("", "The file has no header row.");
    }

    private static List<EmployeeCsv.Line> read(final String text) {
        return EmployeeCsv.read(new StringReader(text));
    }

    private static void assertFileRejected(final String text, final String reason) {
        assertThatExceptionOfType(FileRejectedException.class)
                .isThrownBy(() -> read(text))
                .withMessage(reason);
    }
}
