// Screenshot generator for thesis Appendix B.
// Uses Playwright (already bundled with frontend devDeps).
// Drives Vite dev-server (port 3002) with a mocked backend layer.
//
// Run: cd frontend && node screenshots.mjs

import { chromium } from 'playwright';
import { mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT_DIR = path.resolve(__dirname, '../thesis/figures/screenshots');
const BASE_URL = process.env.BASE_URL ?? 'http://localhost:3002';

function fakeJwt(username, role) {
  const enc = (o) =>
    Buffer.from(JSON.stringify(o), 'utf-8')
      .toString('base64')
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');
  const header = enc({ alg: 'HS256', typ: 'JWT' });
  const payload = enc({
    sub: username,
    username,
    role,
    exp: Math.floor(Date.now() / 1000) + 3600,
  });
  return `${header}.${payload}.sig`;
}

const TASKS = [
  {
    testId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    title: 'Two Sum',
    description: 'Дан массив целых чисел и целевое значение. Верните индексы двух чисел, дающих в сумме target.',
    difficulty: 'Easy',
    category: 'Array',
  },
  {
    testId: 'b2c3d4e5-f6a7-8901-bcde-f12345678901',
    title: 'Valid Parentheses',
    description:
      'Дана строка из символов "(){}[]". Определите, корректно ли расставлены скобки.\n\nПример:\n  "()[]{}"   -> true\n  "(]"       -> false\n  "([)]"     -> false',
    difficulty: 'Easy',
    category: 'Stack',
  },
  {
    testId: 'c3d4e5f6-a7b8-9012-cdef-123456789012',
    title: 'FizzBuzz',
    description: 'Выведите числа от 1 до n с заменой кратных 3 — Fizz, кратных 5 — Buzz, кратных 15 — FizzBuzz.',
    difficulty: 'Easy',
    category: 'Math',
  },
  {
    testId: 'd4e5f6a7-b8c9-0123-def1-234567890123',
    title: 'Binary Search',
    description: 'Отсортированный массив, целевое значение. Верните индекс или -1.',
    difficulty: 'Medium',
    category: 'Array',
  },
];

const SUBMISSION_ID = 'aaaa1111-2222-3333-4444-555566667777';
const PROCESSING_STATE = {
  status: 'PROCESSING',
  task_id: 'b2c3d4e5-f6a7-8901-bcde-f12345678901',
  testResults: [],
  ai_analysis: null,
};
const FINAL_STATE = {
  status: 'PARTIAL_SUCCESS',
  task_id: 'b2c3d4e5-f6a7-8901-bcde-f12345678901',
  language: 'python',
  source_code: 'print("hello world")\n',
  total_tests: 4,
  passed_tests: 0,
  testResults: [
    { test_id: 't1', verdict: 'WA', input: '"()"', expected: 'true', actual: 'hello world', duration_ms: 12, memory_kb: 7200 },
    { test_id: 't2', verdict: 'WA', input: '"(]"', expected: 'false', actual: 'hello world', duration_ms: 11, memory_kb: 7200 },
    { test_id: 't3', verdict: 'WA', input: '"([)]"', expected: 'false', actual: 'hello world', duration_ms: 12, memory_kb: 7200 },
    { test_id: 't4', verdict: 'WA', input: '"{}"', expected: 'true', actual: 'hello world', duration_ms: 13, memory_kb: 7200 },
  ],
  ai_analysis: {
    code_quality: 40,
    complexity: 'LOW',
    explanation:
      'Представленный код выводит литеральную строку и не выполняет требуемой задачи проверки сбалансированности скобок. Все тесты завершились с вердиктом WA, поскольку ожидаемый ответ — булева величина (true/false), а программа печатает "hello world". Это типичная заглушка, не реализующая алгоритма решения.',
    issues: [
      'Не выполняется чтение входной строки.',
      'Ответ не зависит от входных данных — ни один тест не может пройти.',
      'Отсутствует структура данных «стек» для проверки баланса скобок.',
    ],
    recommendations: [
      'Считать строку входных данных через input().',
      'Использовать список как стек: добавлять открывающие скобки, при закрывающей проверять соответствие верхушки.',
      'Проверять, что стек пуст по окончании обхода — иначе ответ false.',
      'Вернуть результат как "true"/"false" в зависимости от баланса.',
    ],
    model: 'gigachat',
    analyzed_at: '2026-05-13T22:55:00Z',
  },
};

async function setupMocks(page) {
  await page.route('**/api/v1/tasks?**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(TASKS) }),
  );
  await page.route('**/api/v1/tasks', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(TASKS) }),
  );
  await page.route('**/api/v1/tasks/categories', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(['Array', 'Math', 'Stack']) }),
  );
  await page.route(/\/api\/v1\/tasks\/[a-f0-9-]+$/, (route) => {
    const url = route.request().url();
    const id = url.split('/').pop();
    const task = TASKS.find((t) => t.testId === id) ?? TASKS[1];
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(task) });
  });
  await page.route(/\/api\/v1\/submissions\/[a-f0-9-]+$/, (route) => {
    const u = new URL(route.request().url());
    const fn = u.searchParams.get('__state') ?? 'final';
    const body = fn === 'processing' ? PROCESSING_STATE : FINAL_STATE;
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ submission_id: SUBMISSION_ID, ...body }) });
  });
  await page.route('**/api/v1/me/submissions', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        { submission_id: SUBMISSION_ID, task_id: TASKS[1].testId, task_title: 'Valid Parentheses', status: 'PARTIAL_SUCCESS', total_tests: 4, passed_tests: 0, submitted_at: '2026-05-13T22:55:00Z', code_quality: 40 },
        { submission_id: 'bbbb2222-3333-4444-5555-666677778888', task_id: TASKS[0].testId, task_title: 'Two Sum', status: 'SUCCESS', total_tests: 3, passed_tests: 3, submitted_at: '2026-05-13T20:12:00Z', code_quality: 78 },
        { submission_id: 'cccc3333-4444-5555-6666-777788889999', task_id: TASKS[2].testId, task_title: 'FizzBuzz', status: 'SUCCESS', total_tests: 2, passed_tests: 2, submitted_at: '2026-05-13T18:34:00Z', code_quality: 65 },
      ]),
    }),
  );
  await page.route('**/api/v1/me/stats', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ tasksSolved: 2, totalSubmissions: 3, averageQuality: 61, recentSubmissions: [] }),
    }),
  );
  await page.route('**/api/v1/admin/tasks/import', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ imported: 12, skipped: 2, errors: [] }),
    }),
  );
}

async function loginAs(context, role) {
  const username = role === 'TEACHER' ? 'teacher@test.local' : 'student@test.local';
  const token = fakeJwt(username, role);
  await context.addInitScript(
    ({ token, username, role }) => {
      localStorage.setItem('authToken', token);
      localStorage.setItem('refreshToken', token);
      localStorage.setItem('authUsername', username);
      localStorage.setItem('authUser', JSON.stringify({ email: username, role, username }));
    },
    { token, username, role },
  );
}

async function shot(page, file) {
  const out = path.join(OUT_DIR, file);
  await page.screenshot({ path: out, fullPage: false });
  console.log(`  → ${file}`);
}

async function run() {
  await mkdir(OUT_DIR, { recursive: true });
  const browser = await chromium.launch({ headless: true });

  // -------- guest pages (no login) --------
  const guestCtx = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'ru-RU' });
  const guest = await guestCtx.newPage();
  await setupMocks(guest);

  // 01 login
  await guest.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' });
  await guest.fill('input[type="email"], input[name="email"], input[name="username"]', 'student@test.local').catch(() => {});
  await guest.fill('input[type="password"], input[name="password"]', 'studentPass123').catch(() => {});
  await guest.waitForTimeout(400);
  await shot(guest, '01_login.png');

  // 02 register
  await guest.goto(`${BASE_URL}/register`, { waitUntil: 'networkidle' });
  await guest.waitForTimeout(400);
  await shot(guest, '02_register.png');

  await guestCtx.close();

  // -------- student pages --------
  const stuCtx = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'ru-RU' });
  await loginAs(stuCtx, 'STUDENT');
  const stu = await stuCtx.newPage();
  await setupMocks(stu);

  // 03 catalog with filter
  await stu.goto(`${BASE_URL}/tasks`, { waitUntil: 'networkidle' });
  await stu.waitForTimeout(800);
  await shot(stu, '03_tasks.png');

  // 04 task detail
  await stu.goto(`${BASE_URL}/tasks/${TASKS[1].testId}`, { waitUntil: 'networkidle' });
  await stu.waitForTimeout(800);
  await shot(stu, '04_task_detail.png');

  // 05 editor
  await stu.goto(`${BASE_URL}/submit/${TASKS[1].testId}`, { waitUntil: 'networkidle' });
  await stu.waitForTimeout(1500);
  // Try to type into Monaco textarea
  try {
    await stu.click('.monaco-editor textarea', { force: true });
    await stu.keyboard.type('print("hello world")', { delay: 20 });
  } catch (e) {
    /* ignore */
  }
  await stu.waitForTimeout(500);
  await shot(stu, '05_editor.png');

  // 06 results polling
  await stu.goto(`${BASE_URL}/results/${SUBMISSION_ID}?__state=processing`, { waitUntil: 'networkidle' });
  await stu.waitForTimeout(600);
  await shot(stu, '06_polling.png');

  // 07 final results
  await stu.goto(`${BASE_URL}/results/${SUBMISSION_ID}`, { waitUntil: 'networkidle' });
  await stu.waitForTimeout(1200);
  await shot(stu, '07_results.png');

  // 08 submissions
  await stu.goto(`${BASE_URL}/submissions`, { waitUntil: 'networkidle' });
  await stu.waitForTimeout(800);
  await shot(stu, '08_submissions.png');

  await stuCtx.close();

  // -------- teacher pages --------
  const teaCtx = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'ru-RU' });
  await loginAs(teaCtx, 'TEACHER');
  const tea = await teaCtx.newPage();
  await setupMocks(tea);
  await tea.goto(`${BASE_URL}/admin/import`, { waitUntil: 'networkidle' });
  await tea.waitForTimeout(800);
  await shot(tea, '09_admin_import.png');
  await teaCtx.close();

  // -------- grafana --------
  const gfCtx = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'ru-RU' });
  const gf = await gfCtx.newPage();
  await gf.goto('http://localhost:3000/?orgId=1', { waitUntil: 'networkidle' });
  await gf.waitForTimeout(1500);
  await shot(gf, '10_grafana_slo.png');
  await gfCtx.close();

  await browser.close();
  console.log(`\nDone. Saved to ${OUT_DIR}`);
}

run().catch((err) => {
  console.error(err);
  process.exit(1);
});
