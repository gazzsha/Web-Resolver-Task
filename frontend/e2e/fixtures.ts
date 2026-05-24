import { test as base, expect, type Page } from '@playwright/test';

export type Role = 'STUDENT' | 'TEACHER';

/**
 * Маленький JWT-стуб (header.payload.signature, base64url). Подпись не валидируется на фронте.
 */
function fakeJwt(username: string, role: Role): string {
  const header = base64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = base64url(JSON.stringify({ sub: username, username, role, exp: Math.floor(Date.now() / 1000) + 3600 }));
  return `${header}.${payload}.sig`;
}

function base64url(s: string): string {
  return Buffer.from(s, 'utf-8').toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

interface Fixtures {
  loginAs: (role: Role) => Promise<void>;
  mockApi: () => Promise<void>;
}

export const test = base.extend<Fixtures>({
  loginAs: async ({ context }, use) => {
    await use(async (role) => {
      const username = role === 'TEACHER' ? 'teacher@test' : 'student@test';
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
    });
  },
  mockApi: async ({ page }, use) => {
    await use(async () => {
      // Список задач
      await page.route('**/api/v1/tasks?**', (route) =>
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            { testId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', title: 'Two Sum', description: 'Sum two numbers', difficulty: 'Easy', category: 'Array' },
            { testId: 'b2c3d4e5-f6a7-8901-bcde-f12345678901', title: 'Valid Parentheses', description: 'Check brackets', difficulty: 'Easy', category: 'Stack' },
            { testId: 'c3d4e5f6-a7b8-9012-cdef-123456789012', title: 'FizzBuzz', description: 'Print numbers', difficulty: 'Easy', category: 'Math' },
          ]),
        }),
      );
      await page.route('**/api/v1/tasks', (route) =>
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            { testId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', title: 'Two Sum', description: 'Sum two numbers', difficulty: 'Easy', category: 'Array' },
            { testId: 'b2c3d4e5-f6a7-8901-bcde-f12345678901', title: 'Valid Parentheses', description: 'Check brackets', difficulty: 'Easy', category: 'Stack' },
            { testId: 'c3d4e5f6-a7b8-9012-cdef-123456789012', title: 'FizzBuzz', description: 'Print numbers', difficulty: 'Easy', category: 'Math' },
          ]),
        }),
      );
      await page.route('**/api/v1/tasks/categories', (route) =>
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(['Array', 'Math', 'Stack']),
        }),
      );
      // Submissions/me — пусто
      await page.route('**/api/v1/me/submissions', (route) =>
        route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }),
      );
      await page.route('**/api/v1/me/stats', (route) =>
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ tasksSolved: 0, totalSubmissions: 0, averageQuality: null, recentSubmissions: [] }),
        }),
      );
    });
  },
});

export { expect };
export type { Page };
