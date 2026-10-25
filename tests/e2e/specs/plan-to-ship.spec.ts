import { test, expect } from '@playwright/test';
import { runPrefix } from '../helpers/ids';

test('plan-to-ship: order → MRP run → shipment pack/ship → SHIPPED', async ({ page }) => {
  const run = runPrefix();
  await page.goto('/plan-to-ship');

  // 1. seed stock (captures inventoryItemId shown on screen) + place order
  await page.getByLabel('Facility').fill(run + 'fac');
  await page.getByRole('button', { name: 'Seed stock' }).click();
  await expect(page.getByTestId('pts-stock')).toContainText(/stocked.*inv/);
  await page.getByRole('button', { name: 'Place order' }).click();
  await expect(page.getByTestId('pts-order')).toContainText(/order \S+ status/);

  // 2. MRP run completes and renders proposals (content state-dependent — assert run, not items)
  await page.getByRole('button', { name: 'Run MRP' }).click();
  await expect(page.getByTestId('pts-mrp')).toContainText(/MRP run complete: \d+ proposals?/);

  // 3. ship the order: create → pack → ship, assert server status each step
  //    (domain truth: Shipment.Status { CREATED, PACKED, SHIPPED }, forward-only)
  await page.getByRole('button', { name: 'Create shipment' }).click();
  await expect(page.getByTestId('pts-shipment')).toContainText(/shipment \S+ status CREATED/);
  await page.getByRole('button', { name: 'Pack' }).click();
  await expect(page.getByTestId('pts-shipment')).toContainText('status PACKED');
  await page.getByRole('button', { name: 'Ship' }).click();
  await expect(page.getByTestId('pts-shipment')).toContainText('status SHIPPED');
});
