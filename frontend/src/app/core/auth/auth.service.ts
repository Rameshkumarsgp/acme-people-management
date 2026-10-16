import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL } from '../api/api-base-url';
import { encodeBasicAuthorization } from './basic-authorization';
import { LoginFailure, toLoginFailure } from './login-failure';

export type LoginResult = { ok: true } | { ok: false; failure: LoginFailure };

interface CurrentUserResponse {
  username: string;
}

interface Session {
  username: string;
  authorization: string;
}

/** Holds the shared login in memory only. A page refresh ends the session. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly session = signal<Session | null>(null);

  readonly username = computed(() => this.session()?.username ?? null);
  readonly isAuthenticated = computed(() => this.session() !== null);

  async login(username: string, password: string): Promise<LoginResult> {
    const authorization = encodeBasicAuthorization(username, password);
    try {
      const user = await firstValueFrom(
        this.http.get<CurrentUserResponse>(`${this.apiBaseUrl}/auth/me`, {
          headers: { Authorization: authorization },
        }),
      );
      this.session.set({ username: user.username, authorization });
      return { ok: true };
    } catch (error) {
      if (error instanceof HttpErrorResponse) {
        return { ok: false, failure: toLoginFailure(error) };
      }
      throw error;
    }
  }

  logout(): void {
    this.session.set(null);
  }

  authorizationHeader(): string | null {
    return this.session()?.authorization ?? null;
  }
}
