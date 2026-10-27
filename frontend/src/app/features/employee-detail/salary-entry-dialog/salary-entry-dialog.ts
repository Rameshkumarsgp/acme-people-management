import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { FormErrors, toFormErrors } from '../../../core/api/to-form-errors';
import { EmployeeApi, SalaryEntry } from '../../../core/employees/employee-api';
import { Currency } from '../../../core/reference/reference-api';
import { formatMinor } from '../../../shared/money/format-minor';
import { AmountError, parseMajorToMinor } from '../../../shared/money/parse-major-to-minor';
import {
  describeAmountError,
  salaryAmountValidator,
} from '../../../shared/money/salary-amount.validator';

export interface SalaryEntryDialogData {
  employeeId: number;
  currencies: Currency[];
  defaultCurrency: string;
  /** The entry to change. Without it, the dialog adds a new entry. */
  entry?: SalaryEntry;
}

type ControlName = 'effectiveDate' | 'amount' | 'currency';

/** The API names a field by its request path. This maps it to the control that shows its error. */
const SERVER_FIELD_TO_CONTROL: Readonly<Record<string, ControlName | undefined>> = {
  effectiveDate: 'effectiveDate',
  amountMinor: 'amount',
  currency: 'currency',
};

@Component({
  selector: 'app-salary-entry-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './salary-entry-dialog.html',
  styleUrl: './salary-entry-dialog.scss',
})
export class SalaryEntryDialog {
  private readonly employeeApi = inject(EmployeeApi);
  private readonly dialogRef = inject(MatDialogRef<SalaryEntryDialog, boolean>);
  private readonly data = inject<SalaryEntryDialogData>(MAT_DIALOG_DATA);
  private readonly selectedCurrencyCode = signal(
    this.data.entry?.currency ?? this.data.defaultCurrency,
  );

  protected readonly currencies = this.data.currencies;
  protected readonly editing = this.data.entry !== undefined;
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);
  private readonly minorUnitDigits = computed(
    () =>
      this.data.currencies.find((currency) => currency.code === this.selectedCurrencyCode())
        ?.minorUnitDigits ?? null,
  );
  protected readonly form = inject(NonNullableFormBuilder).group({
    effectiveDate: [this.data.entry?.effectiveDate ?? '', Validators.required],
    amount: [
      this.amountTextOf(this.data.entry),
      [Validators.required, salaryAmountValidator(() => this.minorUnitDigits())],
    ],
    currency: [this.selectedCurrencyCode(), Validators.required],
  });

  constructor() {
    // The decimals an amount may have depend on the currency, so choosing one re-checks the amount.
    this.form.controls.currency.valueChanges.pipe(takeUntilDestroyed()).subscribe((code) => {
      this.selectedCurrencyCode.set(code);
      this.form.controls.amount.updateValueAndValidity();
    });
  }

  protected errorMessage(name: ControlName): string | null {
    const errors = this.form.controls[name].errors;
    if (!errors) {
      return null;
    }
    if (errors['server']) {
      return String(errors['server']);
    }
    if (errors['required']) {
      return 'This field is required.';
    }
    if (errors['amount']) {
      return describeAmountError(errors['amount'] as AmountError, this.minorUnitDigits());
    }
    return 'This value is not valid.';
  }

  protected async saveSalary(): Promise<void> {
    // The button is disabled while saving, but a second click can arrive before the page redraws.
    if (this.submitting()) {
      return;
    }
    const value = this.form.getRawValue();
    const digits = this.minorUnitDigits();
    const amount = digits === null ? null : parseMajorToMinor(value.amount, digits);
    if (!this.form.valid || amount === null || !amount.ok) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.formError.set(null);
    try {
      const request = {
        effectiveDate: value.effectiveDate,
        amountMinor: amount.amountMinor,
        currency: value.currency,
      };
      await (this.data.entry
        ? this.employeeApi.updateSalaryEntry(this.data.employeeId, this.data.entry.id, request)
        : this.employeeApi.addSalaryEntry(this.data.employeeId, request));
      this.dialogRef.close(true);
    } catch (error) {
      if (!(error instanceof HttpErrorResponse)) {
        throw error;
      }
      this.showServerErrors(toFormErrors(error));
    } finally {
      this.submitting.set(false);
    }
  }

  /** The amount as HR would type it, in the entry's own currency, or empty when adding. */
  private amountTextOf(entry: SalaryEntry | undefined): string {
    const currency = this.data.currencies.find((candidate) => candidate.code === entry?.currency);
    return entry && currency ? formatMinor(entry.amountMinor, currency.minorUnitDigits) : '';
  }

  /** A field with no control here has nowhere to show its message, so it goes in the banner. */
  private showServerErrors(errors: FormErrors): void {
    const unplaced: string[] = errors.formError ? [errors.formError] : [];
    for (const [field, message] of Object.entries(errors.fieldErrors)) {
      const controlName = SERVER_FIELD_TO_CONTROL[field];
      if (controlName === undefined) {
        unplaced.push(message);
        continue;
      }
      this.form.controls[controlName].setErrors({ server: message });
      this.form.controls[controlName].markAsTouched();
    }
    this.formError.set(unplaced.length > 0 ? unplaced.join(' ') : null);
  }
}
