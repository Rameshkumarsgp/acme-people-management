import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CreateEmployeeRequest, EmployeeApi } from './employee-api';

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
});
