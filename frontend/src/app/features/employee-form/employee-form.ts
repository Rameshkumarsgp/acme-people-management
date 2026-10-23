import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { FormErrors, toFormErrors } from '../../core/api/to-form-errors';
import { CreateEmployeeRequest, EmployeeApi } from '../../core/employees/employee-api';
import { ReferenceApi, ReferenceLists } from '../../core/reference/reference-api';
import { AmountError, parseMajorToMinor } from '../../shared/money/parse-major-to-minor';
import { ControlName, SERVER_FIELD_TO_CONTROL } from './employee-form-fields';
import {
  describeAmountError,
  salaryAmountValidator,
} from '../../shared/money/salary-amount.validator';

@Component({
  selector: 'app-employee-form',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './employee-form.html',
  styleUrl: './employee-form.scss',
})
export class EmployeeForm implements OnInit {
  private readonly referenceApi = inject(ReferenceApi);
  private readonly employeeApi = inject(EmployeeApi);
  private readonly router = inject(Router);
  private readonly selectedCurrencyCode = signal('');

  protected readonly lists = signal<ReferenceLists | null>(null);
  protected readonly listsFailed = signal(false);
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);
  private readonly minorUnitDigits = computed(
    () =>
      this.lists()?.currencies.find((currency) => currency.code === this.selectedCurrencyCode())
        ?.minorUnitDigits ?? null,
  );
  protected readonly form = inject(NonNullableFormBuilder).group({
    fullName: ['', [Validators.required, Validators.maxLength(200)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    hireDate: ['', Validators.required],
    departmentId: ['', Validators.required],
    jobTitleId: ['', Validators.required],
    countryCode: ['', Validators.required],
    currency: ['', Validators.required],
    salaryAmount: ['', [Validators.required, salaryAmountValidator(() => this.minorUnitDigits())]],
    salaryEffectiveDate: [''],
  });

  constructor() {
    // The decimals an amount may have depend on the currency, so choosing one re-checks the amount.
    this.form.controls.currency.valueChanges.pipe(takeUntilDestroyed()).subscribe((code) => {
      this.selectedCurrencyCode.set(code);
      this.form.controls.salaryAmount.updateValueAndValidity();
    });
  }

  ngOnInit(): void {
    void this.loadLists();
  }

  protected retryLoadingLists(): void {
    void this.loadLists();
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
    if (errors['email']) {
      return 'Enter a valid email address.';
    }
    if (errors['amount']) {
      return describeAmountError(errors['amount'] as AmountError, this.minorUnitDigits());
    }
    return 'This value is not valid.';
  }

  protected async createEmployee(): Promise<void> {
    // The button is disabled while saving, but a second click can arrive before the page redraws.
    if (this.submitting()) {
      return;
    }
    const request = this.form.valid ? this.toRequest() : null;
    if (request === null) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.formError.set(null);
    try {
      const created = await this.employeeApi.create(request);
      await this.router.navigateByUrl(`/employees/${created.id}`);
    } catch (error) {
      if (!(error instanceof HttpErrorResponse)) {
        throw error;
      }
      this.showServerErrors(toFormErrors(error));
    } finally {
      this.submitting.set(false);
    }
  }

  private toRequest(): CreateEmployeeRequest | null {
    const value = this.form.getRawValue();
    const digits = this.minorUnitDigits();
    const amount = digits === null ? null : parseMajorToMinor(value.salaryAmount, digits);
    if (amount === null || !amount.ok) {
      return null;
    }
    return {
      fullName: value.fullName,
      email: value.email,
      hireDate: value.hireDate,
      departmentId: Number(value.departmentId),
      jobTitleId: Number(value.jobTitleId),
      countryCode: value.countryCode,
      initialSalary: {
        effectiveDate: value.salaryEffectiveDate || undefined,
        amountMinor: amount.amountMinor,
        currency: value.currency,
      },
    };
  }

  /** A field with no control on this form has nowhere to show its message, so it goes in the banner. */
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

  private async loadLists(): Promise<void> {
    this.listsFailed.set(false);
    try {
      this.lists.set(await this.referenceApi.loadLists());
    } catch (error) {
      if (!(error instanceof HttpErrorResponse)) {
        throw error;
      }
      this.listsFailed.set(true);
    }
  }
}
