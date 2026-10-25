import { test, expect } from '@playwright/test';
import { runPrefix } from '../helpers/ids';

test('campaign-to-attribution: campaign + tracking code → tracked order → attribution detail', async ({ page }) => {
  const run = runPrefix();
  const campaignId = run + 'cmp';
  const codeId = run + 'tc';
  await page.goto('/campaign');

  // 1. create campaign + tracking code (ids derived from the run-prefix input)
  await page.getByLabel('Run prefix').fill(run);
  await page.getByRole('button', { name: 'Create campaign + code' }).click();
  await expect(page.getByTestId('cmp-setup')).toContainText(`campaign ${campaignId} + code ${codeId}`);

  // 2. seed stock + place order carrying the tracking code
  await page.getByLabel('Facility').fill(run + 'fac');
  await page.getByRole('button', { name: 'Place tracked order' }).click();
  await expect(page.getByTestId('cmp-order')).toContainText(/order \S+ status/);

  // 3. attribution readback (event-driven read model — screen retries briefly)
  await page.getByRole('button', { name: 'Show attribution' }).click();
  await expect(page.getByTestId('cmp-attribution')).toContainText(new RegExp(`code ${codeId} .* orders 1`));
});
