import { test, expect } from './fixtures';

test('студент НЕ видит пункт "Импорт задач" в меню', async ({ page, loginAs, mockApi }) => {
  await loginAs('STUDENT');
  await mockApi();
  await page.goto('/tasks');
  await expect(page.getByRole('button', { name: 'Импорт задач' })).toHaveCount(0);
});

test('преподаватель видит /admin/import и может загрузить CSV', async ({ page, loginAs, mockApi }) => {
  await loginAs('TEACHER');
  await mockApi();

  await page.route('**/api/v1/admin/tasks/import', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ importedCount: 2, skippedCount: 1, errors: [{ line: 4, message: 'Задача с title "X" уже существует' }] }),
    }),
  );

  await page.goto('/admin/import');
  await expect(page.getByRole('heading', { name: /импорт задач/i })).toBeVisible();

  const csv =
    'title,difficulty,category,description,return_type,arguments_json,tests_json\n' +
    '"Sum","Easy","Math","desc","Integer","[]","[{""input"":""1"",""expectedOutput"":""1""}]"\n';
  await page.locator('input[type=file]').setInputFiles({
    name: 'tasks.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(csv, 'utf-8'),
  });
  await expect(page.getByTestId('selected-file-chip')).toBeVisible();

  await page.getByTestId('import-submit-button').click();

  await expect(page.getByTestId('import-result-card')).toBeVisible();
  await expect(page.getByTestId('import-success-chip')).toContainText('Импортировано: 2');
});

test('студент при прямом заходе на /admin/import перенаправляется на /', async ({ page, loginAs, mockApi }) => {
  await loginAs('STUDENT');
  await mockApi();
  await page.goto('/admin/import');
  await expect(page).toHaveURL(/\/$/);
});
