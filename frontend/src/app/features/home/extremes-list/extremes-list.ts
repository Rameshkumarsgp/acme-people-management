import { Component, input } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { ExtremeEmployee } from '../../../core/insights/insights-api';
import { Currency } from '../../../core/reference/reference-api';
import { formatMoney } from '../../../shared/money/format-money';

/** The highest or lowest paid employees of one currency, in rank order. */
@Component({
  selector: 'app-extremes-list',
  imports: [MatTableModule, RouterLink],
  templateUrl: './extremes-list.html',
  styleUrl: './extremes-list.scss',
})
export class ExtremesList {
  readonly employees = input.required<ExtremeEmployee[]>();
  readonly currency = input.required<string>();
  readonly currencies = input.required<Currency[]>();

  protected readonly columns = ['rank', 'fullName', 'department', 'jobTitle', 'country', 'salary'];

  protected money(employee: ExtremeEmployee): string {
    return formatMoney(employee.amountMinor, this.currency(), this.currencies());
  }
}
