import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { isVersionConflict } from '../../core/api/is-version-conflict';
import { toFormErrors } from '../../core/api/to-form-errors';
import {
  Employee,
  EmployeeApi,
  SalaryChange,
  SalaryEntry,
} from '../../core/employees/employee-api';
import { Currency, ReferenceApi } from '../../core/reference/reference-api';
import { formatMinor } from '../../shared/money/format-minor';
import {
  SalaryEntryDialog,
  SalaryEntryDialogData,
} from './salary-entry-dialog/salary-entry-dialog';
import {
  ConfirmDeleteDialog,
  ConfirmDeleteDialogData,
} from './confirm-delete-dialog/confirm-delete-dialog';
import { SalaryChanges } from './salary-changes/salary-changes';
import { SalaryHistory } from './salary-history/salary-history';

type LoadState = 'loading' | 'loaded' | 'not-found' | 'failed';

const NOT_FOUND = 404;
const UNPROCESSABLE = 422;

const DELETE_EMPLOYEE_MESSAGE =
  'This removes the employee and their whole salary history. It is meant for a record entered by mistake, ' +
  'and it cannot be undone. An employee who left should be given a termination date instead.';

@Component({
  selector: 'app-employee-detail',
  imports: [RouterLink, MatButtonModule, MatCardModule, SalaryChanges, SalaryHistory],
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
  protected readonly changes = signal<SalaryChange[]>([]);
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
    await this.openSalaryDialog();
  }

  protected async openEditSalary(entry: SalaryEntry): Promise<void> {
    await this.openSalaryDialog(entry);
  }

  /** Deletes one salary entry after asking, and then reads everything again. */
  protected async deleteSalaryEntry(entry: SalaryEntry): Promise<void> {
    const dialogRef = this.dialog.open<ConfirmDeleteDialog, ConfirmDeleteDialogData, boolean>(
      ConfirmDeleteDialog,
      {
        data: {
          title: `Delete the salary from ${entry.effectiveDate}?`,
          message:
            'This removes the entry from the salary history. The change history keeps a record of it.',
        },
      },
    );
    if (!(await firstValueFrom(dialogRef.afterClosed()))) {
      return;
    }
    this.actionError.set(null);
    try {
      await this.employeeApi.deleteSalaryEntry(this.employee()?.id ?? 0, entry.id);
      await this.load();
    } catch (error) {
      await this.showEntryDeleteFailure(error);
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
      { data: { title: `Delete ${employee.fullName}?`, message: DELETE_EMPLOYEE_MESSAGE } },
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

  private async openSalaryDialog(entry?: SalaryEntry): Promise<void> {
    const employee = this.employee();
    if (employee === null) {
      return;
    }
    const dialogRef = this.dialog.open<SalaryEntryDialog, SalaryEntryDialogData, boolean>(
      SalaryEntryDialog,
      {
        data: {
          employeeId: employee.id,
          currencies: this.currencies(),
          defaultCurrency: employee.currentSalary.currency,
          entry,
        },
      },
    );
    if (await firstValueFrom(dialogRef.afterClosed())) {
      await this.load();
    }
  }

  /** An entry that is already gone is not a failure, and a broken rule is shown with the server's reason. */
  private async showEntryDeleteFailure(error: unknown): Promise<void> {
    if (!(error instanceof HttpErrorResponse)) {
      throw error;
    }
    if (error.status === NOT_FOUND) {
      await this.load();
    } else if (error.status === UNPROCESSABLE) {
      this.actionError.set(toFormErrors(error).formError);
    } else {
      this.actionError.set(
        'Could not delete the salary entry. Check your connection and try again.',
      );
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
      const [employee, currencies, entries, changes] = await Promise.all([
        this.employeeApi.get(id),
        this.referenceApi.loadCurrencies(),
        this.employeeApi.listSalaryEntries(id),
        this.employeeApi.listSalaryChanges(id),
      ]);
      this.currencies.set(currencies);
      this.entries.set(entries);
      this.changes.set(changes);
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
