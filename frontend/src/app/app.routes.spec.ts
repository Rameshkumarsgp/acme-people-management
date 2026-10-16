import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';

describe('routes', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  async function logIn(): Promise<void> {
    const login = TestBed.inject(AuthService).login('hr', 's3cret');
    TestBed.inject(HttpTestingController).expectOne('/api/auth/me').flush({ username: 'hr' });
    await login;
  }

  it('sends a visitor who is not logged in from the home page to the login page', async () => {
    await RouterTestingHarness.create('/');

    expect(TestBed.inject(Router).url).toBe('/login');
  });

  it('shows the sign-in form on the login page', async () => {
    const harness = await RouterTestingHarness.create('/login');

    expect(harness.routeNativeElement?.textContent).toContain('Sign in');
  });

  it('sends an unknown address to the login page when logged out', async () => {
    await RouterTestingHarness.create('/nowhere');

    expect(TestBed.inject(Router).url).toBe('/login');
  });

  it('lets a logged-in user reach the home page', async () => {
    await logIn();

    const harness = await RouterTestingHarness.create('/');

    expect(TestBed.inject(Router).url).toBe('/');
    expect(harness.routeNativeElement?.textContent).toContain('Welcome, hr');
  });
});
