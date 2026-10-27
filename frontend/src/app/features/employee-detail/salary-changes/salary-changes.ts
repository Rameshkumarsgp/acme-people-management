import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { SalaryChange, SalaryValues } from '../../../core/employees/employee-api';
import { Currency } from '../../../core/reference/reference-api';
import { formatMinor } from '../../../shared/money/format-minor';

const ACTION_TEXT: Record<SalaryChange['action'], string> = {
  ADDED: 'Added',
  EDITED: 'Edited',
  DELETED: 'Deleted',
};

@Component({
  selector: 'app-salary-changes',
  imports: [DatePipe, MatTableModule],
  templateUrl: './salary-changes.html',
  styleUrl: './salary-changes.scss',
})
export class SalaryChanges {
  readonly changes = input.required<SalaryChange[]>();
  readonly currencies = input.required<Currency[]>();

  protected readonly columns = ['changedAt', 'action', 'change'];

  protected actionText(change: SalaryChange): string {
    return ACTION_TEXT[change.action];
  }

  /** What the change did: the entry that was added or deleted, or an edit's old and new values. */
  protected summary(change: SalaryChange): string {
    const before = change.before ? this.valuesText(change.before) : null;
    const after = change.after ? this.valuesText(change.after) : null;
    return before !== null && after !== null ? `${before} → ${after}` : (after ?? before ?? '');
  }

  private valuesText(values: SalaryValues): string {
    const currency = this.currencies().find((candidate) => candidate.code === values.currency);
    const amount = currency
      ? formatMinor(values.amountMinor, currency.minorUnitDigits)
      : String(values.amountMinor);
    return `${values.effectiveDate} · ${amount} ${values.currency}`;
  }
}
