import { test, expect } from '@playwright/test';

test('search parties by role shows seeded customers', async ({ page }) => {
  await page.goto('/parties');
  await page.getByLabel('Role').fill('CUSTOMER');
  await page.getByRole('button', { name: 'Search' }).click();
  await expect(page.getByTestId('parties-count')).toContainText(/parties/);
  await expect(page.getByRole('table').getByRole('row').nth(1)).toContainText(/P\d|demo/);
});

test('token removed mid-session surfaces error, not a crash', async ({ page }) => {
  await page.goto('/parties');
  await page.evaluate(() => localStorage.removeItem('ofbiz.token'));
  await page.getByRole('button', { name: 'Search' }).click();
  await expect(page.getByTestId('parties-msg')).toBeVisible();
});
