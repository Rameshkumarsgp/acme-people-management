package com.acme.peoplemanagement.salary;

import com.acme.peoplemanagement.salary.rules.Money;
import com.acme.peoplemanagement.salary.rules.TimelineEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * One salary on an employee's timeline. It refers to its employee by id only, so this package never
 * depends on the employee feature.
 */
@Entity
@Table(name = "salary_entry")
class SalaryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "employee_id")
    private long employeeId;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "amount_minor")
    private long amountMinor;

    @Column(name = "currency_code")
    private String currencyCode;

    /** For JPA. */
    protected SalaryEntry() {
    }

    SalaryEntry(final long employeeId, final TimelineEntry entry) {
        this.employeeId = employeeId;
        this.effectiveDate = entry.effectiveDate();
        this.amountMinor = entry.salary().amountMinor();
        this.currencyCode = entry.salary().currencyCode();
    }

    TimelineEntry toTimelineEntry() {
        return new TimelineEntry(effectiveDate, new Money(amountMinor, currencyCode));
    }
}
