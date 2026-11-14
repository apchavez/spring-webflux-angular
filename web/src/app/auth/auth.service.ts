import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { LoginRequest, LoginResponse } from './auth.model';

const STORAGE_KEY = 'auth';

interface StoredAuth {
  token: string;
  username: string;
  roles: string[];
}

function readStoredAuth(): StoredAuth | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as StoredAuth;
  } catch {
    return null;
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/api/v1/auth`;

  private readonly stored = signal<StoredAuth | null>(readStoredAuth());

  readonly token = computed(() => this.stored()?.token ?? null);
  readonly username = computed(() => this.stored()?.username ?? null);
  readonly roles = computed(() => this.stored()?.roles ?? []);
  readonly isAuthenticated = computed(() => this.stored() !== null);

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/login`, credentials).pipe(
      tap(response => {
        const auth: StoredAuth = { token: response.token, username: response.username, roles: response.roles };
        localStorage.setItem(STORAGE_KEY, JSON.stringify(auth));
        this.stored.set(auth);
      })
    );
  }

  logout(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.stored.set(null);
  }
}
