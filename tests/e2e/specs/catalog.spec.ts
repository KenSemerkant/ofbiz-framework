import { test, expect } from '@playwright/test';

test('browse catalog and resolve MP1 price at 9.99 USD', async ({ page }) => {
  await page.goto('/catalog');
  await page.getByRole('button', { name: 'Browse catalog' }).click();
  await expect(page.getByTestId('catalog-count')).toContainText(/products/);
  const mp1 = page.getByRole('row', { name: /MP1/ });
  await expect(mp1).toBeVisible();
  await mp1.getByRole('button', { name: 'lookup' }).click();
  await expect(page.getByTestId('price-MP1')).toContainText('9.99 USD');
});

// P9-F2 product-area parity: categories, stores, facilities, stock, shipments
test('catalog area lists categories, stores, facilities, stock, and shipments', async ({ page }) => {
  await page.goto('/catalog');

  await page.getByTestId('cat-tab-categories').click();
  await page.getByRole('button', { name: 'Load categories' }).click();
  await expect(page.getByTestId('category-table')).toBeVisible();

  await page.getByTestId('cat-tab-facilities').click();
  await page.getByRole('button', { name: 'Load facilities' }).click();
  await expect(page.getByTestId('facility-table')).toBeVisible();

  await page.getByTestId('cat-tab-stock').click();
  await page.getByRole('button', { name: 'Search stock' }).click();
  await expect(page.getByTestId('stock-table').getByRole('row').nth(1)).toContainText(/MP1/);

  await page.getByTestId('cat-tab-shipments').click();
  await page.getByRole('button', { name: 'Load shipments' }).click();
  await expect(page.getByTestId('shipment-table').getByRole('row').nth(1)).toContainText(/SHIPPED|PACKED|CREATED/);
});
