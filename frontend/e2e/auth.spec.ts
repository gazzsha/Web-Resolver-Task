import { test, expect } from './fixtures';

test('неаутентифицированный пользователь перенаправляется на /login при заходе на /tasks', async ({ page }) => {
  await page.goto('/tasks');
  await expect(page).toHaveURL(/\/login/);
  await expect(page.getByRole('heading', { name: /вход|войти|sign in/i })).toBeVisible();
});

test('страница /register доступна без авторизации', async ({ page }) => {
  await page.goto('/register');
  await expect(page.locator('input[type=email], input[name=email]').first()).toBeVisible();
});

test('после loginAs("STUDENT") страница /tasks загружается', async ({ page, loginAs, mockApi }) => {
  await loginAs('STUDENT');
  await mockApi();
  await page.goto('/tasks');
  await expect(page.getByRole('heading', { name: /^Задачи$/ })).toBeVisible();
});
