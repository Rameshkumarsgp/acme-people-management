import { InjectionToken } from '@angular/core';
import { environment } from '../../../environments/environment';

export const API_TIMEOUT_IN_MS = new InjectionToken<number>('API_TIMEOUT_IN_MS', {
  providedIn: 'root',
  factory: () => environment.apiTimeoutInMs,
});
