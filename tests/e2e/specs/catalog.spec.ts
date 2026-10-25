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
