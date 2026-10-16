import { defineConfig, devices } from '@playwright/test';

// Real-browser tests of the screens. The API is stubbed inside each test, so no backend is needed and
// the tests are deterministic. A run against the real backend belongs to the docker compose setup.
const E2E_PORT = 4201;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  reporter: [['list']],
  use: { baseURL: `http://localhost:${E2E_PORT}`, trace: 'on-first-retry' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: `npx ng serve --port ${E2E_PORT}`,
    url: `http://localhost:${E2E_PORT}`,
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
