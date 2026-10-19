import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
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

  function answer(employee: object): void {
    http.expectOne('/api/employees/10432').flush(employee);
    http.expectOne('/api/reference/currencies').flush([{ code: 'INR', minorUnitDigits: 2 }]);
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

    await until(() =>
      expect(page.querySelector('[role="alert"]')?.textContent).toContain(
        'No employee with this ID',
      ),
    );
  });

  it('offers to try again when loading fails, and shows the employee after a retry', async () => {
    http.expectOne('/api/employees/10432').error(new ProgressEvent('error'));
    http.expectOne('/api/reference/currencies').flush([]);
    await until(() =>
      expect(page.querySelector('[role="alert"]')?.textContent).toContain('Could not load'),
    );

    page.querySelector<HTMLButtonElement>('button')!.click();
    answer(EMPLOYEE);

    await until(() => expect(page.textContent).toContain('Asha Rao'));
  });
});
