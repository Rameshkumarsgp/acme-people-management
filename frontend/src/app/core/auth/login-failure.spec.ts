import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { toLoginFailure } from './login-failure';

describe('toLoginFailure', () => {
  it('treats a 401 as invalid credentials', () => {
    expect(toLoginFailure(new HttpErrorResponse({ status: 401 }))).toEqual({
      kind: 'invalid-credentials',
    });
  });

  it('reads the wait time of a 429 from the Retry-After header', () => {
    const response = new HttpErrorResponse({
      status: 429,
      headers: new HttpHeaders({ 'Retry-After': '42' }),
    });

    expect(toLoginFailure(response)).toEqual({ kind: 'too-many-attempts', retryAfterSeconds: 42 });
  });

  it('has no wait time for a 429 without a Retry-After header', () => {
    expect(toLoginFailure(new HttpErrorResponse({ status: 429 }))).toEqual({
      kind: 'too-many-attempts',
      retryAfterSeconds: null,
    });
  });

  it('has no wait time for a 429 whose Retry-After is not a number', () => {
    const response = new HttpErrorResponse({
      status: 429,
      headers: new HttpHeaders({ 'Retry-After': 'soon' }),
    });

    expect(toLoginFailure(response)).toEqual({
      kind: 'too-many-attempts',
      retryAfterSeconds: null,
    });
  });

  it('treats status 0 as a network error', () => {
    expect(toLoginFailure(new HttpErrorResponse({ status: 0 }))).toEqual({ kind: 'network-error' });
  });

  it('treats any other status as unexpected', () => {
    expect(toLoginFailure(new HttpErrorResponse({ status: 500 }))).toEqual({ kind: 'unexpected' });
  });
});
