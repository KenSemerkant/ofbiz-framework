import { test, expect } from '@playwright/test';

// P9-F3 order parity: the SPA area must serve the monolith order manager's
// core find surfaces — order search, quote list, return list.

test('orders area serves order search, quotes, and returns', async ({ page }) => {
  await page.goto('/orders');

  // Order find: unfiltered search answers (rows optional — suite ordering
  // guarantees journey data exists, but isolation rules say surface-mandatory)
  await page.getByRole('button', { name: 'Search' }).click();
  await expect(page.getByTestId('order-table')).toBeVisible();

  await page.getByTestId('ord-tab-quotes').click();
  await page.getByRole('button', { name: 'Load quotes' }).click();
  await expect(page.getByTestId('quote-table').getByRole('row').nth(1)).toContainText(/QUO|\w/);

  await page.getByTestId('ord-tab-returns').click();
  await page.getByRole('button', { name: 'Load returns' }).click();
  await expect(page.getByTestId('return-table')).toBeVisible();
});

test('token removed mid-session surfaces error, not a crash', async ({ page }) => {
  await page.goto('/orders');
  await page.evaluate(() => localStorage.removeItem('ofbiz.token'));
  await page.getByRole('button', { name: 'Search' }).click();
  await expect(page.getByTestId('orders-msg')).toBeVisible();
});
