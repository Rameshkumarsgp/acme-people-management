import { Page, expect, test } from '@playwright/test';

const VALID_AUTHORIZATION = 'Basic aHI6czNjcmV0'; // hr:s3cret

/** Answers the login check like the backend does: 200 for hr:s3cret, 401 for anything else. */
async function stubBackend(page: Page): Promise<void> {
  await page.route('**/api/auth/me', async (route) => {
    const authorization = route.request().headers()['authorization'];
    if (authorization === VALID_AUTHORIZATION) {
      await route.fulfill({ json: { username: 'hr' } });
    } else {
      await route.fulfill({ status: 401, json: { title: 'Unauthorized', status: 401 } });
    }
  });
}

async function signIn(page: Page, username: string, password: string): Promise<void> {
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

test.beforeEach(async ({ page }) => {
  // Fonts come from Google. Blocking them keeps the tests offline-safe and deterministic.
  await page.route(/fonts\.(googleapis|gstatic)\.com/, (route) => route.abort());
  await stubBackend(page);
});

test('shows the product name centered, with no tagline', async ({ page }) => {
  await page.goto('/login');

  const name = page.getByRole('heading', { name: 'ACME People Management' });
  await expect(name).toBeVisible();
  await expect(page.getByText('See how ACME pays people')).toHaveCount(0);

  const box = (await name.boundingBox())!;
  const viewportWidth = page.viewportSize()!.width;
  expect(Math.abs(box.x + box.width / 2 - viewportWidth / 2)).toBeLessThan(2);
});

test('sends a visitor who is not logged in to the login page', async ({ page }) => {
  await page.goto('/');

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
});

test('signs in with the right password and lands on the home page', async ({ page }) => {
  await page.goto('/login');

  await signIn(page, 'hr', 's3cret');

  await expect(page).toHaveURL(/localhost:\d+\/$/);
  await expect(page.getByText('Welcome, hr')).toBeVisible();
});

test('signs in with the Enter key', async ({ page }) => {
  await page.goto('/login');

  await page.getByLabel('Username').fill('hr');
  await page.getByLabel('Password').fill('s3cret');
  await page.getByLabel('Password').press('Enter');

  await expect(page.getByText('Welcome, hr')).toBeVisible();
});

test('shows an error and stays on the login page when the password is wrong', async ({ page }) => {
  await page.goto('/login');

  await signIn(page, 'hr', 'wrong');

  await expect(page.getByRole('alert')).toHaveText('Invalid username or password.');
  await expect(page).toHaveURL(/\/login$/);
});

test('shows how long to wait after too many attempts', async ({ page }) => {
  await page.route('**/api/auth/me', (route) =>
    route.fulfill({ status: 429, headers: { 'Retry-After': '45' }, json: { status: 429 } }),
  );
  await page.goto('/login');

  await signIn(page, 'hr', 's3cret');

  await expect(page.getByRole('alert')).toHaveText(
    'Too many failed attempts. Try again in 45 seconds.',
  );
});

test('asks for the missing fields without calling the server', async ({ page }) => {
  const apiCalls: string[] = [];
  page.on('request', (request) => {
    if (request.url().includes('/api/')) {
      apiCalls.push(request.url());
    }
  });
  await page.goto('/login');

  await page.getByRole('button', { name: 'Sign in' }).click();

  await expect(page.getByText('Enter your username.')).toBeVisible();
  await expect(page.getByText('Enter your password.')).toBeVisible();
  expect(apiCalls).toEqual([]);
});

test('forgets the login when the page is refreshed', async ({ page }) => {
  await page.goto('/login');
  await signIn(page, 'hr', 's3cret');
  await expect(page.getByText('Welcome, hr')).toBeVisible();

  await page.reload();

  await expect(page).toHaveURL(/\/login$/);
});

test('signs out and returns to the login page', async ({ page }) => {
  await page.goto('/login');
  await signIn(page, 'hr', 's3cret');

  await page.getByRole('button', { name: 'Sign out' }).click();

  await expect(page).toHaveURL(/\/login$/);
});

test('keeps the credentials out of browser storage and cookies', async ({ page, context }) => {
  await page.goto('/login');
  await signIn(page, 'hr', 's3cret');
  await expect(page.getByText('Welcome, hr')).toBeVisible();

  const storage = await page.evaluate(() => ({
    local: Object.keys(localStorage),
    session: Object.keys(sessionStorage),
  }));

  expect(storage).toEqual({ local: [], session: [] });
  expect(await context.cookies()).toEqual([]);
});

test('fits a phone screen without sideways scrolling', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/login');

  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();

  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow).toBeLessThanOrEqual(0);
});
