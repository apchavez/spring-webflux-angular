import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let router: Router;

  const authServiceMock = { token: vi.fn(), logout: vi.fn() };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: authServiceMock }
      ]
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => httpMock.verify());

  it('should attach the Authorization header when a token exists', () => {
    authServiceMock.token.mockReturnValue('the-token');

    http.get('/api/v1/products/active').subscribe();

    const req = httpMock.expectOne('/api/v1/products/active');
    expect(req.request.headers.get('Authorization')).toBe('Bearer the-token');
    req.flush({});
  });

  it('should not attach a header when there is no token', () => {
    authServiceMock.token.mockReturnValue(null);

    http.get('/api/v1/products/active').subscribe();

    const req = httpMock.expectOne('/api/v1/products/active');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('should log out and redirect to /login on a 401 from a protected endpoint', () => {
    authServiceMock.token.mockReturnValue('stale-token');
    const navigateSpy = vi.spyOn(router, 'navigate');

    http.get('/api/v1/products/active').subscribe({ error: () => {} });

    httpMock.expectOne('/api/v1/products/active').flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(authServiceMock.logout).toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });

  it('should NOT redirect on a 401 from the login endpoint itself', () => {
    authServiceMock.token.mockReturnValue(null);
    const navigateSpy = vi.spyOn(router, 'navigate');

    http.post('/api/v1/auth/login', {}).subscribe({ error: () => {} });

    httpMock.expectOne('/api/v1/auth/login').flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(authServiceMock.logout).not.toHaveBeenCalled();
    expect(navigateSpy).not.toHaveBeenCalled();
  });
});
