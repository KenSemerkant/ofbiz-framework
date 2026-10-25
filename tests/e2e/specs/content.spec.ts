import { test, expect } from '@playwright/test';
import { runPrefix } from '../helpers/ids';

test('content: create → upload → publish → fetch roundtrip', async ({ page }) => {
  const run = runPrefix();
  await page.goto('/content');

  await page.getByLabel('Content ID').fill(run + 'doc');
  await page.getByRole('button', { name: 'Create + upload + publish' }).click();
  await expect(page.getByTestId('cnt-status')).toContainText(/published/);

  await page.getByRole('button', { name: 'Fetch data' }).click();
  await expect(page.getByTestId('cnt-data')).toContainText('e2e content roundtrip');
});
