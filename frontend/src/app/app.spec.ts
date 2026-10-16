import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  async function render(): Promise<HTMLElement> {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the product name in the toolbar', async () => {
    const page = await render();

    expect(page.querySelector('mat-toolbar h1')?.textContent?.trim()).toBe(
      'ACME People Management',
    );
  });

  it('shows no tagline next to the product name', async () => {
    const page = await render();

    expect(page.querySelector('.tagline')).toBeNull();
    expect(page.textContent).not.toContain('See how ACME pays people');
  });
});
