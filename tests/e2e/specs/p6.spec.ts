import { test, expect } from '@playwright/test';
import { runPrefix } from '../helpers/ids';

test('create + read back a work effort via the generic CRUD screen', async ({ page }) => {
  const id = runPrefix() + 'we';
  await page.goto('/p6');
  const effort = page.getByRole('heading', { name: 'Work effort (workeffort)' })
    .locator('..');
  await effort.getByLabel('id').fill(id);
  await effort.getByLabel('type').fill('TASK');
  await effort.getByLabel('name').fill('e2e work effort');
  await effort.getByRole('button', { name: 'create' }).click();
  await expect(effort.getByTestId('p6-result-workEffortId')).toContainText('saved + read back');
});
