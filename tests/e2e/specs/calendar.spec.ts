import { test, expect } from '@playwright/test';
import { runPrefix } from '../helpers/ids';

test('calendar: create work effort → iCal feed contains it', async ({ page }) => {
  const run = runPrefix();
  const effortId = run + 'we';
  await page.goto('/calendar');

  await page.getByLabel('Work effort ID').fill(effortId);
  await page.getByLabel('Name').fill('e2e calendar effort');
  await page.getByRole('button', { name: 'Create effort' }).click();
  await expect(page.getByTestId('cal-effort')).toContainText(`effort ${effortId}`);

  await page.getByRole('button', { name: 'Load feed' }).click();
  await expect(page.getByTestId('cal-feed')).toContainText('BEGIN:VCALENDAR');
  await expect(page.getByTestId('cal-feed')).toContainText(effortId);
});
