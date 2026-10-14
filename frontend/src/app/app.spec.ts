import { TestBed } from '@angular/core/testing';
import { App } from './app';

describe('App', () => {
  it('shows the product name and tagline in the toolbar', async () => {
    // given
    await TestBed.configureTestingModule({ imports: [App] }).compileComponents();

    // when
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    // then
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('h1')?.textContent).toContain('ACME People Management');
    expect(page.querySelector('.tagline')?.textContent).toContain('See how ACME pays people.');
  });
});
