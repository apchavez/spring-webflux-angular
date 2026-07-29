import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { LoginResponse } from './auth.model';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  const mockResponse: LoginResponse = {
    token: 'mock-jwt',
    tokenType: 'Bearer',
    expiresIn: 3600,
    username: 'admin',
    roles: ['ADMIN', 'USER']
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should start unauthenticated when localStorage is empty', () => {
    expect(service.isAuthenticated()).toBe(false);
    expect(service.token()).toBeNull();
  });

  it('should store the token and mark as authenticated after a successful login', () => {
    service.login({ username: 'admin', password: 'admin123' }).subscribe();

    const req = httpMock.expectOne(r => r.url.endsWith('/auth/login'));
    expect(req.request.method).toBe('POST');
    req.flush(mockResponse);

    expect(service.isAuthenticated()).toBe(true);
    expect(service.token()).toBe('mock-jwt');
    expect(service.username()).toBe('admin');
    expect(service.roles()).toEqual(['ADMIN', 'USER']);
    expect(JSON.parse(localStorage.getItem('auth')!).token).toBe('mock-jwt');
  });

  it('should clear the stored session on logout', () => {
    service.login({ username: 'admin', password: 'admin123' }).subscribe();
    httpMock.expectOne(r => r.url.endsWith('/auth/login')).flush(mockResponse);
    expect(service.isAuthenticated()).toBe(true);

    service.logout();

    expect(service.isAuthenticated()).toBe(false);
    expect(service.token()).toBeNull();
    expect(localStorage.getItem('auth')).toBeNull();
  });
});
