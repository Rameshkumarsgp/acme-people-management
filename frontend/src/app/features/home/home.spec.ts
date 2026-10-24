import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Home } from './home';

describe('Home', () => {
  let fixture: ComponentFixture<Home>;
  let page: HTMLElement;
  let auth: AuthService;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Home],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    auth = TestBed.inject(AuthService);
    const login = auth.login('hr', 's3cret');
    TestBed.inject(HttpTestingController).expectOne('/api/auth/me').flush({ username: 'hr' });
    await login;

    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(Home);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  it('greets the signed-in user by name', () => {
    expect(page.textContent).toContain('Welcome, hr');
  });

  it('links to the new employee form', () => {
    const link = page.querySelector<HTMLAnchorElement>('a[href="/employees/new"]');

    expect(link?.textContent).toContain('New employee');
  });

  it('links to the employee directory', () => {
    const link = page.querySelector<HTMLAnchorElement>('a[href="/employees"]');

    expect(link?.textContent).toContain('Employee directory');
  });

  it('says only the insights are still to come, because the directory is here', () => {
    expect(page.textContent).toContain('The salary insights will appear here.');
    expect(page.textContent).not.toContain('The employee directory and');
  });

  it('ends the session and returns to the login page on sign out', () => {
    page.querySelector<HTMLButtonElement>('button')!.click();

    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/login');
  });
});
