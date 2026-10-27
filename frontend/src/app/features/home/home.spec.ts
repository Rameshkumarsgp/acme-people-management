import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthService } from '../../core/auth/auth.service';
import { Home } from './home';

@Component({ template: '' })
class Elsewhere {}

const COUNTRIES = {
  dimension: 'COUNTRY',
  groups: [
    {
      key: 'IN',
      label: 'India',
      currency: 'INR',
      headcount: 2300,
      total: '18014398509481983',
      min: 63000000,
      median: 255000000,
      mean: 300000050,
      max: 540800000,
    },
    {
      key: 'US',
      label: 'United States',
      currency: 'USD',
      headcount: 1200,
      total: '9000000000',
      min: 5000000,
      median: 9000000,
      mean: 7500000,
      max: 20000000,
    },
  ],
};

const HIGHEST = {
  order: 'HIGHEST',
  currency: 'INR',
  employees: [
    {
      id: 7433,
      fullName: 'Arjun Rao',
      department: 'Engineering',
      jobTitle: 'Engineering Manager',
      country: 'India',
      amountMinor: 540800000,
      effectiveDate: '2025-04-01',
    },
  ],
};

describe('Home', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  let router: Router;
  let auth: AuthService;
  let page: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: '', component: Home },
          { path: 'employees/:id', component: Elsewhere },
          { path: 'login', component: Elsewhere },
        ]),
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    auth = TestBed.inject(AuthService);
    const login = auth.login('hr', 's3cret');
    http.expectOne('/api/auth/me').flush({ username: 'hr' });
    await login;
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  async function open(url: string): Promise<void> {
    await harness.navigateByUrl(url, Home);
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
      { code: 'USD', minorUnitDigits: 2 },
    ]);
  }

  /** Waits for the request, because the page asks after the change detection that follows an answer. */
  async function nextRequest(url: string): Promise<TestRequest> {
    let found: TestRequest[] = [];
    await vi.waitFor(() => {
      harness.detectChanges();
      found = http.match((request) => request.url === url);
      expect(found).toHaveLength(1);
    });
    return found[0];
  }

  const nextInsights = () => nextRequest('/api/insights');
  const nextExtremes = () => nextRequest('/api/insights/extremes');

  async function openAndAnswer(url = '/'): Promise<void> {
    await open(url);
    answerLists();
    (await nextInsights()).flush(COUNTRIES);
    (await nextExtremes()).flush(HIGHEST);
    await until(() => expect(page.textContent).toContain('Arjun Rao'));
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

  const queryParams = () => router.parseUrl(router.url).queryParams;

  it('greets the signed-in user by name and links to the directory and the new employee form', async () => {
    await openAndAnswer();

    expect(page.textContent).toContain('Welcome, hr');
    expect(page.querySelector('a[href="/employees"]')?.textContent).toContain('Employee directory');
    expect(page.querySelector('a[href="/employees/new"]')?.textContent).toContain('New employee');
  });

  it('ends the session and returns to the login page on sign out', async () => {
    await openAndAnswer();
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    page.querySelector<HTMLButtonElement>('button')!.click();

    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/login');
  });

  it('asks for the active employees by country when the address has no parameters', async () => {
    await open('/');
    answerLists();
    const request = await nextInsights();
    request.flush(COUNTRIES);

    expect(request.request.params.get('dimension')).toBe('COUNTRY');
    expect(request.request.params.get('status')).toBe('ACTIVE');
    expect(request.request.params.keys().sort()).toEqual(['dimension', 'status']);
    (await nextExtremes()).flush(HIGHEST);
  });

  it('reads its state from the address', async () => {
    await open(
      '/?dimension=TENURE&countryCode=IN&departmentId=3&jobTitleId=12&currency=INR&status=ALL&rank=LOWEST',
    );
    answerLists();
    const request = await nextInsights();
    const ranking = await nextExtremes();
    request.flush({ dimension: 'TENURE', groups: [] });
    ranking.flush({ order: 'LOWEST', currency: 'INR', employees: [] });

    expect(request.request.params.get('dimension')).toBe('TENURE');
    expect(request.request.params.get('countryCode')).toBe('IN');
    expect(request.request.params.get('departmentId')).toBe('3');
    expect(request.request.params.get('jobTitleId')).toBe('12');
    expect(request.request.params.get('currency')).toBe('INR');
    expect(request.request.params.get('status')).toBe('ALL');
    expect(ranking.request.params.get('order')).toBe('LOWEST');
    expect(ranking.request.params.get('currency')).toBe('INR');
    expect(ranking.request.params.get('limit')).toBe('10');
  });

  it('shows each group in its own currency with every figure formatted from its digits', async () => {
    await openAndAnswer();

    await until(() => {
      const rows = Array.from(page.querySelectorAll('app-insights-table tr[mat-row]')).map((row) =>
        Array.from(row.querySelectorAll('td')).map((cell) => cell.textContent?.trim()),
      );
      expect(rows).toEqual([
        [
          'India',
          'INR',
          '2300',
          '180,143,985,094,819.83 INR',
          '630,000.00 INR',
          '2,550,000.00 INR',
          '3,000,000.50 INR',
          '5,408,000.00 INR',
        ],
        [
          'United States',
          'USD',
          '1200',
          '90,000,000.00 USD',
          '50,000.00 USD',
          '90,000.00 USD',
          '75,000.00 USD',
          '200,000.00 USD',
        ],
      ]);
    });
  });

  it('offers the dimensions and the reference lists in the controls', async () => {
    await openAndAnswer();

    const options = (selector: string) =>
      Array.from(page.querySelectorAll(`${selector} option`)).map((option) =>
        option.textContent?.trim(),
      );
    expect(options('select[data-test="dimension"]')).toEqual([
      'Overall',
      'Country',
      'Department',
      'Job title',
      'Job title in country',
      'Tenure',
    ]);
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
    expect(options('select[data-test="currency"]')).toEqual(['All currencies', 'INR', 'USD']);
    expect(options('select[data-test="status"]')).toEqual(['Active', 'Former', 'All']);
  });

  it('shows the state from the address in the controls', async () => {
    await open('/?dimension=JOB_TITLE&countryCode=IN&departmentId=3&status=FORMER');
    answerLists();
    (await nextInsights()).flush(COUNTRIES);
    (await nextExtremes()).flush(HIGHEST);

    const value = (selector: string) => page.querySelector<HTMLSelectElement>(selector)!.value;
    await until(() => {
      expect(value('select[data-test="dimension"]')).toBe('JOB_TITLE');
      expect(value('select[data-test="country"]')).toBe('IN');
      expect(value('select[data-test="department"]')).toBe('3');
      expect(value('select[data-test="status"]')).toBe('FORMER');
    });
  });

  it('puts a changed dimension in the address and asks again', async () => {
    await openAndAnswer();

    choose('select[data-test="dimension"]', 'TENURE');

    await vi.waitFor(() => expect(queryParams()['dimension']).toBe('TENURE'));
    const request = await nextInsights();
    expect(request.request.params.get('dimension')).toBe('TENURE');
    request.flush({ dimension: 'TENURE', groups: [COUNTRIES.groups[0]] });

    await until(() => expect(page.textContent).toContain('India'));
    http.expectNone((candidate) => candidate.url === '/api/insights/extremes');
  });

  it('puts a changed filter in the address and asks for both the groups and the ranking again', async () => {
    await openAndAnswer();

    choose('select[data-test="department"]', '3');

    await vi.waitFor(() => expect(queryParams()['departmentId']).toBe('3'));
    const request = await nextInsights();
    const ranking = await nextExtremes();
    expect(request.request.params.get('departmentId')).toBe('3');
    expect(ranking.request.params.get('departmentId')).toBe('3');
    request.flush(COUNTRIES);
    ranking.flush(HIGHEST);
  });

  it('ranks in the currency filter and clears a chosen ranking currency when the filter changes', async () => {
    await openAndAnswer('/?rankCurrency=USD');

    choose('select[data-test="currency"]', 'INR');

    await vi.waitFor(() => expect(queryParams()['currency']).toBe('INR'));
    expect(queryParams()['rankCurrency']).toBeUndefined();
    (await nextInsights()).flush(COUNTRIES);
    const ranking = await nextExtremes();
    expect(ranking.request.params.get('currency')).toBe('INR');
    ranking.flush(HIGHEST);
  });

  it('ranks in the chosen currency rather than the currency filter', async () => {
    await open('/?currency=INR&rankCurrency=USD');
    answerLists();
    (await nextInsights()).flush(COUNTRIES);
    const ranking = await nextExtremes();

    expect(ranking.request.params.get('currency')).toBe('USD');
    ranking.flush({ order: 'HIGHEST', currency: 'USD', employees: [] });
  });

  it('ranks in the first group currency until another is chosen', async () => {
    await open('/');
    answerLists();
    (await nextInsights()).flush(COUNTRIES);
    harness.detectChanges();
    const ranking = await nextExtremes();
    expect(ranking.request.params.get('currency')).toBe('INR');
    expect(ranking.request.params.get('order')).toBe('HIGHEST');
    ranking.flush(HIGHEST);
    await until(() =>
      expect(
        page.querySelector<HTMLSelectElement>('select[data-test="rank-currency"]')!.value,
      ).toBe('INR'),
    );

    choose('select[data-test="rank-currency"]', 'USD');

    await vi.waitFor(() => expect(queryParams()['rankCurrency']).toBe('USD'));
    const next = await nextExtremes();
    expect(next.request.params.get('currency')).toBe('USD');
    next.flush({ order: 'HIGHEST', currency: 'USD', employees: [] });
    http.expectNone((request) => request.url === '/api/insights');
  });

  it('asks for the lowest paid when the ranking order changes', async () => {
    await openAndAnswer();

    choose('select[data-test="rank"]', 'LOWEST');

    await vi.waitFor(() => expect(queryParams()['rank']).toBe('LOWEST'));
    const ranking = await nextExtremes();
    expect(ranking.request.params.get('order')).toBe('LOWEST');
    ranking.flush(HIGHEST);
    http.expectNone((request) => request.url === '/api/insights');
  });

  it('lists the ranked employees with the salary in the ranked currency and a link to each', async () => {
    await openAndAnswer();

    await until(() => {
      const text = page.querySelector('app-extremes-list')?.textContent ?? '';
      expect(text).toContain('Arjun Rao');
      expect(text).toContain('Engineering Manager');
      expect(text).toContain('5,408,000.00 INR');
      expect(page.querySelector('a[href="/employees/7433"]')?.textContent).toContain('Arjun Rao');
    });
  });

  it('says so when no employee matches', async () => {
    await open('/?countryCode=IN');
    answerLists();
    (await nextInsights()).flush({ dimension: 'COUNTRY', groups: [] });
    harness.detectChanges();

    await until(() => expect(page.querySelector('[data-test="no-groups"]')).not.toBeNull());
    expect(page.querySelector('app-insights-table')).toBeNull();
  });

  it('says so when nobody is paid in the ranked currency', async () => {
    await openAndAnswer();

    choose('select[data-test="rank-currency"]', 'USD');
    await vi.waitFor(() => expect(queryParams()['rankCurrency']).toBe('USD'));
    (await nextExtremes()).flush({ order: 'HIGHEST', currency: 'USD', employees: [] });

    await until(() => expect(page.querySelector('[data-test="no-employees"]')).not.toBeNull());
    expect(page.querySelector('[data-test="no-employees"]')?.textContent).toContain('USD');
  });

  it('shows an error with a retry when the insights cannot be loaded, and loads them on retry', async () => {
    await open('/');
    answerLists();
    (await nextInsights()).flush(null, { status: 500, statusText: 'Server Error' });

    await until(() => expect(page.querySelector('[role="alert"]')).not.toBeNull());
    expect(page.textContent).toContain('Could not load the insights.');

    page.querySelector<HTMLButtonElement>('button[data-test="retry"]')!.click();
    http.expectOne('/api/reference/countries').flush([]);
    http.expectOne('/api/reference/departments').flush([]);
    http.expectOne('/api/reference/job-titles').flush([]);
    http.expectOne('/api/reference/currencies').flush([]);
    (await nextInsights()).flush(COUNTRIES);
    (await nextExtremes()).flush(HIGHEST);
    await until(() => expect(page.querySelector('app-insights-table')).not.toBeNull());
  });

  it('shows an error when the ranking cannot be loaded', async () => {
    await open('/');
    answerLists();
    (await nextInsights()).flush(COUNTRIES);
    (await nextExtremes()).flush(null, { status: 500, statusText: 'Server Error' });

    await until(() => expect(page.textContent).toContain('Could not load the ranking.'));
    expect(page.querySelector('app-insights-table')).not.toBeNull();
  });

  it('ignores the answer of a request that a newer one replaced', async () => {
    await openAndAnswer();
    choose('select[data-test="dimension"]', 'TENURE');
    await vi.waitFor(() => expect(queryParams()['dimension']).toBe('TENURE'));
    const stale = await nextInsights();
    choose('select[data-test="dimension"]', 'DEPARTMENT');
    await vi.waitFor(() => expect(queryParams()['dimension']).toBe('DEPARTMENT'));
    const latest = await nextInsights();

    const table = () => page.querySelector('app-insights-table')?.textContent ?? '';

    latest.flush({ dimension: 'DEPARTMENT', groups: [COUNTRIES.groups[1]] });
    await until(() => expect(table()).not.toContain('India'));
    (await nextExtremes()).flush({ order: 'HIGHEST', currency: 'USD', employees: [] });
    stale.flush({ dimension: 'TENURE', groups: [COUNTRIES.groups[0]] });
    await new Promise((resolve) => setTimeout(resolve));
    harness.detectChanges();

    expect(table()).toContain('United States');
    expect(table()).not.toContain('India');
    expect(page.querySelector('[data-test="no-employees"]')?.textContent).toContain('USD');
  });
});
