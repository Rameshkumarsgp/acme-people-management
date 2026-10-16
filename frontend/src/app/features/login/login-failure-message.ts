import { LoginFailure } from '../../core/auth/login-failure';

/*
 * The text is hard-coded in English for now. For localization it should come from one central place,
 * for example Angular's i18n (the i18n attribute and $localize) with translation files, so that no
 * screen owns its own copy of a sentence.
 */

export function describeLoginFailure(failure: LoginFailure): string {
  switch (failure.kind) {
    case 'invalid-credentials':
      return 'Invalid username or password.';
    case 'too-many-attempts':
      return `Too many failed attempts. Try again in ${describeWait(failure.retryAfterSeconds)}.`;
    case 'network-error':
      return 'Cannot reach the server. Check your connection and try again.';
    case 'timeout':
      return 'The server took too long to respond. Try again.';
    case 'unexpected':
      return 'Something went wrong. Try again.';
  }
}

function describeWait(seconds: number | null): string {
  if (seconds === null) {
    return 'a minute';
  }
  return seconds === 1 ? '1 second' : `${seconds} seconds`;
}
