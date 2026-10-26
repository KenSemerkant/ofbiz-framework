import { test, expect } from '@playwright/test';

// P9-F5..F9 long-tail parity: every domain find screen answers through the
// gateway — row count is surface-mandatory (fresh clusters start empty).
const LISTS = [
  'positions', 'employments', 'contents', 'campaigns', 'tracking-codes',
  'opportunities', 'routings', 'mrp-proposals', 'workefforts', 'timesheets',
];

test('long-tail find screens all answer', async ({ page }) => {
  await page.goto('/p6');
  for (const id of LISTS) {
    await page.getByTestId(`load-${id}`).click();
    await expect(page.getByTestId(`count-${id}`)).not.toBeEmpty();
  }
});
