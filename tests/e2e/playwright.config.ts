import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: '.',
  timeout: 30_000,
  reporter: process.env.CI ? 'dot' : 'list',
  use: { baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:4200' },
  projects: [
    { name: 'setup', testMatch: /auth\.setup\.ts/ },
    {
      name: 'e2e',
      testMatch: /specs\/.*\.spec\.ts/,
      dependencies: ['setup'],
      use: { storageState: 'playwright/.auth/user.json' },
    },
  ],
  outputDir: 'playwright/.artifacts',
});
