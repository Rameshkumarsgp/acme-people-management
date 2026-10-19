import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ReferenceApi } from './reference-api';

describe('ReferenceApi', () => {
  let api: ReferenceApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ReferenceApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the four lists the forms need', async () => {
    const loading = api.loadLists();
    http.expectOne('/api/reference/countries').flush([{ code: 'IN', name: 'India' }]);
    http.expectOne('/api/reference/departments').flush([{ id: 1, name: 'Engineering' }]);
    http.expectOne('/api/reference/job-titles').flush([{ id: 2, name: 'Software Engineer' }]);
    http.expectOne('/api/reference/currencies').flush([{ code: 'INR', minorUnitDigits: 2 }]);

    expect(await loading).toEqual({
      countries: [{ code: 'IN', name: 'India' }],
      departments: [{ id: 1, name: 'Engineering' }],
      jobTitles: [{ id: 2, name: 'Software Engineer' }],
      currencies: [{ code: 'INR', minorUnitDigits: 2 }],
    });
  });

  it('loads only the currencies for a page that just shows money', async () => {
    const loading = api.loadCurrencies();
    http.expectOne('/api/reference/currencies').flush([{ code: 'JPY', minorUnitDigits: 0 }]);

    expect(await loading).toEqual([{ code: 'JPY', minorUnitDigits: 0 }]);
  });
});
