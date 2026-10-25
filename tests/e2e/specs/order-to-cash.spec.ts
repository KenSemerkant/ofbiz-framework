import { test, expect } from '@playwright/test';
import { runPrefix } from '../helpers/ids';

test('order-to-cash: quote → order → approve → invoice → payment capture → GL visible', async ({ page }) => {
  const run = runPrefix();
  await page.goto('/order-to-cash');

  // 1. seed stock (saga reserves on place)
  await page.getByLabel('Facility').fill(run + 'fac');
  await page.getByRole('button', { name: 'Seed stock' }).click();
  await expect(page.getByTestId('otc-stock')).toContainText('stocked');

  // 2. quote for seeded customer/product
  await page.getByRole('button', { name: 'Create quote' }).click();
  await expect(page.getByTestId('otc-quote')).toContainText(/quote \S+ created \(status: \S+\)/);

  // 3. convert → order (grand total snapshot-priced: 2 × 9.99)
  await page.getByRole('button', { name: 'Convert to order' }).click();
  await expect(page.getByTestId('otc-order')).toContainText(/order \S+ status/);
  await expect(page.getByTestId('otc-order')).toContainText('19.98');

  // 4. approve
  await page.getByRole('button', { name: 'Approve' }).click();
  await expect(page.getByTestId('otc-order')).toContainText('ORDER_APPROVED');

  // 5. invoice + capture payment
  await page.getByRole('button', { name: 'Invoice + capture' }).click();
  await expect(page.getByTestId('otc-billing')).toContainText(/invoice \S+/);
  await expect(page.getByTestId('otc-billing')).toContainText(/payment \S+ status: \S+/);

  // 6. GL visibility (balanced-posting invariant is finance-svc's own test)
  await page.getByRole('button', { name: 'Show trial balance' }).click();
  await expect(page.getByTestId('otc-gl')).toContainText(/trial balance/);
});
