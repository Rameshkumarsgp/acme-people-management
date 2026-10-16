import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { encodeBasicAuthorization } from './basic-authorization';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts without a session', () => {
    expect(service.isAuthenticated()).toBe(false);
    expect(service.username()).toBeNull();
    expect(service.authorizationHeader()).toBeNull();
  });

  it('checks the login with GET /api/auth/me and a Basic header', () => {
    void service.login('hr', 's3cret');

    const request = http.expectOne('/api/auth/me');
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.get('Authorization')).toBe(
      encodeBasicAuthorization('hr', 's3cret'),
    );
    request.flush({ username: 'hr' });
  });

  it('starts a session when the server accepts the login', async () => {
    const result = service.login('hr', 's3cret');
    http.expectOne('/api/auth/me').flush({ username: 'hr' });

    expect(await result).toEqual({ ok: true });
    expect(service.isAuthenticated()).toBe(true);
    expect(service.username()).toBe('hr');
    expect(service.authorizationHeader()).toBe(encodeBasicAuthorization('hr', 's3cret'));
  });

  it('stays logged out and explains why when the password is wrong', async () => {
    const result = service.login('hr', 'wrong');
    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(await result).toEqual({ ok: false, failure: { kind: 'invalid-credentials' } });
    expect(service.isAuthenticated()).toBe(false);
  });

  it('reports the wait time when the server says too many attempts', async () => {
    const result = service.login('hr', 'wrong');
    http.expectOne('/api/auth/me').flush(null, {
      status: 429,
      statusText: 'Too Many Requests',
      headers: { 'Retry-After': '60' },
    });

    expect(await result).toEqual({
      ok: false,
      failure: { kind: 'too-many-attempts', retryAfterSeconds: 60 },
    });
  });

  it('reports a network error when the server cannot be reached', async () => {
    const result = service.login('hr', 's3cret');
    http.expectOne('/api/auth/me').error(new ProgressEvent('error'));

    expect(await result).toEqual({ ok: false, failure: { kind: 'network-error' } });
  });

  it('ends the session on logout', async () => {
    const result = service.login('hr', 's3cret');
    http.expectOne('/api/auth/me').flush({ username: 'hr' });
    await result;

    service.logout();

    expect(service.isAuthenticated()).toBe(false);
    expect(service.authorizationHeader()).toBeNull();
  });
});
