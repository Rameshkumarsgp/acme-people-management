import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { EmployeeDetail } from './employee-detail';

const EMPLOYEE = {
  id: 10432,
  fullName: 'Asha Rao',
  email: 'asha.rao@acme.example',
  hireDate: '2024-04-01',
  terminationDate: null,
  status: 'ACTIVE',
  department: { id: 3, name: 'Engineering' },
  jobTitle: { id: 12, name: 'Software Engineer' },
  country: { code: 'IN', name: 'India' },
  currentSalary: { effectiveDate: '2024-04-01', amountMinor: 125000050, currency: 'INR' },
  version: 0,
};

const ENTRIES = [
  {
    id: 1,
    effectiveDate: '2024-04-01',
    amountMinor: 125000050,
    currency: 'INR',
    endDate: null,
    current: true,
    scheduled: false,
  },
];

describe('EmployeeDetail', () => {
  let fixture: ComponentFixture<EmployeeDetail>;
  let page: HTMLElement;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmployeeDetail],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '10432' }) } },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EmployeeDetail);
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

  function answer(employee: object, entries: object[] = ENTRIES): void {
    http.expectOne('/api/employees/10432').flush(employee);
    http.expectOne('/api/reference/currencies').flush([{ code: 'INR', minorUnitDigits: 2 }]);
    http.expectOne('/api/employees/10432/salary-entries').flush(entries);
  }

  it('says it is loading until the answers arrive', () => {
    expect(page.textContent).toContain('Loading');
    answer(EMPLOYEE);
  });

  it('shows the profile and the current salary in its own currency', async () => {
    answer(EMPLOYEE);

    await until(() => {
      const text = page.textContent ?? '';
      expect(text).toContain('Asha Rao');
      expect(text).toContain('asha.rao@acme.example');
      expect(text).toContain('Engineering');
      expect(text).toContain('Software Engineer');
      expect(text).toContain('India');
      expect(text).toContain('1,250,000.50 INR');
      expect(text).toContain('Active');
    });
  });

  it('says the employee does not exist when the server answers 404', async () => {
    http.expectOne('/api/employees/10432').flush(null, { status: 404, statusText: 'Not Found' });
    http.expectOne('/api/reference/currencies').flush([]);
    http.expectOne('/api/employees/10432/salary-entries').flush([]);

    await until(() =>
      expect(page.querySelector('[role="alert"]')?.textContent).toContain(
        'No employee with this ID',
      ),
    );
  });

  it('offers to try again when loading fails, and shows the employee after a retry', async () => {
    http.expectOne('/api/employees/10432').error(new ProgressEvent('error'));
    http.expectOne('/api/reference/currencies').flush([]);
    http.expectOne('/api/employees/10432/salary-entries').flush([]);
    await until(() =>
      expect(page.querySelector('[role="alert"]')?.textContent).toContain('Could not load'),
    );

    page.querySelector<HTMLButtonElement>('button')!.click();
    answer(EMPLOYEE);

    await until(() => expect(page.textContent).toContain('Asha Rao'));
  });

  it('shows the salary history under the profile', async () => {
    answer(EMPLOYEE);

    await until(() => {
      expect(page.querySelectorAll('tr[mat-row]').length).toBe(1);
      expect(page.querySelector('tr[mat-row]')?.textContent).toContain('Current');
    });
  });

  it('links back to the directory and to the home page', async () => {
    answer(EMPLOYEE);

    await until(() => {
      expect(page.querySelector('a[href="/employees"]')?.textContent).toContain(
        'Back to directory',
      );
      expect(page.querySelector('a[href="/"]')?.textContent).toContain('Back to home');
    });
  });

  it('opens the add salary dialog with the employee, the currencies and the current currency', async () => {
    const open = vi
      .spyOn(TestBed.inject(MatDialog), 'open')
      .mockReturnValue({ afterClosed: () => of(false) } as MatDialogRef<unknown, boolean>);
    answer(EMPLOYEE);
    await until(() => expect(page.textContent).toContain('Asha Rao'));

    page.querySelector<HTMLButtonElement>('button[data-test="add-salary"]')!.click();

    expect(open).toHaveBeenCalledTimes(1);
    expect(open.mock.calls[0][1]?.data).toEqual({
      employeeId: 10432,
      currencies: [{ code: 'INR', minorUnitDigits: 2 }],
      defaultCurrency: 'INR',
    });
  });

  it('reads the employee and the history again after a salary was added', async () => {
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(true),
    } as MatDialogRef<unknown, boolean>);
    answer(EMPLOYEE);
    await until(() => expect(page.textContent).toContain('Asha Rao'));

    page.querySelector<HTMLButtonElement>('button[data-test="add-salary"]')!.click();

    // The reads start after the dialog promise resolves, so wait for the requests to appear.
    await vi.waitFor(() => answer(EMPLOYEE));
    await until(() => expect(page.textContent).toContain('Asha Rao'));
  });

  it('does not read again when the dialog was closed without saving', async () => {
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(false),
    } as MatDialogRef<unknown, boolean>);
    answer(EMPLOYEE);
    await until(() => expect(page.textContent).toContain('Asha Rao'));

    page.querySelector<HTMLButtonElement>('button[data-test="add-salary"]')!.click();
    await new Promise((resolve) => setTimeout(resolve));

    http.expectNone('/api/employees/10432');
  });
});
