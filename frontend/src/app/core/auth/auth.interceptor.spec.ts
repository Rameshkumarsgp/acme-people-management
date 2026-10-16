import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';
import { encodeBasicAuthorization } from './basic-authorization';

describe('authInterceptor', () => {
  let auth: AuthService;
  let client: HttpClient;
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    auth = TestBed.inject(AuthService);
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  async function logIn(): Promise<void> {
    const result = auth.login('hr', 's3cret');
    http.expectOne('/api/auth/me').flush({ username: 'hr' });
    await result;
  }

  it('adds the Authorization header to API calls once logged in', async () => {
    await logIn();

    client.get('/api/employees').subscribe();

    const request = http.expectOne('/api/employees');
    expect(request.request.headers.get('Authorization')).toBe(
      encodeBasicAuthorization('hr', 's3cret'),
    );
  });

  it('sends no header when nobody is logged in', () => {
    client.get('/api/employees').subscribe({ error: () => undefined });

    const request = http.expectOne('/api/employees');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(null);
  });

  it('leaves calls outside the API alone', async () => {
    await logIn();

    client.get('/assets/logo.svg').subscribe();

    expect(http.expectOne('/assets/logo.svg').request.headers.has('Authorization')).toBe(false);
  });

  it('keeps an Authorization header that the caller set itself', async () => {
    await logIn();

    client.get('/api/auth/me', { headers: { Authorization: 'Basic other' } }).subscribe();

    expect(http.expectOne('/api/auth/me').request.headers.get('Authorization')).toBe('Basic other');
  });

  it('ends the session and returns to the login page when the server answers 401', async () => {
    await logIn();

    client.get('/api/employees').subscribe({ error: () => undefined });
    http.expectOne('/api/employees').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/login');
  });

  it('keeps the session when a login attempt with other credentials is rejected', async () => {
    await logIn();

    const attempt = auth.login('hr', 'wrong');
    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await attempt;

    expect(auth.isAuthenticated()).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('keeps the session when the server fails with another error', async () => {
    await logIn();

    client.get('/api/employees').subscribe({ error: () => undefined });
    http.expectOne('/api/employees').flush(null, { status: 500, statusText: 'Server Error' });

    expect(auth.isAuthenticated()).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });
});
