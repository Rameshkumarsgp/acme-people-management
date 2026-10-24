import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
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

  describe('editing and deleting', () => {
    let navigate: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
      navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    });

    function confirmDelete(answer: boolean) {
      return vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
        afterClosed: () => of(answer),
      } as MatDialogRef<unknown, boolean>);
    }

    async function clickDelete(employee: object = EMPLOYEE): Promise<void> {
      answer(employee);
      await until(() => expect(page.textContent).toContain('Asha Rao'));
      page.querySelector<HTMLButtonElement>('button[data-test="delete-employee"]')!.click();
    }

    const deleteRequest = () =>
      http.expectOne(
        (request) => request.method === 'DELETE' && request.url === '/api/employees/10432',
      );

    it('links to the edit form', async () => {
      answer(EMPLOYEE);

      await until(() =>
        expect(page.querySelector('a[href="/employees/10432/edit"]')?.textContent).toContain(
          'Edit',
        ),
      );
    });

    it('asks for confirmation naming the employee before it deletes', async () => {
      const open = confirmDelete(false);

      await clickDelete();

      expect(open).toHaveBeenCalledTimes(1);
      expect(open.mock.calls[0][1]?.data).toEqual({ fullName: 'Asha Rao' });
    });

    it('does nothing when the confirmation is cancelled', async () => {
      confirmDelete(false);

      await clickDelete();
      await new Promise((resolve) => setTimeout(resolve));

      http.expectNone((request) => request.method === 'DELETE');
      expect(navigate).not.toHaveBeenCalled();
    });

    it('deletes with the version it saw and goes to the directory', async () => {
      confirmDelete(true);

      await clickDelete({ ...EMPLOYEE, version: 3 });
      let sent = '';
      await vi.waitFor(() => {
        const request = deleteRequest();
        sent = request.request.params.get('version') ?? '';
        request.flush(null, { status: 204, statusText: 'No Content' });
      });

      expect(sent).toBe('3');
      await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/employees'));
    });

    it('says someone else changed the employee, and shows the latest version, when the version is stale', async () => {
      confirmDelete(true);

      await clickDelete();
      await vi.waitFor(() =>
        deleteRequest().flush(
          { code: 'VERSION_CONFLICT', detail: 'changed' },
          { status: 409, statusText: 'Conflict' },
        ),
      );
      await vi.waitFor(() => answer({ ...EMPLOYEE, fullName: 'Asha R. Rao', version: 3 }));

      await until(() => {
        expect(page.textContent).toContain('Asha R. Rao');
        expect(page.querySelector('[role="alert"]')?.textContent).toContain(
          'changed by someone else',
        );
      });
      expect(navigate).not.toHaveBeenCalled();
    });

    it('says the employee does not exist when it was already deleted', async () => {
      confirmDelete(true);

      await clickDelete();
      await vi.waitFor(() => deleteRequest().flush(null, { status: 404, statusText: 'Not Found' }));

      await until(() =>
        expect(page.querySelector('[role="alert"]')?.textContent).toContain(
          'No employee with this ID',
        ),
      );
    });

    it('says it could not delete when the server fails', async () => {
      confirmDelete(true);

      await clickDelete();
      await vi.waitFor(() => deleteRequest().error(new ProgressEvent('error')));

      await until(() =>
        expect(page.querySelector('[role="alert"]')?.textContent).toContain('Could not delete'),
      );
      expect(navigate).not.toHaveBeenCalled();
    });
  });
});
