import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ConfirmDeleteDialog } from './confirm-delete-dialog';

describe('ConfirmDeleteDialog', () => {
  let fixture: ComponentFixture<ConfirmDeleteDialog>;
  let page: HTMLElement;
  const close = vi.fn();

  beforeEach(async () => {
    close.mockReset();
    await TestBed.configureTestingModule({
      imports: [ConfirmDeleteDialog],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { fullName: 'Asha Rao' } },
        { provide: MatDialogRef, useValue: { close } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ConfirmDeleteDialog);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  it('names the person and says the history goes with them and it cannot be undone', () => {
    const text = page.textContent ?? '';

    expect(text).toContain('Delete Asha Rao?');
    expect(text).toContain('salary history');
    expect(text).toContain('cannot be undone');
  });

  it('closes with true when Delete is chosen', () => {
    page.querySelector<HTMLButtonElement>('button[data-test="confirm-delete"]')!.click();

    expect(close).toHaveBeenCalledWith(true);
  });

  it('closes with false when Cancel is chosen', () => {
    page.querySelector<HTMLButtonElement>('button[data-test="cancel-delete"]')!.click();

    expect(close).toHaveBeenCalledWith(false);
  });
});
