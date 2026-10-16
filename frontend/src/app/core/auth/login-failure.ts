import { HttpErrorResponse } from '@angular/common/http';

export type LoginFailure =
  | { kind: 'invalid-credentials' }
  | { kind: 'too-many-attempts'; retryAfterSeconds: number | null }
  | { kind: 'network-error' }
  | { kind: 'timeout' }
  | { kind: 'unexpected' };

const UNAUTHORIZED = 401;
const TOO_MANY_REQUESTS = 429;
const NO_RESPONSE = 0;
const REQUEST_TIMEOUT = 408;
const GATEWAY_TIMEOUT = 504;

export function toLoginFailure(error: HttpErrorResponse): LoginFailure {
  switch (error.status) {
    case UNAUTHORIZED:
      return { kind: 'invalid-credentials' };
    case TOO_MANY_REQUESTS:
      return { kind: 'too-many-attempts', retryAfterSeconds: readRetryAfterSeconds(error) };
    case NO_RESPONSE:
      return { kind: 'network-error' };
    case REQUEST_TIMEOUT:
    case GATEWAY_TIMEOUT:
      return { kind: 'timeout' };
    default:
      return { kind: 'unexpected' };
  }
}

function readRetryAfterSeconds(error: HttpErrorResponse): number | null {
  const seconds = Number(error.headers.get('Retry-After'));
  return Number.isInteger(seconds) && seconds > 0 ? seconds : null;
}
