import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { SalaryEntry } from '../../../core/employees/employee-api';
import { Currency } from '../../../core/reference/reference-api';
import { formatMinor } from '../../../shared/money/format-minor';

@Component({
  selector: 'app-salary-history',
  imports: [MatButtonModule, MatTableModule],
  templateUrl: './salary-history.html',
  styleUrl: './salary-history.scss',
})
export class SalaryHistory {
  readonly entries = input.required<SalaryEntry[]>();
  readonly currencies = input.required<Currency[]>();
  readonly edit = output<SalaryEntry>();
  readonly remove = output<SalaryEntry>();

  protected readonly columns = ['effectiveDate', 'endDate', 'amount', 'marker', 'actions'];

  protected amountText(entry: SalaryEntry): string {
    const currency = this.currencies().find((candidate) => candidate.code === entry.currency);
    return currency
      ? `${formatMinor(entry.amountMinor, currency.minorUnitDigits)} ${currency.code}`
      : `${entry.amountMinor} ${entry.currency}`;
  }

  protected markerText(entry: SalaryEntry): string {
    if (entry.scheduled) {
      return 'Scheduled';
    }
    return entry.current ? 'Current' : '';
  }
}
