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

// P9-F4 party parity: blank role lists all parties; a row opens the detail
test('blank role lists all parties and row opens party detail', async ({ page }) => {
  await page.goto('/parties');
  await page.getByLabel('Role').fill('');
  await page.getByRole('button', { name: 'Search' }).click();
  await expect(page.getByTestId('parties-count')).toContainText(/parties/);
  await page.getByRole('row').nth(1).click();
  await expect(page.getByTestId('party-detail')).toBeVisible();
  await expect(page.getByTestId('party-roles')).toContainText(/Roles:/);
});

// P10-F0 partymgr edit parity: create → grant roles → add mech → edit → revoke
test('party edit roundtrip: create, mech, role grant and revoke', async ({ page }) => {
  const id = `e2e-${Date.now().toString(36)}-ed`;
  await page.goto('/parties');

  await page.getByTestId('new-party').click();
  await page.getByLabel('ID').fill(id);
  await page.getByLabel('First name').fill('Edit');
  await page.getByLabel('Last name').fill('Tester');
  await page.getByLabel('Roles (comma-separated)').fill('CUSTOMER, SUPPLIER');
  await page.getByLabel('Value', { exact: true }).fill('edit.tester@example.com');
  await page.getByTestId('add-mech').click();
  await page.getByRole('button', { name: 'Save party' }).click();

  const detail = page.getByTestId('party-detail');
  await expect(detail).toBeVisible();
  await expect(page.getByTestId('party-roles')).toContainText('SUPPLIER');
  await expect(detail).toContainText('edit.tester@example.com');

  // edit: revoke SUPPLIER, keep the mech (full-state upsert must preserve it)
  await page.getByTestId('edit-party').click();
  await page.getByLabel('Roles (comma-separated)').fill('CUSTOMER');
  await page.getByRole('button', { name: 'Save party' }).click();
  await expect(page.getByTestId('party-roles')).toContainText('CUSTOMER');
  await expect(page.getByTestId('party-roles')).not.toContainText('SUPPLIER');
  await expect(detail).toContainText('edit.tester@example.com');
});
