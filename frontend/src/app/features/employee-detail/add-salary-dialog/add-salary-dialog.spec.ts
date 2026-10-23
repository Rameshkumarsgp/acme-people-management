import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { AddSalaryDialog, AddSalaryDialogData } from './add-salary-dialog';

const DATA: AddSalaryDialogData = {
  employeeId: 10432,
  defaultCurrency: 'INR',
  currencies: [
    { code: 'INR', minorUnitDigits: 2 },
    { code: 'JPY', minorUnitDigits: 0 },
  ],
};

const ENTRIES_URL = '/api/employees/10432/salary-entries';

describe('AddSalaryDialog', () => {
  let fixture: ComponentFixture<AddSalaryDialog>;
  let page: HTMLElement;
  let http: HttpTestingController;
  const close = vi.fn();

  beforeEach(async () => {
    close.mockReset();
    await TestBed.configureTestingModule({
      imports: [AddSalaryDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: DATA },
        { provide: MatDialogRef, useValue: { close } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AddSalaryDialog);
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

  function type(name: string, value: string): void {
    const field = page.querySelector<HTMLInputElement | HTMLSelectElement>(
      `[formControlName="${name}"]`,
    )!;
    field.value = value;
    field.dispatchEvent(new Event(field instanceof HTMLSelectElement ? 'change' : 'input'));
  }

  function submit(): void {
    page.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
  }

  /** The error shown inside the form field of one control, so a message under the wrong field fails. */
  function errorOf(name: string): string {
    const field = page.querySelector(`[formControlName="${name}"]`)!;
    return field.closest('mat-form-field')?.querySelector('mat-error')?.textContent?.trim() ?? '';
  }

  function alertText(): string {
    return page.querySelector('[role="alert"]')?.textContent ?? '';
  }

  it('preselects the current currency of the employee', () => {
    expect(page.querySelector<HTMLSelectElement>('select[formControlName="currency"]')!.value).toBe(
      'INR',
    );
  });

  it('does not call the server while the date or amount is empty and says what is missing', () => {
    submit();
    fixture.detectChanges();

    http.expectNone(ENTRIES_URL);
    expect(errorOf('effectiveDate')).toBe('This field is required.');
    expect(errorOf('amount')).toBe('This field is required.');
  });

  it('sends the amount as exact minor units with the chosen currency and closes', async () => {
    type('effectiveDate', '2026-04-01');
    type('amount', '1,500,000.50');

    submit();
    const call = http.expectOne(ENTRIES_URL);
    expect(call.request.body).toEqual({
      effectiveDate: '2026-04-01',
      amountMinor: 150000050,
      currency: 'INR',
    });
    call.flush({ id: 5 });

    await vi.waitFor(() => expect(close).toHaveBeenCalledWith(true));
  });

  it('rejects decimals for a currency without minor units, even if the amount was typed first', () => {
    type('effectiveDate', '2026-04-01');
    type('amount', '10.50');
    type('currency', 'JPY');
    fixture.detectChanges();

    submit();
    fixture.detectChanges();

    http.expectNone(ENTRIES_URL);
    expect(errorOf('amount')).toBe('This currency has no decimal places.');
  });

  it('sends one request when the button is clicked twice', async () => {
    type('effectiveDate', '2026-04-01');
    type('amount', '100');

    submit();
    submit();

    http.expectOne(ENTRIES_URL).flush({ id: 5 });
    await vi.waitFor(() => expect(close).toHaveBeenCalledTimes(1));
  });

  it('shows a duplicate date from the server in a banner and keeps what was typed', async () => {
    type('effectiveDate', '2026-04-01');
    type('amount', '100');

    submit();
    http.expectOne(ENTRIES_URL).flush(
      {
        code: 'DUPLICATE_SALARY_DATE',
        detail: 'There is already a salary effective on 2026-04-01.',
      },
      { status: 422, statusText: 'Unprocessable Content' },
    );

    await until(() => expect(alertText()).toContain('There is already a salary effective on'));
    expect(page.querySelector<HTMLInputElement>('input[formControlName="amount"]')!.value).toBe(
      '100',
    );
    expect(close).not.toHaveBeenCalled();
  });

  it('puts a server error about the amount under the amount field', async () => {
    type('effectiveDate', '2026-04-01');
    type('amount', '100');

    submit();
    http
      .expectOne(ENTRIES_URL)
      .flush(
        { errors: [{ field: 'amountMinor', message: 'must be greater than or equal to 1' }] },
        { status: 400, statusText: 'Bad Request' },
      );

    await until(() => expect(errorOf('amount')).toBe('must be greater than or equal to 1'));
  });

  it('enables the button again after a failed save', async () => {
    type('effectiveDate', '2026-04-01');
    type('amount', '100');
    const button = page.querySelector<HTMLButtonElement>('button[type="submit"]')!;

    submit();
    fixture.detectChanges();
    expect(button.disabled).toBe(true);
    http.expectOne(ENTRIES_URL).error(new ProgressEvent('error'));

    await until(() => {
      expect(button.disabled).toBe(false);
      expect(alertText()).toContain('Could not save');
    });
  });
});
