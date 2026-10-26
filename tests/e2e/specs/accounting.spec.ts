import { test, expect } from '@playwright/test';

// P9-F1 accounting parity: the SPA area must serve the monolith accounting
// app's core read surfaces — AR find, chart of accounts, journal, and the
// three statements — through the gateway against cluster truth.

test('accounting area serves invoices, chart of accounts, and statements', async ({ page }) => {
  await page.goto('/accounting');

  // AR find screen: unfiltered search answers (rows optional, surface mandatory)
  await page.getByRole('button', { name: 'Search' }).click();
  await expect(page.getByTestId('invoice-table')).toBeVisible();

  // Chart of accounts is seeded (finance slice) — rows must render
  await page.getByTestId('tab-accounts').click();
  await page.getByRole('button', { name: 'Load chart of accounts' }).click();
  await expect(page.getByTestId('gl-account-table').getByRole('row').nth(1))
    .toContainText(/\d/);

  // Journal listing renders server truth
  await page.getByTestId('tab-journal').click();
  await page.getByRole('button', { name: 'Load journal' }).click();
  await expect(page.getByTestId('journal-table')).toBeVisible();

  // Statements: all three answer with totals
  await page.getByTestId('tab-statements').click();
  await page.getByRole('button', { name: 'Trial balance' }).click();
  await expect(page.getByTestId('tb-totals')).not.toBeEmpty();
  await page.getByRole('button', { name: 'Balance sheet' }).click();
  await expect(page.getByTestId('bs-totals')).not.toBeEmpty();
  await page.getByRole('button', { name: 'Income statement' }).click();
  await expect(page.getByTestId('net-income')).not.toBeEmpty();
});

test('token removed mid-session surfaces error, not a crash', async ({ page }) => {
  await page.goto('/accounting');
  await page.evaluate(() => localStorage.removeItem('ofbiz.token'));
  await page.getByRole('button', { name: 'Search' }).click();
  await expect(page.getByTestId('accounting-msg')).toBeVisible();
});
