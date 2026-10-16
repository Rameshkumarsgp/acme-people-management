import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { API_BASE_URL } from '../api/api-base-url';
import { AuthService } from './auth.service';

const UNAUTHORIZED = 401;

/**
 * Adds the shared login to API calls. A request that already carries its own Authorization header,
 * such as the login attempt itself, is left alone. A 401 on a call this interceptor authenticated
 * means the session is no longer valid, so it ends and the user returns to the login page.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const apiBaseUrl = inject(API_BASE_URL);

  const authorization = auth.authorizationHeader();
  const addsLogin =
    authorization !== null &&
    isApiCall(request.url, apiBaseUrl) &&
    !request.headers.has('Authorization');
  const outgoing = addsLogin
    ? request.clone({ setHeaders: { Authorization: authorization } })
    : request;

  return next(outgoing).pipe(
    catchError((error: unknown) => {
      if (addsLogin && error instanceof HttpErrorResponse && error.status === UNAUTHORIZED) {
        auth.logout();
        void router.navigateByUrl('/login');
      }
      return throwError(() => error);
    }),
  );
};

function isApiCall(url: string, apiBaseUrl: string): boolean {
  return url === apiBaseUrl || url.startsWith(`${apiBaseUrl}/`);
}
