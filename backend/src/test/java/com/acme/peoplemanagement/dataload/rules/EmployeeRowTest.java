package com.acme.peoplemanagement.dataload.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmployeeRowTest {

    private static final EmployeeRow ROW = new EmployeeRow("Asha Rao", "asha@acme.example", LocalDate.of(2024, 4, 1),
            "Engineering", "Software Engineer", "IN", "INR", "1250000.00", LocalDate.of(2025, 4, 1), null);

    @Test
    void parsesAFullRow() {
        assertThat(EmployeeRow.parse(ROW.cells())).isEqualTo(ROW);
    }

    @Test
    void leavesTheSalaryDateEmptyWhenTheCellIsEmpty() {
        final Map<Column, String> cells = ROW.cells();
        cells.put(Column.SALARY_EFFECTIVE_DATE, "");

        assertThat(EmployeeRow.parse(cells).salaryEffectiveDate()).isNull();
    }

    @Test
    void writesAnEmptySalaryDateAsAnEmptyCell() {
        final EmployeeRow withoutDate = new EmployeeRow("Asha Rao", "asha@acme.example", LocalDate.of(2024, 4, 1),
                "Engineering", "Software Engineer", "IN", "INR", "1250000.00", null, null);

        assertThat(withoutDate.cells()).containsEntry(Column.SALARY_EFFECTIVE_DATE, "");
        assertThat(withoutDate.cells()).containsEntry(Column.TERMINATION_DATE, "");
    }

    @Test
    void rejectsAMissingRequiredValueAgainstItsColumn() {
        final Map<Column, String> cells = ROW.cells();
        cells.put(Column.DEPARTMENT, "");

        assertRejected(cells, "department", "A value is required.");
    }

    @Test
    void rejectsADateThatIsNotInIsoFormat() {
        final Map<Column, String> cells = ROW.cells();
        cells.put(Column.HIRE_DATE, "01/04/2024");

        assertRejected(cells, "hireDate", "Dates must look like 2024-04-01 (year-month-day).");
    }

    @Test
    void rejectsADateThatDoesNotExist() {
        final Map<Column, String> cells = ROW.cells();
        cells.put(Column.SALARY_EFFECTIVE_DATE, "2024-02-30");

        assertRejected(cells, "salaryEffectiveDate", "Dates must look like 2024-04-01 (year-month-day).");
    }

    @Test
    void parsesATerminationDate() {
        final Map<Column, String> cells = ROW.cells();
        cells.put(Column.TERMINATION_DATE, "2025-12-31");

        assertThat(EmployeeRow.parse(cells).terminationDate()).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    void leavesTheTerminationDateEmptyWhenTheCellIsEmpty() {
        assertThat(EmployeeRow.parse(ROW.cells()).terminationDate()).isNull();
    }

    @Test
    void rejectsATerminationDateThatIsNotInIsoFormat() {
        final Map<Column, String> cells = ROW.cells();
        cells.put(Column.TERMINATION_DATE, "31/12/2025");

        assertRejected(cells, "terminationDate", "Dates must look like 2024-04-01 (year-month-day).");
    }

    @Test
    void writesATerminationDateAsACellAndReadsItBack() {
        final EmployeeRow leaver = new EmployeeRow("Leo Leaver", "leo@acme.example", LocalDate.of(2024, 4, 1),
                "Engineering", "Software Engineer", "IN", "INR", "1250000.00", LocalDate.of(2024, 4, 1),
                LocalDate.of(2025, 12, 31));

        assertThat(leaver.cells()).containsEntry(Column.TERMINATION_DATE, "2025-12-31");
        assertThat(EmployeeRow.parse(leaver.cells())).isEqualTo(leaver);
    }

    @Test
    void treatsAnAbsentCellAsEmpty() {
        final Map<Column, String> cells = new EnumMap<>(ROW.cells());
        cells.remove(Column.SALARY_EFFECTIVE_DATE);
        cells.remove(Column.TERMINATION_DATE);

        assertThat(EmployeeRow.parse(cells).salaryEffectiveDate()).isNull();
    }

    private static void assertRejected(final Map<Column, String> cells, final String field, final String reason) {
        assertThatExceptionOfType(RowRejectedException.class)
                .isThrownBy(() -> EmployeeRow.parse(cells))
                .withMessage(reason)
                .satisfies(exception -> assertThat(exception.field()).isEqualTo(field));
    }
}
