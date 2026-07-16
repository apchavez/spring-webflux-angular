import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { LoginComponent } from './login.component';
import { AuthService } from './auth.service';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let router: Router;

  const authServiceMock = {
    login: vi.fn()
  };

  beforeEach(async () => {
    vi.clearAllMocks();

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        provideAnimations(),
        { provide: AuthService, useValue: authServiceMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should not submit when the form is invalid', () => {
    component.submit();
    expect(authServiceMock.login).not.toHaveBeenCalled();
  });

  it('should navigate to /products on successful login', () => {
    authServiceMock.login.mockReturnValue(of({ token: 't', tokenType: 'Bearer', expiresIn: 3600, username: 'admin', role: 'ADMIN' }));
    const navigateSpy = vi.spyOn(router, 'navigate');

    component.form.setValue({ username: 'admin', password: 'admin123' });
    component.submit();

    expect(authServiceMock.login).toHaveBeenCalledWith({ username: 'admin', password: 'admin123' });
    expect(navigateSpy).toHaveBeenCalledWith(['/products']);
  });

  it('should show an error message on invalid credentials', () => {
    authServiceMock.login.mockReturnValue(throwError(() => new Error('401')));

    component.form.setValue({ username: 'admin', password: 'wrong' });
    component.submit();

    expect(component.error()).toBe('Invalid username or password');
    expect(component.submitting()).toBe(false);
  });
});
