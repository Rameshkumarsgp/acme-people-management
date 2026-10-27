import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';

export interface ConfirmDeleteDialogData {
  title: string;
  message: string;
}

@Component({
  selector: 'app-confirm-delete-dialog',
  imports: [MatButtonModule, MatDialogModule],
  templateUrl: './confirm-delete-dialog.html',
})
export class ConfirmDeleteDialog {
  private readonly dialogRef = inject(MatDialogRef<ConfirmDeleteDialog, boolean>);

  protected readonly data = inject<ConfirmDeleteDialogData>(MAT_DIALOG_DATA);

  protected answer(confirmed: boolean): void {
    this.dialogRef.close(confirmed);
  }
}
