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

  it('ends the session and returns to the login page on sign out', () => {
    page.querySelector<HTMLButtonElement>('button')!.click();

    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/login');
  });
});
