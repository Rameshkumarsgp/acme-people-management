import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ExtremesRequest,
  ExtremesResponse,
  InsightsApi,
  InsightsRequest,
  InsightsResponse,
} from './insights-api';

const NO_FILTERS = {
  countryCode: '',
  departmentId: null,
  jobTitleId: null,
  currency: '',
  status: 'ACTIVE' as const,
};

describe('InsightsApi', () => {
  let api: InsightsApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(InsightsApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks for a dimension and the status, and leaves out the filters that are not set', async () => {
    const request: InsightsRequest = { ...NO_FILTERS, dimension: 'COUNTRY' };
    const response: InsightsResponse = { dimension: 'COUNTRY', groups: [] };

    const result = api.insights(request);
    const call = http.expectOne((candidate) => candidate.url === '/api/insights');
    call.flush(response);

    expect(call.request.method).toBe('GET');
    expect(call.request.params.keys().sort()).toEqual(['dimension', 'status']);
    expect(call.request.params.get('dimension')).toBe('COUNTRY');
    expect(call.request.params.get('status')).toBe('ACTIVE');
    expect(await result).toEqual(response);
  });

  it('sends every filter that is set', async () => {
    const result = api.insights({
      dimension: 'TENURE',
      countryCode: 'IN',
      departmentId: 3,
      jobTitleId: 12,
      currency: 'INR',
      status: 'ALL',
    });
    const call = http.expectOne((candidate) => candidate.url === '/api/insights');
    call.flush({ dimension: 'TENURE', groups: [] });
    await result;

    expect(call.request.params.get('countryCode')).toBe('IN');
    expect(call.request.params.get('departmentId')).toBe('3');
    expect(call.request.params.get('jobTitleId')).toBe('12');
    expect(call.request.params.get('currency')).toBe('INR');
    expect(call.request.params.get('status')).toBe('ALL');
  });

  it('keeps the total of a group as the text the server sent', async () => {
    const result = api.insights({ ...NO_FILTERS, dimension: 'OVERALL' });
    http
      .expectOne((candidate) => candidate.url === '/api/insights')
      .flush({
        dimension: 'OVERALL',
        groups: [
          {
            key: 'ALL',
            label: 'All employees',
            currency: 'INR',
            headcount: 2,
            total: '18014398509481983',
            min: 1,
            median: 1,
            mean: 1,
            max: 1,
          },
        ],
      });

    expect((await result).groups[0].total).toBe('18014398509481983');
  });

  it('asks for the extremes with the order, the limit and the currency filter', async () => {
    const request: ExtremesRequest = {
      ...NO_FILTERS,
      currency: 'INR',
      order: 'LOWEST',
      limit: 5,
    };
    const response: ExtremesResponse = { order: 'LOWEST', currency: 'INR', employees: [] };

    const result = api.extremes(request);
    const call = http.expectOne((candidate) => candidate.url === '/api/insights/extremes');
    call.flush(response);

    expect(call.request.params.get('order')).toBe('LOWEST');
    expect(call.request.params.get('limit')).toBe('5');
    expect(call.request.params.get('currency')).toBe('INR');
    expect(call.request.params.get('status')).toBe('ACTIVE');
    expect(await result).toEqual(response);
  });
});
