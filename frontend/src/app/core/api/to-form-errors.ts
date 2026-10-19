import { HttpErrorResponse } from '@angular/common/http';

export interface FormErrors {
  fieldErrors: Record<string, string>;
  formError: string | null;
}

interface ProblemBody {
  detail?: string;
  code?: string;
  errors?: { field: string; message: string }[];
}

const EMAIL_ALREADY_EXISTS = 'EMAIL_ALREADY_EXISTS';
const GENERIC_MESSAGE = 'Could not save. Check your connection and try again.';

/**
 * Decides where a failed save is shown. Validation messages go under their field, a duplicate email
 * goes under the email field, and a broken business rule goes in a banner with the server's wording.
 * Anything without a stable shape (server error, timeout, no answer) gets one generic message, so
 * server internals are never shown.
 */
export function toFormErrors(error: HttpErrorResponse): FormErrors {
  const problem = readProblem(error.error);
  if (problem?.errors?.length) {
    return { fieldErrors: firstMessagePerField(problem.errors), formError: null };
  }
  if (problem?.code === EMAIL_ALREADY_EXISTS && problem.detail) {
    return { fieldErrors: { email: problem.detail }, formError: null };
  }
  if (problem?.code && problem.detail) {
    return { fieldErrors: {}, formError: problem.detail };
  }
  return { fieldErrors: {}, formError: GENERIC_MESSAGE };
}

function readProblem(body: unknown): ProblemBody | null {
  return typeof body === 'object' && body !== null ? (body as ProblemBody) : null;
}

function firstMessagePerField(
  errors: { field: string; message: string }[],
): Record<string, string> {
  const messages: Record<string, string> = {};
  for (const { field, message } of errors) {
    messages[field] ??= message;
  }
  return messages;
}
