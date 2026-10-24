import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { isVersionConflict } from '../../core/api/is-version-conflict';
import { FormErrors, toFormErrors } from '../../core/api/to-form-errors';
import { Employee, EmployeeApi, UpdateEmployeeRequest } from '../../core/employees/employee-api';
import { ReferenceApi, ReferenceLists } from '../../core/reference/reference-api';

type LoadState = 'loading' | 'loaded' | 'not-found' | 'failed';
type ControlName =
  | 'fullName'
  | 'email'
  | 'hireDate'
  | 'terminationDate'
  | 'departmentId'
  | 'jobTitleId'
  | 'countryCode';

const NOT_FOUND = 404;

/** The API names a field by its request path. This maps it to the form control that shows its error. */
const SERVER_FIELD_TO_CONTROL: Readonly<Record<string, ControlName | undefined>> = {
  fullName: 'fullName',
  email: 'email',
  hireDate: 'hireDate',
  terminationDate: 'terminationDate',
  departmentId: 'departmentId',
  jobTitleId: 'jobTitleId',
  countryCode: 'countryCode',
};

@Component({
  selector: 'app-employee-edit',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './employee-edit.html',
  styleUrl: './employee-edit.scss',
})
export class EmployeeEdit implements OnInit {
  private readonly employeeApi = inject(EmployeeApi);
  private readonly referenceApi = inject(ReferenceApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly id = Number(this.route.snapshot.paramMap.get('id'));

  protected readonly lists = signal<ReferenceLists | null>(null);
  protected readonly employee = signal<Employee | null>(null);
  protected readonly loadState = signal<LoadState>('loading');
  protected readonly submitting = signal(false);
  protected readonly conflict = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group({
    fullName: ['', [Validators.required, Validators.maxLength(200)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    hireDate: ['', Validators.required],
    terminationDate: [''],
    departmentId: ['', Validators.required],
    jobTitleId: ['', Validators.required],
    countryCode: ['', Validators.required],
  });

  ngOnInit(): void {
    void this.load();
  }

  protected retry(): void {
    void this.load();
  }

  /** Reads the latest values and drops what was typed, after someone else changed the employee. */
  protected async reload(): Promise<void> {
    try {
      this.show(await this.employeeApi.get(this.id));
    } catch (error) {
      this.failedToLoad(error);
    }
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
    return 'This value is not valid.';
  }

  protected async saveEmployee(): Promise<void> {
    // The button is disabled while saving, but a second click can arrive before the page redraws.
    const employee = this.employee();
    if (this.submitting() || employee === null) {
      return;
    }
    if (!this.form.valid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.formError.set(null);
    this.conflict.set(false);
    try {
      await this.employeeApi.update(employee.id, this.toRequest(employee));
      await this.router.navigateByUrl(`/employees/${employee.id}`);
    } catch (error) {
      if (!(error instanceof HttpErrorResponse)) {
        throw error;
      }
      if (isVersionConflict(error)) {
        this.conflict.set(true);
      } else {
        this.showServerErrors(toFormErrors(error));
      }
    } finally {
      this.submitting.set(false);
    }
  }

  private toRequest(employee: Employee): UpdateEmployeeRequest {
    const value = this.form.getRawValue();
    return {
      fullName: value.fullName,
      email: value.email,
      hireDate: value.hireDate,
      terminationDate: value.terminationDate === '' ? null : value.terminationDate,
      departmentId: Number(value.departmentId),
      jobTitleId: Number(value.jobTitleId),
      countryCode: value.countryCode,
      version: employee.version,
    };
  }

  private show(employee: Employee): void {
    this.form.reset({
      fullName: employee.fullName,
      email: employee.email,
      hireDate: employee.hireDate,
      terminationDate: employee.terminationDate ?? '',
      departmentId: String(employee.department.id),
      jobTitleId: String(employee.jobTitle.id),
      countryCode: employee.country.code,
    });
    this.employee.set(employee);
    this.conflict.set(false);
    this.formError.set(null);
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

  private async load(): Promise<void> {
    this.loadState.set('loading');
    try {
      const [employee, lists] = await Promise.all([
        this.employeeApi.get(this.id),
        this.referenceApi.loadLists(),
      ]);
      this.lists.set(lists);
      this.show(employee);
      this.loadState.set('loaded');
    } catch (error) {
      this.failedToLoad(error);
    }
  }

  private failedToLoad(error: unknown): void {
    if (!(error instanceof HttpErrorResponse)) {
      throw error;
    }
    this.loadState.set(error.status === NOT_FOUND ? 'not-found' : 'failed');
  }
}
