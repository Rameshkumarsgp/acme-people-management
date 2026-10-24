import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatPaginator } from '@angular/material/paginator';
import { By } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Directory } from './directory';

@Component({ template: '' })
class Elsewhere {}

const ROW = {
  id: 10432,
  fullName: 'Asha Rao',
  email: 'asha.rao@acme.example',
  department: 'Engineering',
  jobTitle: 'Software Engineer',
  country: 'India',
  status: 'ACTIVE',
  hireDate: '2024-04-01',
  currentSalary: { amountMinor: 125000050, currency: 'INR' },
};

const PAGE = { content: [ROW], page: 0, size: 20, totalElements: 41, totalPages: 3 };
const EMPTY = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

describe('Directory', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  let router: Router;
  let page: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'employees', component: Directory },
          { path: 'employees/new', component: Elsewhere },
          { path: 'employees/:id', component: Elsewhere },
          { path: '', component: Elsewhere },
        ]),
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  async function open(url: string): Promise<void> {
    await harness.navigateByUrl(url, Directory);
    harness.detectChanges();
    page = harness.routeNativeElement as HTMLElement;
  }

  function answerLists(): void {
    http.expectOne('/api/reference/countries').flush([
      { code: 'IN', name: 'India' },
      { code: 'US', name: 'United States' },
    ]);
    http.expectOne('/api/reference/departments').flush([{ id: 3, name: 'Engineering' }]);
    http.expectOne('/api/reference/job-titles').flush([{ id: 12, name: 'Software Engineer' }]);
    http.expectOne('/api/reference/currencies').flush([
      { code: 'INR', minorUnitDigits: 2 },
      { code: 'JPY', minorUnitDigits: 0 },
    ]);
  }

  function pageRequest(): TestRequest {
    return http.expectOne((request) => request.url === '/api/employees');
  }

  async function openAndAnswer(url: string, answer: object = PAGE): Promise<void> {
    await open(url);
    answerLists();
    pageRequest().flush(answer);
    await until(() => expect(harness.routeNativeElement?.textContent).not.toContain('Loading'));
  }

  /** Redraws until the check passes, because the page updates after the answers resolve. */
  async function until(check: () => void): Promise<void> {
    await vi.waitFor(() => {
      harness.detectChanges();
      check();
    });
  }

  function choose(selector: string, value: string): void {
    const select = page.querySelector<HTMLSelectElement>(selector)!;
    select.value = value;
    select.dispatchEvent(new Event('change'));
  }

  function type(text: string): void {
    const input = page.querySelector<HTMLInputElement>('input[data-test="search"]')!;
    input.value = text;
    input.dispatchEvent(new Event('input'));
  }

  const queryParams = () => router.parseUrl(router.url).queryParams;

  it('asks for the first page of active employees sorted by name when the address has no parameters', async () => {
    await open('/employees');
    answerLists();
    const request = pageRequest();
    request.flush(PAGE);

    expect(request.request.params.get('status')).toBe('ACTIVE');
    expect(request.request.params.get('sort')).toBe('fullName,asc');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    expect(request.request.params.has('q')).toBe(false);
  });

  it('reads its state from the address', async () => {
    await open(
      '/employees?q=rao&countryCode=IN&departmentId=3&currency=INR&sort=salary,desc&page=2&size=50',
    );
    answerLists();
    const request = pageRequest();
    request.flush(PAGE);

    expect(request.request.params.get('q')).toBe('rao');
    expect(request.request.params.get('countryCode')).toBe('IN');
    expect(request.request.params.get('departmentId')).toBe('3');
    expect(request.request.params.get('currency')).toBe('INR');
    expect(request.request.params.get('sort')).toBe('salary,desc');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('50');
  });

  it('shows the rows with the salary in its own currency and a link to the employee', async () => {
    await openAndAnswer('/employees');

    await until(() => {
      const text = page.textContent ?? '';
      expect(text).toContain('Asha Rao');
      expect(text).toContain('Engineering');
      expect(text).toContain('Software Engineer');
      expect(text).toContain('India');
      expect(text).toContain('2024-04-01');
      expect(text).toContain('1,250,000.50 INR');
      expect(page.querySelector('a[href="/employees/10432"]')?.textContent).toContain('Asha Rao');
    });
  });

  it('offers the reference lists in the filters', async () => {
    await openAndAnswer('/employees');

    const options = (selector: string) =>
      Array.from(page.querySelectorAll(`${selector} option`)).map((option) =>
        option.textContent?.trim(),
      );
    expect(options('select[data-test="country"]')).toEqual([
      'All countries',
      'India',
      'United States',
    ]);
    expect(options('select[data-test="department"]')).toEqual(['All departments', 'Engineering']);
    expect(options('select[data-test="job-title"]')).toEqual([
      'All job titles',
      'Software Engineer',
    ]);
    expect(options('select[data-test="currency"]')).toEqual(['All currencies', 'INR', 'JPY']);
    expect(options('select[data-test="status"]')).toEqual(['Active', 'Former', 'All']);
  });

  it('shows the filters from the address in the controls', async () => {
    await openAndAnswer(
      '/employees?q=rao&countryCode=IN&departmentId=3&jobTitleId=12&currency=INR&status=FORMER',
    );

    const value = (selector: string) => page.querySelector<HTMLSelectElement>(selector)!.value;
    await until(() => {
      expect(page.querySelector<HTMLInputElement>('input[data-test="search"]')!.value).toBe('rao');
      expect(value('select[data-test="country"]')).toBe('IN');
      expect(value('select[data-test="department"]')).toBe('3');
      expect(value('select[data-test="job-title"]')).toBe('12');
      expect(value('select[data-test="currency"]')).toBe('INR');
      expect(value('select[data-test="status"]')).toBe('FORMER');
    });
  });

  it('shows the first page again and asks again when a filter changes', async () => {
    await openAndAnswer('/employees?page=3');

    choose('select[data-test="country"]', 'IN');

    await vi.waitFor(() => expect(queryParams()['countryCode']).toBe('IN'));
    expect(queryParams()['page']).toBeUndefined();
    const request = pageRequest();
    request.flush(PAGE);
    expect(request.request.params.get('countryCode')).toBe('IN');
    expect(request.request.params.get('page')).toBe('0');
  });

  it('filters by department, job title and status', async () => {
    await openAndAnswer('/employees');

    choose('select[data-test="department"]', '3');
    await vi.waitFor(() => expect(queryParams()['departmentId']).toBe('3'));
    pageRequest().flush(PAGE);
    await until(() => expect(page.textContent).not.toContain('Loading'));

    choose('select[data-test="job-title"]', '12');
    await vi.waitFor(() => expect(queryParams()['jobTitleId']).toBe('12'));
    pageRequest().flush(PAGE);
    await until(() => expect(page.textContent).not.toContain('Loading'));

    choose('select[data-test="status"]', 'FORMER');
    await vi.waitFor(() => expect(queryParams()['status']).toBe('FORMER'));
    const request = pageRequest();
    request.flush(PAGE);
    expect(request.request.params.get('departmentId')).toBe('3');
    expect(request.request.params.get('jobTitleId')).toBe('12');
    expect(request.request.params.get('status')).toBe('FORMER');
  });

  it('waits for a pause in the typing before it searches', async () => {
    await openAndAnswer('/employees');

    type('ra');
    await new Promise((resolve) => setTimeout(resolve, 30));
    type('rao');

    await vi.waitFor(() => expect(queryParams()['q']).toBe('rao'));
    harness.detectChanges();
    const requests = http.match((request) => request.url === '/api/employees');
    expect(requests.map((request) => request.request.params.get('q'))).toEqual(['rao']);
    requests.forEach((request) => request.flush(PAGE));
  });

  it('cannot sort by salary until a currency is chosen', async () => {
    await openAndAnswer('/employees');
    const salaryHeader = () => page.querySelector('th.mat-column-salary')!;

    expect(salaryHeader().classList).toContain('mat-sort-header-disabled');

    choose('select[data-test="currency"]', 'INR');
    await vi.waitFor(() => expect(queryParams()['currency']).toBe('INR'));
    pageRequest().flush(PAGE);
    await until(() => expect(salaryHeader().classList).not.toContain('mat-sort-header-disabled'));
  });

  it('sorts by a column when its header is chosen', async () => {
    await openAndAnswer('/employees');

    page.querySelector<HTMLElement>('th.mat-column-hireDate')!.click();

    await vi.waitFor(() => expect(queryParams()['sort']).toBe('hireDate,asc'));
    pageRequest().flush(PAGE);
  });

  it('sorts descending on the second choice and goes back to the default on the third', async () => {
    await openAndAnswer('/employees');
    const header = () => page.querySelector<HTMLElement>('th.mat-column-hireDate')!;

    header().click();
    await vi.waitFor(() => expect(queryParams()['sort']).toBe('hireDate,asc'));
    pageRequest().flush(PAGE);
    await until(() => expect(page.textContent).not.toContain('Loading'));

    header().click();
    await vi.waitFor(() => expect(queryParams()['sort']).toBe('hireDate,desc'));
    pageRequest().flush(PAGE);
    await until(() => expect(page.textContent).not.toContain('Loading'));

    header().click();
    await vi.waitFor(() => expect(queryParams()['sort']).toBeUndefined());
    pageRequest().flush(PAGE);
  });

  it('goes back to the default sort when the currency is cleared while sorted by salary', async () => {
    await openAndAnswer('/employees?currency=INR&sort=salary,desc');

    choose('select[data-test="currency"]', '');

    await vi.waitFor(() => expect(queryParams()['currency']).toBeUndefined());
    expect(queryParams()['sort']).toBeUndefined();
    const request = pageRequest();
    request.flush(PAGE);
    expect(request.request.params.get('sort')).toBe('fullName,asc');
  });

  it('moves to the next page with the paginator', async () => {
    await openAndAnswer('/employees');

    page.querySelector<HTMLElement>('button.mat-mdc-paginator-navigation-next')!.click();

    await vi.waitFor(() => expect(queryParams()['page']).toBe('1'));
    pageRequest().flush(PAGE);
  });

  it('shows the first page again when the page size changes', async () => {
    await openAndAnswer('/employees?page=2');
    const paginator = harness.routeDebugElement!.query(By.directive(MatPaginator))
      .componentInstance as MatPaginator;

    paginator._changePageSize(50);

    await vi.waitFor(() => expect(queryParams()['size']).toBe('50'));
    expect(queryParams()['page']).toBeUndefined();
    pageRequest().flush(PAGE);
  });

  it('shows the amount as stored until the currency lists arrive', async () => {
    await open('/employees');
    pageRequest().flush(PAGE);
    await until(() => expect(page.textContent).toContain('125000050 INR'));

    answerLists();

    await until(() => expect(page.textContent).toContain('1,250,000.50 INR'));
  });

  it('says that nothing matches when the page is empty', async () => {
    await openAndAnswer('/employees?q=zzz', EMPTY);

    await until(() => expect(page.textContent).toContain('No employees match'));
  });

  it('offers to try again when loading fails, and shows the rows after a retry', async () => {
    await open('/employees');
    answerLists();
    pageRequest().error(new ProgressEvent('error'));
    await until(() =>
      expect(page.querySelector('[role="alert"]')?.textContent).toContain('Could not load'),
    );

    page.querySelector<HTMLButtonElement>('button[data-test="retry"]')!.click();
    const lists = http.match((request) => request.url.startsWith('/api/reference/'));
    expect(lists).toHaveLength(4);
    lists.forEach((list) => list.flush([]));
    pageRequest().flush(PAGE);

    await until(() => expect(page.textContent).toContain('Asha Rao'));
  });

  it('ignores an answer that arrives after a newer request was made', async () => {
    await openAndAnswer('/employees');

    await router.navigateByUrl('/employees?countryCode=IN');
    harness.detectChanges();
    const older = pageRequest();
    await router.navigateByUrl('/employees?countryCode=US');
    harness.detectChanges();
    const newer = pageRequest();

    newer.flush({ ...PAGE, content: [{ ...ROW, id: 2, fullName: 'Ben Carter' }] });
    older.flush(PAGE);

    await until(() => expect(page.textContent).toContain('Ben Carter'));
    expect(page.textContent).not.toContain('Asha Rao');
  });

  it('links to the new employee form and back to the home page', async () => {
    await openAndAnswer('/employees');

    expect(page.querySelector('a[href="/employees/new"]')?.textContent).toContain('New employee');
    expect(page.querySelector('a[href="/"]')?.textContent).toContain('Back to home');
  });
});
