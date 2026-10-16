import { describeLoginFailure } from './login-failure-message';

describe('describeLoginFailure', () => {
  it('tells the user the username or password is wrong', () => {
    expect(describeLoginFailure({ kind: 'invalid-credentials' })).toBe(
      'Invalid username or password.',
    );
  });

  it('gives the wait time when the server provided one', () => {
    expect(describeLoginFailure({ kind: 'too-many-attempts', retryAfterSeconds: 42 })).toBe(
      'Too many failed attempts. Try again in 42 seconds.',
    );
  });

  it('uses the singular for a wait of one second', () => {
    expect(describeLoginFailure({ kind: 'too-many-attempts', retryAfterSeconds: 1 })).toBe(
      'Too many failed attempts. Try again in 1 second.',
    );
  });

  it('falls back to a minute when the server gave no wait time', () => {
    expect(describeLoginFailure({ kind: 'too-many-attempts', retryAfterSeconds: null })).toBe(
      'Too many failed attempts. Try again in a minute.',
    );
  });

  it('tells the user the server could not be reached', () => {
    expect(describeLoginFailure({ kind: 'network-error' })).toBe(
      'Cannot reach the server. Check your connection and try again.',
    );
  });

  it('tells the user the server took too long', () => {
    expect(describeLoginFailure({ kind: 'timeout' })).toBe(
      'The server took too long to respond. Try again.',
    );
  });

  it('gives a generic message for an unexpected failure', () => {
    expect(describeLoginFailure({ kind: 'unexpected' })).toBe('Something went wrong. Try again.');
  });
});
