import { HttpErrorResponse } from '@angular/common/http';

const CONFLICT = 409;

/** Whether a failed save or delete means someone else changed the employee since it was opened. */
export function isVersionConflict(error: HttpErrorResponse): boolean {
  const body: unknown = error.error;
  return (
    error.status === CONFLICT &&
    typeof body === 'object' &&
    body !== null &&
    (body as { code?: unknown }).code === 'VERSION_CONFLICT'
  );
}
