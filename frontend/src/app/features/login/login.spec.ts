import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let page: HTMLElement;
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(Login);
    page = fixture.nativeElement as HTMLElement;
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function type(selector: string, value: string): void {
    const input = page.querySelector<HTMLInputElement>(selector);
    input!.value = value;
    input!.dispatchEvent(new Event('input'));
  }

  function submit(username: string, password: string): void {
    type('input[formControlName="username"]', username);
    type('input[formControlName="password"]', password);
    page.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
  }

  function alertText(): string | undefined {
    return page.querySelector('[role="alert"]')?.textContent?.trim();
  }

  it('shows a username field, a password field and a sign-in button', () => {
    expect(page.querySelector('input[formControlName="username"]')).not.toBeNull();
    expect(page.querySelector('input[formControlName="password"][type="password"]')).not.toBeNull();
    expect(page.querySelector('button[type="submit"]')?.textContent).toContain('Sign in');
  });

  it('does not call the server while a field is empty and says what is missing', () => {
    submit('', '');
    fixture.detectChanges();

    http.expectNone('/api/auth/me');
    expect(page.textContent).toContain('Enter your username.');
    expect(page.textContent).toContain('Enter your password.');
  });

  it('goes to the app after the server accepts the login', async () => {
    submit('hr', 's3cret');
    http.expectOne('/api/auth/me').flush({ username: 'hr' });
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith('/');
  });

  it('says the username or password is wrong when the server answers 401', async () => {
    submit('hr', 'wrong');
    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(alertText()).toBe('Invalid username or password.');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('shows how long to wait when the server answers 429', async () => {
    submit('hr', 'wrong');
    http.expectOne('/api/auth/me').flush(null, {
      status: 429,
      statusText: 'Too Many Requests',
      headers: { 'Retry-After': '30' },
    });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(alertText()).toBe('Too many failed attempts. Try again in 30 seconds.');
  });

  it('says the server cannot be reached when the network fails', async () => {
    submit('hr', 's3cret');
    http.expectOne('/api/auth/me').error(new ProgressEvent('error'));
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(alertText()).toBe('Cannot reach the server. Check your connection and try again.');
    });
  });

  it('clears the previous error as soon as the user tries again', async () => {
    submit('hr', 'wrong');
    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(alertText()).toBe('Invalid username or password.');
    });

    submit('hr', 'wrong-again');
    fixture.detectChanges();

    expect(alertText()).toBeUndefined();
    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
  });

  it('signs in when the form is submitted, which is what the Enter key does', async () => {
    type('input[formControlName="username"]', 'hr');
    type('input[formControlName="password"]', 's3cret');

    page.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));

    http.expectOne('/api/auth/me').flush({ username: 'hr' });
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/'));
  });

  it('disables the button while signing in and enables it again afterwards', async () => {
    const button = page.querySelector<HTMLButtonElement>('button[type="submit"]')!;

    submit('hr', 'wrong');
    fixture.detectChanges();
    expect(button.disabled).toBe(true);
    expect(button.textContent).toContain('Signing in');

    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(button.disabled).toBe(false);
    });
  });
});
