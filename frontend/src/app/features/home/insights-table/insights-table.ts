import { Component, input } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { InsightGroup } from '../../../core/insights/insights-api';
import { Currency } from '../../../core/reference/reference-api';
import { formatMoney } from '../../../shared/money/format-money';

/** The statistics of each group. A group is one slice value in one currency, so figures are never mixed. */
@Component({
  selector: 'app-insights-table',
  imports: [MatTableModule],
  templateUrl: './insights-table.html',
  styleUrl: './insights-table.scss',
})
export class InsightsTable {
  readonly groups = input.required<InsightGroup[]>();
  readonly currencies = input.required<Currency[]>();

  protected readonly columns = [
    'label',
    'currency',
    'headcount',
    'total',
    'min',
    'median',
    'mean',
    'max',
  ];

  protected money(amount: number | string, group: InsightGroup): string {
    return formatMoney(amount, group.currency, this.currencies());
  }
}
