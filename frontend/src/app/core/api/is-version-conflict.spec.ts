import { HttpErrorResponse } from '@angular/common/http';
import { isVersionConflict } from './is-version-conflict';

const failure = (status: number, body: unknown) =>
  new HttpErrorResponse({ status, error: body, statusText: 'x' });

describe('isVersionConflict', () => {
  it('is true for a 409 that carries the version conflict code', () => {
    expect(isVersionConflict(failure(409, { code: 'VERSION_CONFLICT' }))).toBe(true);
  });

  it('is false for a 409 about something else, such as a duplicate email', () => {
    expect(isVersionConflict(failure(409, { code: 'EMAIL_ALREADY_EXISTS' }))).toBe(false);
  });

  it('is false when the code arrives with another status', () => {
    expect(isVersionConflict(failure(400, { code: 'VERSION_CONFLICT' }))).toBe(false);
  });

  it('is false when the body is not a problem document', () => {
    expect(isVersionConflict(failure(409, null))).toBe(false);
    expect(isVersionConflict(failure(409, 'conflict'))).toBe(false);
  });
});
