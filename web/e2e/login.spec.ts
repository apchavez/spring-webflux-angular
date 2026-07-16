import { test, expect } from '@playwright/test';

test.describe('Login', () => {
  test('redirects an unauthenticated visitor to /login', async ({ page }) => {
    await page.goto('/products');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('logs in and reaches the product list', async ({ page }) => {
    await page.route('**/api/v1/auth/login', async route => {
      await route.fulfill({
        json: { token: 'e2e-token', tokenType: 'Bearer', expiresIn: 3600, username: 'admin', role: 'ADMIN' },
      });
    });
    await page.route('**/api/v1/products**', async route => {
      await route.fulfill({ json: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 1, last: true } });
    });

    await page.goto('/login');
    await page.getByLabel('Username').fill('admin');
    await page.getByLabel('Password').fill('admin123');
    await page.getByRole('button', { name: 'Sign In' }).click();

    await expect(page).toHaveURL(/\/products$/);
    await expect(page.getByText('admin')).toBeVisible();
  });

  test('shows an error message on invalid credentials', async ({ page }) => {
    await page.route('**/api/v1/auth/login', async route => {
      await route.fulfill({ status: 401, json: { status: 401, error: 'Unauthorized', message: 'Usuario o contraseña inválidos' } });
    });

    await page.goto('/login');
    await page.getByLabel('Username').fill('admin');
    await page.getByLabel('Password').fill('wrong-password');
    await page.getByRole('button', { name: 'Sign In' }).click();

    await expect(page.getByText('Invalid username or password')).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);
  });

  test('logs out and returns to /login', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('auth', JSON.stringify({ token: 'e2e-token', username: 'admin', role: 'ADMIN' }));
    });
    await page.route('**/api/v1/products**', async route => {
      await route.fulfill({ json: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 1, last: true } });
    });

    await page.goto('/products');
    await page.getByRole('button', { name: 'Logout' }).click();

    await expect(page).toHaveURL(/\/login$/);
  });
});
