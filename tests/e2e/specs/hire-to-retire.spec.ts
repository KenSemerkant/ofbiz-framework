import { test, expect } from '@playwright/test';
import { runPrefix } from '../helpers/ids';

test('hire-to-retire: position → onboarding saga → read back → retire position', async ({ page }) => {
  const run = runPrefix();
  const positionId = run + 'pos';
  await page.goto('/hire-to-retire');

  // 1. create an active position
  await page.getByLabel('Position ID').fill(positionId);
  await page.getByRole('button', { name: 'Create position' }).click();
  await expect(page.getByTestId('htr-position')).toContainText(`position ${positionId}`);

  // 2. start the onboarding saga for seeded party P1 (saga adds EMPLOYEE role + employment).
  //    Domain truth: OnboardingSagaStore.Status { PENDING, ROLE_ADDED, COMPLETED, FAILED };
  //    start() drives both steps synchronously and returns the final saga row, so the
  //    terminal COMPLETED status is asserted (a FAILED row fails here loudly).
  await page.getByRole('button', { name: 'Start onboarding' }).click();
  await expect(page.getByTestId('htr-onboarding')).toContainText(/onboarding \S+ status COMPLETED/);

  // 3. server readback of saga state + steps
  await page.getByRole('button', { name: 'Read back' }).click();
  await expect(page.getByTestId('htr-readback')).toContainText(/steps:/);

  // 4. retire: deactivate the position (PUT, 30-verify-p6.sh proven shape)
  await page.getByRole('button', { name: 'Retire position' }).click();
  await expect(page.getByTestId('htr-position')).toContainText('INACTIVE');
});
