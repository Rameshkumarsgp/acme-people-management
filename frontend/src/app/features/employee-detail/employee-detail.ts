import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { isVersionConflict } from '../../core/api/is-version-conflict';
import { Employee, EmployeeApi, SalaryEntry } from '../../core/employees/employee-api';
import { Currency, ReferenceApi } from '../../core/reference/reference-api';
import { formatMinor } from '../../shared/money/format-minor';
import { AddSalaryDialog, AddSalaryDialogData } from './add-salary-dialog/add-salary-dialog';
import {
  ConfirmDeleteDialog,
  ConfirmDeleteDialogData,
} from './confirm-delete-dialog/confirm-delete-dialog';
import { SalaryHistory } from './salary-history/salary-history';

type LoadState = 'loading' | 'loaded' | 'not-found' | 'failed';

const NOT_FOUND = 404;

@Component({
  selector: 'app-employee-detail',
  imports: [RouterLink, MatButtonModule, MatCardModule, SalaryHistory],
  templateUrl: './employee-detail.html',
  styleUrl: './employee-detail.scss',
})
export class EmployeeDetail implements OnInit {
  private readonly employeeApi = inject(EmployeeApi);
  private readonly referenceApi = inject(ReferenceApi);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);

  protected readonly currencies = signal<Currency[]>([]);
  protected readonly entries = signal<SalaryEntry[]>([]);
  protected readonly loadState = signal<LoadState>('loading');
  protected readonly employee = signal<Employee | null>(null);
  protected readonly actionError = signal<string | null>(null);
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

  protected async openAddSalary(): Promise<void> {
    const employee = this.employee();
    if (employee === null) {
      return;
    }
    const dialogRef = this.dialog.open<AddSalaryDialog, AddSalaryDialogData, boolean>(
      AddSalaryDialog,
      {
        data: {
          employeeId: employee.id,
          currencies: this.currencies(),
          defaultCurrency: employee.currentSalary.currency,
        },
      },
    );
    if (await firstValueFrom(dialogRef.afterClosed())) {
      await this.load();
    }
  }

  /** Asks first, then deletes with the version this page saw, so a stale page cannot delete by mistake. */
  protected async deleteEmployee(): Promise<void> {
    const employee = this.employee();
    if (employee === null) {
      return;
    }
    const dialogRef = this.dialog.open<ConfirmDeleteDialog, ConfirmDeleteDialogData, boolean>(
      ConfirmDeleteDialog,
      { data: { fullName: employee.fullName } },
    );
    if (!(await firstValueFrom(dialogRef.afterClosed()))) {
      return;
    }
    this.actionError.set(null);
    try {
      await this.employeeApi.delete(employee.id, employee.version);
      await this.router.navigateByUrl('/employees');
    } catch (error) {
      await this.showDeleteFailure(error);
    }
  }

  private async showDeleteFailure(error: unknown): Promise<void> {
    if (!(error instanceof HttpErrorResponse)) {
      throw error;
    }
    if (error.status === NOT_FOUND) {
      this.loadState.set('not-found');
    } else if (isVersionConflict(error)) {
      await this.load();
      this.actionError.set(
        'This employee was changed by someone else since you opened it. Their latest details are shown, so check them and try again.',
      );
    } else {
      this.actionError.set('Could not delete the employee. Check your connection and try again.');
    }
  }

  private async load(): Promise<void> {
    this.loadState.set('loading');
    const id = Number(this.route.snapshot.paramMap.get('id'));
    try {
      const [employee, currencies, entries] = await Promise.all([
        this.employeeApi.get(id),
        this.referenceApi.loadCurrencies(),
        this.employeeApi.listSalaryEntries(id),
      ]);
      this.currencies.set(currencies);
      this.entries.set(entries);
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
