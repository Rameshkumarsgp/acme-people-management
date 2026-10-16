import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { appConfig } from './app.config';
import { API_TIMEOUT_IN_MS } from './core/api/api-timeout-in-ms';
import { AuthService } from './core/auth/auth.service';

const TIMEOUT_IN_MS = 1000;

/** Checks that the interceptors are wired into the real application configuration. */
describe('appConfig', () => {
  let client: HttpClient;
  let http: HttpTestingController;

  beforeEach(async () => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [
        ...appConfig.providers,
        provideHttpClientTesting(),
        { provide: API_TIMEOUT_IN_MS, useValue: TIMEOUT_IN_MS },
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);

    const login = TestBed.inject(AuthService).login('hr', 's3cret');
    http.expectOne('/api/auth/me').flush({ username: 'hr' });
    await login;
  });

  afterEach(() => vi.useRealTimers());

  it('adds the login to API calls', () => {
    client.get('/api/employees').subscribe();

    expect(http.expectOne('/api/employees').request.headers.has('Authorization')).toBe(true);
  });

  it('gives up on an API call that gets no answer in time', () => {
    let error: HttpErrorResponse | undefined;
    client
      .get('/api/employees')
      .subscribe({ error: (failure: HttpErrorResponse) => (error = failure) });

    vi.advanceTimersByTime(TIMEOUT_IN_MS);

    expect(error?.status).toBe(408);
  });
});
