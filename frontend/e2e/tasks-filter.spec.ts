import { test, expect } from './fixtures';

test.beforeEach(async ({ loginAs, mockApi }) => {
  await loginAs('STUDENT');
  await mockApi();
});

test('каталог отображает 3 задачи и счётчик "Показано 3 из 3"', async ({ page }) => {
  await page.goto('/tasks');
  await expect(page.getByText(/Показано 3 из 3/)).toBeVisible();
  await expect(page.getByRole('button', { name: /Открыть задачу: Two Sum/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /Открыть задачу: Valid Parentheses/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /Открыть задачу: FizzBuzz/ })).toBeVisible();
});

test('фильтр по категории "Math" оставляет одну задачу', async ({ page }) => {
  await page.goto('/tasks');
  const categoryInput = page.getByLabel('Фильтр по категории');
  await categoryInput.click();
  await page.getByRole('option', { name: 'Math' }).click();
  await expect(page.getByText(/Показано 1 из 3/)).toBeVisible();
  await expect(page.getByRole('button', { name: /Открыть задачу: FizzBuzz/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /Открыть задачу: Two Sum/ })).toHaveCount(0);
});

test('фильтр по сложности Hard скрывает все задачи и показывает empty-state', async ({ page }) => {
  await page.goto('/tasks');
  await page.getByRole('button', { name: 'Сложная' }).click();
  await expect(page.getByText(/По вашему запросу ничего не найдено/)).toBeVisible();
  await expect(page.getByRole('button', { name: /Сбросить фильтры/ })).toBeVisible();
});

test('сброс фильтров возвращает полный список', async ({ page }) => {
  await page.goto('/tasks');
  await page.getByRole('button', { name: 'Сложная' }).click();
  await page.getByRole('button', { name: /Сбросить фильтры/ }).click();
  await expect(page.getByText(/Показано 3 из 3/)).toBeVisible();
});
