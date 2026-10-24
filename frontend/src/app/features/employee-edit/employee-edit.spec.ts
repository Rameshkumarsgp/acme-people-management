import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { EmployeeEdit } from './employee-edit';

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
  version: 4,
};

describe('EmployeeEdit', () => {
  let fixture: ComponentFixture<EmployeeEdit>;
  let page: HTMLElement;
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmployeeEdit],
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
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(EmployeeEdit);
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

  function answerLists(): void {
    http.expectOne('/api/reference/countries').flush([
      { code: 'IN', name: 'India' },
      { code: 'US', name: 'United States' },
    ]);
    http.expectOne('/api/reference/departments').flush([
      { id: 3, name: 'Engineering' },
      { id: 5, name: 'Sales' },
    ]);
    http.expectOne('/api/reference/job-titles').flush([{ id: 12, name: 'Software Engineer' }]);
    http.expectOne('/api/reference/currencies').flush([{ code: 'INR', minorUnitDigits: 2 }]);
  }

  async function openForm(employee: object = EMPLOYEE): Promise<void> {
    http.expectOne('/api/employees/10432').flush(employee);
    answerLists();
    await until(() => expect(page.querySelector('form')).not.toBeNull());
  }

  const input = (name: string) =>
    page.querySelector<HTMLInputElement>(`input[formControlName=${name}]`)!;
  const select = (name: string) =>
    page.querySelector<HTMLSelectElement>(`select[formControlName=${name}]`)!;

  function type(name: string, value: string): void {
    input(name).value = value;
    input(name).dispatchEvent(new Event('input'));
  }

  function choose(name: string, value: string): void {
    select(name).value = value;
    select(name).dispatchEvent(new Event('change'));
  }

  const save = () => page.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
  const saveRequest = () =>
    http.expectOne((request) => request.method === 'PUT' && request.url === '/api/employees/10432');

  it('says it is loading until the answers arrive', () => {
    expect(page.textContent).toContain('Loading');
    http.expectOne('/api/employees/10432').flush(EMPLOYEE);
    answerLists();
  });

  it('shows the employee in the form', async () => {
    await openForm();

    expect(input('fullName').value).toBe('Asha Rao');
    expect(input('email').value).toBe('asha.rao@acme.example');
    expect(input('hireDate').value).toBe('2024-04-01');
    expect(select('departmentId').value).toBe('3');
    expect(select('jobTitleId').value).toBe('12');
    expect(select('countryCode').value).toBe('IN');
    expect(input('terminationDate').value).toBe('');
  });

  it('shows an existing termination date', async () => {
    await openForm({ ...EMPLOYEE, terminationDate: '2026-09-30' });

    expect(input('terminationDate').value).toBe('2026-09-30');
  });

  it('has no salary fields, because salary is changed from the salary history', async () => {
    await openForm();

    expect(page.querySelector('[formControlName=currency]')).toBeNull();
    expect(page.querySelector('[formControlName=salaryAmount]')).toBeNull();
  });

  it('sends the whole profile with the version it was opened with, and returns to the employee', async () => {
    await openForm();
    type('fullName', 'Asha R. Rao');
    choose('departmentId', '5');

    save();
    const request = saveRequest();
    request.flush({ ...EMPLOYEE, version: 5 });

    expect(request.request.body).toEqual({
      fullName: 'Asha R. Rao',
      email: 'asha.rao@acme.example',
      hireDate: '2024-04-01',
      terminationDate: null,
      departmentId: 5,
      jobTitleId: 12,
      countryCode: 'IN',
      version: 4,
    });
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/employees/10432'));
  });

  it('sends a termination date when one is entered', async () => {
    await openForm();
    type('terminationDate', '2026-09-30');

    save();
    const request = saveRequest();
    request.flush({ ...EMPLOYEE, version: 5 });

    expect(request.request.body.terminationDate).toBe('2026-09-30');
  });

  it('sends no termination date when it is cleared', async () => {
    await openForm({ ...EMPLOYEE, terminationDate: '2026-09-30' });
    type('terminationDate', '');

    save();
    const request = saveRequest();
    request.flush({ ...EMPLOYEE, version: 5 });

    expect(request.request.body.terminationDate).toBeNull();
  });

  it('asks for a name and a valid email before it sends anything', async () => {
    await openForm();
    type('fullName', '');
    type('email', 'not-an-email');

    save();

    await until(() => {
      const text = page.textContent ?? '';
      expect(text).toContain('This field is required.');
      expect(text).toContain('Enter a valid email address.');
    });
    http.expectNone((request) => request.method === 'PUT');
  });

  it('shows a server message under its field and keeps the typed values', async () => {
    await openForm();
    type('fullName', 'Typed Name');

    save();
    saveRequest().flush(
      { errors: [{ field: 'departmentId', message: 'Unknown department.' }] },
      { status: 400, statusText: 'Bad Request' },
    );

    await until(() =>
      expect(page.querySelector('.mat-mdc-form-field-error')?.textContent).toContain(
        'Unknown department.',
      ),
    );
    expect(page.querySelector('[role="alert"]')).toBeNull();
    expect(input('fullName').value).toBe('Typed Name');
  });

  it('shows a duplicate email under the email field', async () => {
    await openForm();

    save();
    saveRequest().flush(
      { code: 'EMAIL_ALREADY_EXISTS', detail: 'An employee with this email already exists.' },
      { status: 409, statusText: 'Conflict' },
    );

    await until(() =>
      expect(page.textContent).toContain('An employee with this email already exists.'),
    );
  });

  it('shows a broken salary rule in a banner with the server wording', async () => {
    await openForm();
    type('terminationDate', '2025-01-01');

    save();
    saveRequest().flush(
      {
        code: 'SALARY_AFTER_TERMINATION',
        detail: 'A salary cannot be effective after the termination date, 2025-01-01.',
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );

    await until(() =>
      expect(page.querySelector('[role="alert"]')?.textContent).toContain(
        'A salary cannot be effective after the termination date, 2025-01-01.',
      ),
    );
  });

  it('says someone else changed the employee, and reloads the latest values on request', async () => {
    await openForm();
    type('fullName', 'My Edit');

    save();
    saveRequest().flush(
      { code: 'VERSION_CONFLICT', detail: 'Employee 10432 was changed by someone else.' },
      { status: 409, statusText: 'Conflict' },
    );
    await until(() =>
      expect(page.querySelector('[role="alert"]')?.textContent).toContain(
        'changed by someone else',
      ),
    );

    page.querySelector<HTMLButtonElement>('button[data-test="reload"]')!.click();
    http
      .expectOne('/api/employees/10432')
      .flush({ ...EMPLOYEE, fullName: 'Changed Elsewhere', version: 5 });

    await until(() => {
      expect(input('fullName').value).toBe('Changed Elsewhere');
      expect(page.querySelector('[role="alert"]')).toBeNull();
    });
    save();
    const request = saveRequest();
    request.flush({ ...EMPLOYEE, version: 6 });
    expect(request.request.body.version).toBe(5);
  });

  it('ignores a second click while saving', async () => {
    await openForm();

    save();
    save();

    const request = saveRequest();
    request.flush({ ...EMPLOYEE, version: 5 });
    http.expectNone((candidate) => candidate.method === 'PUT');
  });

  it('says the employee does not exist when the server answers 404', async () => {
    http.expectOne('/api/employees/10432').flush(null, { status: 404, statusText: 'Not Found' });
    answerLists();

    await until(() =>
      expect(page.querySelector('[role="alert"]')?.textContent).toContain(
        'No employee with this ID',
      ),
    );
  });

  it('offers to try again when loading fails, and shows the form after a retry', async () => {
    http.expectOne('/api/employees/10432').error(new ProgressEvent('error'));
    answerLists();
    await until(() =>
      expect(page.querySelector('[role="alert"]')?.textContent).toContain('Could not load'),
    );

    page.querySelector<HTMLButtonElement>('button[data-test="retry"]')!.click();
    await openForm();

    expect(input('fullName').value).toBe('Asha Rao');
  });

  it('can be cancelled, going back to the employee', async () => {
    await openForm();

    expect(page.querySelector('a[href="/employees/10432"]')?.textContent).toContain('Cancel');
  });
});
