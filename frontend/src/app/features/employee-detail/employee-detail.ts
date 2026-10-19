import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Employee, EmployeeApi } from '../../core/employees/employee-api';
import { Currency, ReferenceApi } from '../../core/reference/reference-api';
import { formatMinor } from '../../shared/money/format-minor';

type LoadState = 'loading' | 'loaded' | 'not-found' | 'failed';

const NOT_FOUND = 404;

@Component({
  selector: 'app-employee-detail',
  imports: [RouterLink, MatButtonModule, MatCardModule],
  templateUrl: './employee-detail.html',
  styleUrl: './employee-detail.scss',
})
export class EmployeeDetail implements OnInit {
  private readonly employeeApi = inject(EmployeeApi);
  private readonly referenceApi = inject(ReferenceApi);
  private readonly route = inject(ActivatedRoute);
  private readonly currencies = signal<Currency[]>([]);

  protected readonly loadState = signal<LoadState>('loading');
  protected readonly employee = signal<Employee | null>(null);
  protected readonly salaryText = computed(() => {
    const salary = this.employee()?.currentSalary;
    const currency = this.currencies().find((candidate) => candidate.code === salary?.currency);
    return salary && currency
      ? `${formatMinor(salary.amountMinor, currency.minorUnitDigits)} ${currency.code}`
      : null;
  });

  ngOnInit(): void {
    void this.load();
  }

  protected retry(): void {
    void this.load();
  }

  private async load(): Promise<void> {
    this.loadState.set('loading');
    const id = Number(this.route.snapshot.paramMap.get('id'));
    try {
      const [employee, currencies] = await Promise.all([
        this.employeeApi.get(id),
        this.referenceApi.loadCurrencies(),
      ]);
      this.currencies.set(currencies);
      this.employee.set(employee);
      this.loadState.set('loaded');
    } catch (error) {
      if (!(error instanceof HttpErrorResponse)) {
        throw error;
      }
      this.loadState.set(error.status === NOT_FOUND ? 'not-found' : 'failed');
    }
  }
}
