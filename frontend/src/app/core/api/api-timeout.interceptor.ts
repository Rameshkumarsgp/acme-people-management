import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { throwError, timeout } from 'rxjs';
import { API_BASE_URL } from './api-base-url';
import { API_TIMEOUT_IN_MS } from './api-timeout-in-ms';
import { isApiCall } from './is-api-call';

const REQUEST_TIMEOUT = 408;

/**
 * Gives up on an API call that gets no answer in time. Unsubscribing cancels the request. The failure
 * is a 408, so callers handle it like any other HTTP error, next to the 504 a proxy would send.
 */
export const apiTimeoutInterceptor: HttpInterceptorFn = (request, next) => {
  const apiBaseUrl = inject(API_BASE_URL);
  const timeoutInMs = inject(API_TIMEOUT_IN_MS);

  if (!isApiCall(request.url, apiBaseUrl)) {
    return next(request);
  }
  return next(request).pipe(
    timeout({
      each: timeoutInMs,
      with: () =>
        throwError(
          () =>
            new HttpErrorResponse({
              url: request.url,
              status: REQUEST_TIMEOUT,
              statusText: 'Request Timeout',
            }),
        ),
    }),
  );
};
