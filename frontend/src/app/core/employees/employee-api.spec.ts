import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  AddSalaryEntryRequest,
  CreateEmployeeRequest,
  DEFAULT_DIRECTORY_QUERY,
  DirectoryPage,
  DirectoryQuery,
  EmployeeApi,
  SalaryEntry,
  UpdateEmployeeRequest,
} from './employee-api';

describe('EmployeeApi', () => {
  let api: EmployeeApi;
  let http: HttpTestingController;

  const request: CreateEmployeeRequest = {
    fullName: 'Asha Rao',
    email: 'asha.rao@acme.example',
    hireDate: '2024-04-01',
    departmentId: 3,
    jobTitleId: 12,
    countryCode: 'IN',
    initialSalary: { amountMinor: 125000000, currency: 'INR' },
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(EmployeeApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts the new employee and returns the created detail', async () => {
    const creating = api.create(request);
    const call = http.expectOne('/api/employees');
    expect(call.request.method).toBe('POST');
    expect(call.request.body).toEqual(request);
    call.flush({ id: 10432 });

    expect((await creating).id).toBe(10432);
  });

  it('gets an employee by id', async () => {
    const reading = api.get(10432);
    http.expectOne('/api/employees/10432').flush({ id: 10432 });

    expect((await reading).id).toBe(10432);
  });

  it('lists the salary entries of an employee', async () => {
    const entry: SalaryEntry = {
      id: 5,
      effectiveDate: '2026-04-01',
      amountMinor: 150000000,
      currency: 'INR',
      endDate: null,
      current: true,
      scheduled: false,
    };
    const listing = api.listSalaryEntries(10432);
    http.expectOne('/api/employees/10432/salary-entries').flush([entry]);

    expect(await listing).toEqual([entry]);
  });

  it('posts a new salary entry for an employee and returns it', async () => {
    const request: AddSalaryEntryRequest = {
      effectiveDate: '2026-04-01',
      amountMinor: 150000000,
      currency: 'INR',
    };
    const adding = api.addSalaryEntry(10432, request);
    const call = http.expectOne('/api/employees/10432/salary-entries');
    expect(call.request.method).toBe('POST');
    expect(call.request.body).toEqual(request);
    call.flush({ id: 5 });

    expect((await adding).id).toBe(5);
  });

  describe('listEmployees', () => {
    const page: DirectoryPage = {
      content: [],
      page: 2,
      size: 50,
      totalElements: 120,
      totalPages: 3,
    };

    it('sends the filters, sort and page as query parameters and returns the page', async () => {
      const query: DirectoryQuery = {
        q: 'rao',
        countryCode: 'IN',
        departmentId: 3,
        jobTitleId: 12,
        currency: 'INR',
        status: 'ALL',
        sort: 'salary,desc',
        page: 2,
        size: 50,
      };
      const listing = api.listEmployees(query);
      const call = http.expectOne((request) => request.url === '/api/employees');
      call.flush(page);

      expect(call.request.method).toBe('GET');
      expect(call.request.params.get('q')).toBe('rao');
      expect(call.request.params.get('countryCode')).toBe('IN');
      expect(call.request.params.get('departmentId')).toBe('3');
      expect(call.request.params.get('jobTitleId')).toBe('12');
      expect(call.request.params.get('currency')).toBe('INR');
      expect(call.request.params.get('status')).toBe('ALL');
      expect(call.request.params.get('sort')).toBe('salary,desc');
      expect(call.request.params.get('page')).toBe('2');
      expect(call.request.params.get('size')).toBe('50');
      expect(await listing).toEqual(page);
    });

    it('leaves out the filters that are empty and always sends status, sort, page and size', async () => {
      const listing = api.listEmployees(DEFAULT_DIRECTORY_QUERY);
      const call = http.expectOne((request) => request.url === '/api/employees');
      call.flush(page);
      await listing;

      expect(call.request.params.keys().sort()).toEqual(['page', 'size', 'sort', 'status']);
      expect(call.request.params.get('status')).toBe('ACTIVE');
      expect(call.request.params.get('sort')).toBe('fullName,asc');
      expect(call.request.params.get('page')).toBe('0');
      expect(call.request.params.get('size')).toBe('20');
    });
  });

  describe('update and delete', () => {
    it('puts the whole profile with its version and returns the updated employee', async () => {
      const request: UpdateEmployeeRequest = {
        fullName: 'Asha Rao',
        email: 'asha.rao@acme.example',
        hireDate: '2024-04-01',
        terminationDate: null,
        departmentId: 3,
        jobTitleId: 12,
        countryCode: 'IN',
        version: 4,
      };
      const updating = api.update(10432, request);
      const call = http.expectOne('/api/employees/10432');
      expect(call.request.method).toBe('PUT');
      expect(call.request.body).toEqual(request);
      call.flush({ id: 10432, version: 5 });

      expect((await updating).version).toBe(5);
    });

    it('deletes an employee and sends the version as a query parameter', async () => {
      const deleting = api.delete(10432, 3);
      const call = http.expectOne((request) => request.url === '/api/employees/10432');
      expect(call.request.method).toBe('DELETE');
      expect(call.request.params.get('version')).toBe('3');
      call.flush(null, { status: 204, statusText: 'No Content' });

      await expect(deleting).resolves.toBeUndefined();
    });
  });
});
