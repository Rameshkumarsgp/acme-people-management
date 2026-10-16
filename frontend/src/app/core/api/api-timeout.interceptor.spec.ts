import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_TIMEOUT_IN_MS } from './api-timeout-in-ms';
import { apiTimeoutInterceptor } from './api-timeout.interceptor';

const TIMEOUT_IN_MS = 1000;

describe('apiTimeoutInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiTimeoutInterceptor])),
        provideHttpClientTesting(),
        { provide: API_TIMEOUT_IN_MS, useValue: TIMEOUT_IN_MS },
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => vi.useRealTimers());

  function callAndCollect(url: string): { error?: HttpErrorResponse; body?: unknown } {
    const outcome: { error?: HttpErrorResponse; body?: unknown } = {};
    client.get(url).subscribe({
      next: (body) => (outcome.body = body),
      error: (error: HttpErrorResponse) => (outcome.error = error),
    });
    return outcome;
  }

  it('fails an API call that gets no answer in time with a 408', () => {
    const outcome = callAndCollect('/api/employees');

    vi.advanceTimersByTime(TIMEOUT_IN_MS);

    expect(outcome.error).toBeInstanceOf(HttpErrorResponse);
    expect(outcome.error?.status).toBe(408);
    http.expectOne('/api/employees');
  });

  it('cancels the request that timed out, so it stops using the connection', () => {
    callAndCollect('/api/employees');

    vi.advanceTimersByTime(TIMEOUT_IN_MS);

    expect(http.expectOne('/api/employees').cancelled).toBe(true);
  });

  it('lets an answer that arrives in time through', () => {
    const outcome = callAndCollect('/api/employees');

    vi.advanceTimersByTime(TIMEOUT_IN_MS - 1);
    http.expectOne('/api/employees').flush({ ok: true });
    vi.advanceTimersByTime(TIMEOUT_IN_MS * 5);

    expect(outcome.body).toEqual({ ok: true });
    expect(outcome.error).toBeUndefined();
  });

  it('does not time out calls outside the API', () => {
    const outcome = callAndCollect('/assets/logo.svg');

    vi.advanceTimersByTime(TIMEOUT_IN_MS * 5);

    expect(outcome.error).toBeUndefined();
    http.expectOne('/assets/logo.svg').flush('<svg/>');
  });
});
