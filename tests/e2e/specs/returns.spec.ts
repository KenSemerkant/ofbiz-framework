import { test, expect } from '@playwright/test';
import { runPrefix } from '../helpers/ids';

test('returns: order→approve→complete→return, server-truth COMPLETED', async ({ page }) => {
  const run = runPrefix();
  await page.goto('/returns');

  // 1. seed stock + place a one-line order for MP1 (journey prereq)
  await page.getByLabel('Facility').fill(run + 'fac');
  await page.getByRole('button', { name: 'Seed stock' }).click();
  await expect(page.getByTestId('ret-stock')).toContainText('stocked');
  await page.getByRole('button', { name: 'Place order' }).click();
  await expect(page.getByTestId('ret-order')).toContainText(/order \S+ status/);

  // 2. drive the order to COMPLETED (returns need COMPLETED — server contract)
  await page.getByRole('button', { name: 'Approve' }).click();
  await expect(page.getByTestId('ret-order')).toContainText('status APPROVED');
  await page.getByRole('button', { name: 'Complete' }).click();
  await expect(page.getByTestId('ret-order')).toContainText('status COMPLETED');

  // 3. return one unit of the ordered product
  await page.getByRole('button', { name: 'Create return' }).click();
  await expect(page.getByTestId('ret-return')).toContainText(/return \S+ status COMPLETED/);

  // 4. read back from the server (no locally-minted state)
  await page.getByRole('button', { name: 'Read back' }).click();
  await expect(page.getByTestId('ret-readback')).toContainText(/COMPLETED.*MP1/);
});
