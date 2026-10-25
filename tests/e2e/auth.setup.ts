import { test as setup, expect } from '@playwright/test';
import { mkdirSync } from 'node:fs';

const STATE = 'playwright/.auth/user.json';

setup('OIDC login as demo-user', async ({ page }) => {
  mkdirSync('playwright/.auth', { recursive: true });
  await page.goto('/');
  await page.getByTestId('signin').click();
  // identity login form is served through the gateway passthrough; it is
  // not our UI, so name-attribute selectors are the stable contract here.
  await page.locator('input[name="username"]').fill('demo-user');
  await page.locator('input[name="password"]').fill('demo-password');
  await page.locator('button[type="submit"], input[type="submit"]').first().click();
  await expect(page.getByTestId('signed-in')).toBeVisible({ timeout: 15_000 });
  await page.context().storageState({ path: STATE });
});
