import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { EmployeeForm } from './employee-form';

const LISTS = {
  countries: [{ code: 'IN', name: 'India' }],
  departments: [{ id: 3, name: 'Engineering' }],
  jobTitles: [{ id: 12, name: 'Software Engineer' }],
  currencies: [
    { code: 'INR', minorUnitDigits: 2 },
    { code: 'JPY', minorUnitDigits: 0 },
  ],
};

describe('EmployeeForm', () => {
  let fixture: ComponentFixture<EmployeeForm>;
  let page: HTMLElement;
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmployeeForm],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(EmployeeForm);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  /** Redraws until the check passes, because the page updates after the answers resolve. */
  async function until(check: () => void): Promise<void> {
    await vi.waitFor(() => {
      fixture.detectChanges();
      check();
    });
  }

  async function loadLists(): Promise<void> {
    http.expectOne('/api/reference/countries').flush(LISTS.countries);
    http.expectOne('/api/reference/departments').flush(LISTS.departments);
    http.expectOne('/api/reference/job-titles').flush(LISTS.jobTitles);
    http.expectOne('/api/reference/currencies').flush(LISTS.currencies);
    await until(() =>
      expect(page.querySelector('select[formControlName="currency"]')).not.toBeNull(),
    );
  }

  function type(name: string, value: string): void {
    const field = page.querySelector<HTMLInputElement | HTMLSelectElement>(
      `[formControlName="${name}"]`,
    )!;
    field.value = value;
    field.dispatchEvent(new Event(field instanceof HTMLSelectElement ? 'change' : 'input'));
  }

  function fillValidForm(): void {
    type('fullName', 'Asha Rao');
    type('email', 'asha.rao@acme.example');
    type('hireDate', '2024-04-01');
    type('departmentId', '3');
    type('jobTitleId', '12');
    type('countryCode', 'IN');
    type('currency', 'INR');
    type('salaryAmount', '1,250,000.50');
  }

  function submit(): void {
    page.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
  }

  function errorText(): string {
    return Array.from(page.querySelectorAll('mat-error'))
      .map((element) => element.textContent?.trim())
      .join(' | ');
  }

  /** The error shown inside the form field of one control, so a message under the wrong field fails. */
  function errorOf(name: string): string {
    const field = page.querySelector(`[formControlName="${name}"]`)!;
    return field.closest('mat-form-field')?.querySelector('mat-error')?.textContent?.trim() ?? '';
  }

  function alertText(): string {
    return page.querySelector('[role="alert"]')?.textContent ?? '';
  }

  const EMAIL_TAKEN = {
    code: 'EMAIL_ALREADY_EXISTS',
    detail: 'An employee with this email already exists.',
  };

  it('says it is loading the lists, then shows the form', async () => {
    expect(page.textContent).toContain('Loading');

    await loadLists();

    expect(page.querySelector('input[formControlName="fullName"]')).not.toBeNull();
    expect(page.querySelector('select[formControlName="departmentId"]')?.textContent).toContain(
      'Engineering',
    );
  });

  it('offers to try again when the lists cannot be loaded', async () => {
    // The other requests are answered first: a failing request cancels the ones still waiting.
    http.expectOne('/api/reference/countries').flush([]);
    http.expectOne('/api/reference/departments').flush([]);
    http.expectOne('/api/reference/job-titles').flush([]);
    http.expectOne('/api/reference/currencies').error(new ProgressEvent('error'));

    await until(() => expect(alertText()).toContain('Could not load'));
    expect(page.querySelector('button')?.textContent).toContain('Try again');
  });

  it('does not call the server while required fields are empty and says what is missing', async () => {
    await loadLists();

    submit();
    fixture.detectChanges();

    http.expectNone('/api/employees');
    expect(errorText()).toContain('This field is required.');
  });

  it('sends the salary as exact minor units and opens the new employee', async () => {
    await loadLists();
    fillValidForm();

    submit();
    const call = http.expectOne('/api/employees');
    expect(call.request.body).toEqual({
      fullName: 'Asha Rao',
      email: 'asha.rao@acme.example',
      hireDate: '2024-04-01',
      departmentId: 3,
      jobTitleId: 12,
      countryCode: 'IN',
      initialSalary: { amountMinor: 125000050, currency: 'INR' },
    });
    call.flush({ id: 10432 });

    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/employees/10432'));
  });

  it('sends one request when the button is clicked twice', async () => {
    await loadLists();
    fillValidForm();

    submit();
    submit();

    http.expectOne('/api/employees').flush({ id: 10432 });
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledTimes(1));
  });

  it('rejects decimals for a currency without minor units, even if the amount was typed first', async () => {
    await loadLists();
    fillValidForm();
    type('salaryAmount', '10.50');
    type('currency', 'JPY');
    fixture.detectChanges();

    submit();
    fixture.detectChanges();

    http.expectNone('/api/employees');
    expect(errorOf('salaryAmount')).toBe('This currency has no decimal places.');
  });

  it('shows a duplicate email under the email field and keeps what was typed', async () => {
    await loadLists();
    fillValidForm();

    submit();
    http.expectOne('/api/employees').flush(EMAIL_TAKEN, { status: 409, statusText: 'Conflict' });

    await until(() => expect(errorOf('email')).toBe('An employee with this email already exists.'));
    expect(page.querySelector<HTMLInputElement>('input[formControlName="fullName"]')!.value).toBe(
      'Asha Rao',
    );
    expect(navigate).not.toHaveBeenCalled();
  });

  it('clears a server error on a field as soon as it is edited', async () => {
    await loadLists();
    fillValidForm();
    submit();
    http.expectOne('/api/employees').flush(EMAIL_TAKEN, { status: 409, statusText: 'Conflict' });
    await until(() => expect(errorOf('email')).toContain('already exists'));

    type('email', 'asha.other@acme.example');
    fixture.detectChanges();

    expect(errorOf('email')).toBe('');
  });

  it('shows the server wording for a broken salary rule in a banner', async () => {
    await loadLists();
    fillValidForm();

    submit();
    http
      .expectOne('/api/employees')
      .flush(
        { code: 'NO_CURRENT_SALARY', detail: 'The hire date cannot be in the future.' },
        { status: 422, statusText: 'Unprocessable Content' },
      );

    await until(() => expect(alertText()).toContain('The hire date cannot be in the future.'));
  });

  it('puts a server error about the amount under the amount field', async () => {
    await loadLists();
    fillValidForm();

    submit();
    http.expectOne('/api/employees').flush(
      {
        errors: [
          { field: 'initialSalary.amountMinor', message: 'must be greater than or equal to 1' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );

    await until(() => expect(errorOf('salaryAmount')).toBe('must be greater than or equal to 1'));
  });

  it('enables the button again after a failed save', async () => {
    await loadLists();
    fillValidForm();
    const button = page.querySelector<HTMLButtonElement>('button[type="submit"]')!;

    submit();
    fixture.detectChanges();
    expect(button.disabled).toBe(true);
    http.expectOne('/api/employees').error(new ProgressEvent('error'));

    await until(() => {
      expect(button.disabled).toBe(false);
      expect(alertText()).toContain('Could not save');
    });
  });
});
