import { test, expect } from './fixtures';

// Grant clipboard permissions for this file
test.use({ permissions: ['clipboard-read', 'clipboard-write'] });

const TASK_ID = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';
const TASK_DESCRIPTION = 'Given two numbers a and b, print their sum.\n\nInput: two integers on one line\nOutput: their sum';

const SUBMISSION_ID = 'sub-0001-0000-0000-000000000001';

const mockTask = {
  testId: TASK_ID,
  title: 'Two Sum',
  description: TASK_DESCRIPTION,
  difficulty: 'Easy',
  category: 'Array',
};

const mockTestOutput = '42\n';
const mockTestError = 'java.lang.NullPointerException at line 5';

const mockSubmissionResult = {
  taskId: TASK_ID,
  testId: SUBMISSION_ID,
  status: 'PARTIAL_SUCCESS',
  totalTests: 2,
  passedTests: 1,
  totalExecutionTimeMs: 120,
  memoryUsedKb: 8192,
  createdAt: new Date().toISOString(),
  testResults: [
    {
      testId: 'tr-0001',
      status: 'PASSED',
      verdict: 'OK',
      output: mockTestOutput,
      executionTimeMs: 60,
      memoryUsedKb: 4096,
    },
    {
      testId: 'tr-0002',
      status: 'FAILED',
      verdict: 'RUNTIME_ERROR',
      output: '',
      error: mockTestError,
      executionTimeMs: 60,
      memoryUsedKb: 4096,
    },
  ],
};

test.beforeEach(async ({ loginAs, mockApi, page }) => {
  await loginAs('STUDENT');
  await mockApi();

  // Mock individual task endpoint
  await page.route(`**/api/v1/tasks/${TASK_ID}`, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(mockTask),
    }),
  );

  // Mock submission result endpoint
  await page.route(`**/api/v1/task-results/${SUBMISSION_ID}`, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(mockSubmissionResult),
    }),
  );

  // Mock AI analysis — return 404 so the AI block shows "unavailable"
  await page.route(`**/api/v1/ai-analysis/${SUBMISSION_ID}`, (route) =>
    route.fulfill({ status: 404, contentType: 'application/json', body: '{}' }),
  );
});

test('кнопка копирования условия задачи копирует описание в буфер', async ({ page }) => {
  await page.goto(`/tasks/${TASK_ID}`);

  // Wait for task to load
  await expect(page.getByRole('heading', { name: 'Two Sum' })).toBeVisible();

  // Find and click the CopyButton next to "Условие задачи"
  const copyBtn = page.getByRole('button', { name: /Копировать условие задачи/i });
  await expect(copyBtn).toBeVisible();
  await copyBtn.click();

  // Verify clipboard content
  const clipboardText = await page.evaluate(() => navigator.clipboard.readText());
  expect(clipboardText).toBe(TASK_DESCRIPTION);
});

test('кнопка копирования вывода программы в деталях теста копирует output', async ({ page }) => {
  await page.goto(`/results/${SUBMISSION_ID}`);

  // Wait for results to load (status chip)
  await expect(page.getByText(/Частично принято/)).toBeVisible();

  // Expand the first test accordion (Тест #1 — PASSED, has output)
  const firstAccordion = page.getByText('Тест #1').first();
  await firstAccordion.click();

  // Wait for the output section to appear
  await expect(page.getByText('Вывод программы')).toBeVisible();

  // Click the CopyButton for "вывод программы"
  const copyOutputBtn = page.getByRole('button', { name: /Копировать вывод программы/i });
  await expect(copyOutputBtn).toBeVisible();
  await copyOutputBtn.click();

  // Verify clipboard content matches the test output
  const clipboardText = await page.evaluate(() => navigator.clipboard.readText());
  expect(clipboardText).toBe(mockTestOutput);
});

test('кнопка копирования ошибки в деталях теста копирует error', async ({ page }) => {
  await page.goto(`/results/${SUBMISSION_ID}`);

  // Wait for results to load
  await expect(page.getByText(/Частично принято/)).toBeVisible();

  // Expand the second test accordion (Тест #2 — FAILED, has error)
  const secondAccordion = page.getByText('Тест #2').first();
  await secondAccordion.click();

  // Wait for the error section to appear
  await expect(page.getByText('Ошибка выполнения')).toBeVisible();

  // Click the CopyButton for "ошибку выполнения"
  const copyErrorBtn = page.getByRole('button', { name: /Копировать ошибку выполнения/i });
  await expect(copyErrorBtn).toBeVisible();
  await copyErrorBtn.click();

  // Verify clipboard content
  const clipboardText = await page.evaluate(() => navigator.clipboard.readText());
  expect(clipboardText).toBe(mockTestError);
});

test('кнопка «Скопировать всё» собирает вывод и ошибку в одну строку', async ({ page }) => {
  await page.goto(`/results/${SUBMISSION_ID}`);

  await expect(page.getByText(/Частично принято/)).toBeVisible();

  // Expand Тест #2 which has only error (no output)
  await page.getByText('Тест #2').first().click();
  await expect(page.getByText('Ошибка выполнения')).toBeVisible();

  // The "Скопировать всё" button is the one with aria-label "Копировать все данные теста"
  const copyAllBtn = page.getByRole('button', { name: /Копировать все данные теста/i });
  await expect(copyAllBtn).toBeVisible();
  await copyAllBtn.click();

  const clipboardText = await page.evaluate(() => navigator.clipboard.readText());
  expect(clipboardText).toContain('Ошибка:');
  expect(clipboardText).toContain(mockTestError);
});
