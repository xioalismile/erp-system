export const LOCATOR_TIMEOUT = 30000;
export const NAVIGATION_TIMEOUT = 60000;

export async function waitForPageReady(page) {
  await page.waitForLoadState('networkidle', { timeout: NAVIGATION_TIMEOUT });
  await page.waitForTimeout(1000);
}

export async function login(page, username = 'admin', password = 'admin123') {
  await page.goto('/');
  await waitForPageReady(page);
  
  await page.fill('input[placeholder="用户名"]', username, { timeout: LOCATOR_TIMEOUT });
  await page.fill('input[placeholder="密码"]', password, { timeout: LOCATOR_TIMEOUT });
  await page.click('button', { timeout: LOCATOR_TIMEOUT });
  
  await page.waitForURL('**/dashboard', { timeout: NAVIGATION_TIMEOUT });
  await waitForPageReady(page);
}

export async function selectDropdownOption(page, selectLocator, optionLabel, fieldName) {
  const select = page.locator(selectLocator);
  await select.waitFor({ state: 'visible', timeout: LOCATOR_TIMEOUT });
  await select.click();
  
  const option = page.locator(`.el-select-dropdown__item:has-text("${optionLabel}")`);
  await option.waitFor({ state: 'visible', timeout: LOCATOR_TIMEOUT });
  await option.click();
  
  await page.waitForTimeout(500);
  
  const selectedValue = await select.inputValue();
  if (!selectedValue) {
    throw new Error(`${fieldName}: 下拉选择失败，未选中任何值`);
  }
  return selectedValue;
}

export async function verifyFieldValue(page, fieldLocator, expectedValue, fieldName) {
  const field = page.locator(fieldLocator);
  await field.waitFor({ state: 'visible', timeout: LOCATOR_TIMEOUT });
  const actualValue = await field.inputValue();
  
  if (actualValue !== expectedValue) {
    throw new Error(`${fieldName}: 期望值 "${expectedValue}"，实际值 "${actualValue}"`);
  }
  return actualValue;
}

export async function verifyFieldContains(page, fieldLocator, expectedSubstring, fieldName) {
  const field = page.locator(fieldLocator);
  await field.waitFor({ state: 'visible', timeout: LOCATOR_TIMEOUT });
  const actualValue = await field.inputValue();
  
  if (!actualValue.includes(expectedSubstring)) {
    throw new Error(`${fieldName}: 期望包含 "${expectedSubstring}"，实际值 "${actualValue}"`);
  }
  return actualValue;
}

export async function clickButton(page, buttonText) {
  const button = page.locator(`button:has-text("${buttonText}")`);
  await button.waitFor({ state: 'visible', timeout: LOCATOR_TIMEOUT });
  await button.click();
}

export async function waitForToast(page, message) {
  const toast = page.locator(`.el-message:has-text("${message}")`);
  await toast.waitFor({ state: 'visible', timeout: LOCATOR_TIMEOUT });
  await page.waitForTimeout(1000);
}

export async function waitForDialog(page) {
  await page.locator('.el-dialog').waitFor({ state: 'visible', timeout: LOCATOR_TIMEOUT });
}

export async function closeDialog(page) {
  const closeBtn = page.locator('.el-dialog__headerbtn .el-dialog__close');
  await closeBtn.waitFor({ state: 'visible', timeout: LOCATOR_TIMEOUT });
  await closeBtn.click();
  await page.waitForTimeout(500);
}

export async function selectMenu(page, menuPath) {
  const menuItems = menuPath.split(' > ');
  
  for (let i = 0; i < menuItems.length; i++) {
    const menuItem = page.locator(`.el-menu-item:has-text("${menuItems[i]}")`);
    await menuItem.waitFor({ state: 'visible', timeout: LOCATOR_TIMEOUT });
    await menuItem.click();
    await waitForPageReady(page);
  }
}

export async function addTableItem(page, addButtonText) {
  const addBtn = page.locator(`button:has-text("${addButtonText}")`);
  await addBtn.waitFor({ state: 'visible', timeout: LOCATOR_TIMEOUT });
  await addBtn.click();
  await page.waitForTimeout(500);
}
