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
        {
          provide: MAT_DIALOG_DATA,
          useValue: { title: 'Delete Asha Rao?', message: 'This cannot be undone.' },
        },
        { provide: MatDialogRef, useValue: { close } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ConfirmDeleteDialog);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  it('shows the title and the message it was given', () => {
    expect(page.querySelector('h2')?.textContent).toContain('Delete Asha Rao?');
    expect(page.textContent).toContain('This cannot be undone.');
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
