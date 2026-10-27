package com.acme.peoplemanagement.salary;

import com.acme.peoplemanagement.salary.rules.ChangeAction;
import com.acme.peoplemanagement.salary.rules.Money;
import com.acme.peoplemanagement.salary.rules.SalaryChange;
import com.acme.peoplemanagement.salary.rules.TimelineEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** One line of the change log. It refers to its employee and entry by id only, and outlives the entry. */
@Entity
@Table(name = "salary_entry_change")
class SalaryEntryChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "employee_id")
    private long employeeId;

    @Column(name = "entry_id")
    private long entryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action")
    private ChangeAction action;

    @Column(name = "old_effective_date")
    private LocalDate oldEffectiveDate;

    @Column(name = "old_amount_minor")
    private Long oldAmountMinor;

    @Column(name = "old_currency_code")
    private String oldCurrencyCode;

    @Column(name = "new_effective_date")
    private LocalDate newEffectiveDate;

    @Column(name = "new_amount_minor")
    private Long newAmountMinor;

    @Column(name = "new_currency_code")
    private String newCurrencyCode;

    @Column(name = "changed_at")
    private Instant changedAt;

    /** For JPA. */
    protected SalaryEntryChange() {
    }

    /** A null {@code before} is an added entry, and a null {@code after} is a deleted one. */
    SalaryEntryChange(
            final long employeeId,
            final long entryId,
            final TimelineEntry before,
            final TimelineEntry after,
            final Instant changedAt) {
        this.employeeId = employeeId;
        this.entryId = entryId;
        this.action = before == null ? ChangeAction.ADDED : after == null ? ChangeAction.DELETED : ChangeAction.EDITED;
        if (before != null) {
            this.oldEffectiveDate = before.effectiveDate();
            this.oldAmountMinor = before.salary().amountMinor();
            this.oldCurrencyCode = before.salary().currencyCode();
        }
        if (after != null) {
            this.newEffectiveDate = after.effectiveDate();
            this.newAmountMinor = after.salary().amountMinor();
            this.newCurrencyCode = after.salary().currencyCode();
        }
        this.changedAt = changedAt;
    }

    SalaryChange toChange() {
        return new SalaryChange(id, entryId, action,
                oldEffectiveDate == null ? null : new TimelineEntry(oldEffectiveDate,
                        new Money(oldAmountMinor, oldCurrencyCode)),
                newEffectiveDate == null ? null : new TimelineEntry(newEffectiveDate,
                        new Money(newAmountMinor, newCurrencyCode)),
                changedAt);
    }
}
