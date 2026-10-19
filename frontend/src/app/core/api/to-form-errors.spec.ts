import { HttpErrorResponse } from '@angular/common/http';
import { toFormErrors } from './to-form-errors';

function failure(status: number, body: unknown): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: body });
}

describe('toFormErrors', () => {
  it('puts each validation message under its field', () => {
    const errors = toFormErrors(
      failure(400, {
        detail: 'The request is invalid.',
        errors: [
          { field: 'fullName', message: 'must not be blank' },
          { field: 'initialSalary.amountMinor', message: 'must be greater than or equal to 1' },
        ],
      }),
    );

    expect(errors).toEqual({
      fieldErrors: {
        fullName: 'must not be blank',
        'initialSalary.amountMinor': 'must be greater than or equal to 1',
      },
      formError: null,
    });
  });

  it('keeps the first message when a field has several', () => {
    const errors = toFormErrors(
      failure(400, {
        errors: [
          { field: 'email', message: 'first' },
          { field: 'email', message: 'second' },
        ],
      }),
    );

    expect(errors.fieldErrors).toEqual({ email: 'first' });
  });

  it('puts a duplicate email under the email field', () => {
    const errors = toFormErrors(
      failure(409, {
        code: 'EMAIL_ALREADY_EXISTS',
        detail: 'An employee with this email already exists.',
      }),
    );

    expect(errors).toEqual({
      fieldErrors: { email: 'An employee with this email already exists.' },
      formError: null,
    });
  });

  it('shows the server wording for a broken business rule in the banner', () => {
    const errors = toFormErrors(
      failure(422, {
        code: 'NO_CURRENT_SALARY',
        detail: 'There must be a salary effective today.',
      }),
    );

    expect(errors).toEqual({
      fieldErrors: {},
      formError: 'There must be a salary effective today.',
    });
  });

  it.each([
    ['a server error', failure(500, { detail: 'Internal error text' })],
    ['no answer from the server', failure(0, null)],
    ['a timeout', failure(408, null)],
    ['a body that is not JSON', failure(502, '<html>Bad gateway</html>')],
  ])('shows a generic message for %s and never the server text', (_, error) => {
    expect(toFormErrors(error)).toEqual({
      fieldErrors: {},
      formError: 'Could not save. Check your connection and try again.',
    });
  });
});
