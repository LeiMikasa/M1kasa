import { expect, test, type Page } from '@playwright/test';

const payNo = 'a'.repeat(32);
const markerKey = 'm1kasa.payment.pending:1011:6171';

async function mockGateway(page: Page, options: { createError?: number; confirmError?: number; paymentPaid?: boolean; orderStatus?: number; amount?: number; expired?: boolean; stallConfirmation?: boolean } = {}) {
  const state = { createError: 0, confirmError: 0, paymentPaid: false, orderStatus: 1, amount: 880, expired: false, stallConfirmation: false,
    creates: 0, confirms: 0, queries: 0, submittedOrderId: '', ...options };
  await page.addInitScript(() => { localStorage.setItem('m1kasa.token', 'payment-test-token'); });
  await page.route('**/*', async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (/fonts\.(googleapis|gstatic)\.com/.test(url.hostname)) { await route.abort(); return; }
    if (!/^\/(user|shop|shop-type|voucher|blog|pay|order)(\/|$)/.test(url.pathname)) { await route.continue(); return; }
    const headers = {'access-control-allow-origin': '*', 'access-control-allow-headers': 'authorization,content-type', 'access-control-allow-methods': 'GET,POST,OPTIONS'};
    const reply = (data: unknown, status = 200, errorMsg: string | null = null) => route.fulfill({
      status, headers, contentType: 'application/json', body: JSON.stringify({ success: status < 400, errorMsg, data, total: null })
    });
    if (request.method() === 'OPTIONS') { await route.fulfill({status: 204, headers}); return; }
    const order = { id: '6171', orderNo: 'MK0123456789abcdef0123456789abcdef', voucherId: '10', status: state.orderStatus, createTime: '2026-11-08T12:00:00' };
    const shop = {id: 1, name: '巷口咖啡', typeId: 1, images: null, area: '街角', address: '巷里路1号', avgPrice: 20, score: 45, comments: 3, openHours: '09:00–21:00', distance: null};
    if (url.pathname === '/user/me') { await reply({id: '1011', nickName: 'mikasa', icon: null}); return; }
    if (url.pathname === '/shop-type/list') { await reply([{id: 1, name: '美食', icon: null}]); return; }
    if (url.pathname === '/shop/of/type') { await reply([shop]); return; }
    if (url.pathname === '/shop/1') { await reply(shop); return; }
    if (url.pathname === '/voucher/list/1') { await reply([{id: 10, shopId: 1, title: '巷口咖啡代金券', subTitle: '给今天一点小确幸', rules: '到店使用', payValue: 990, actualValue: 2000, type: 1, status: 1, stock: 10, beginTime: '2020-01-01T00:00:00', endTime: '2099-12-31T23:59:59'}]); return; }
    if (url.pathname === '/blog/feed') { await reply({records: [], total: 0, current: 1, size: 10}); return; }
    if (url.pathname === '/order/of/voucher/10') { await reply(order); return; }
    if (url.pathname === '/order/6171') {
      state.queries++;
      if (state.expired) { await reply(null, 401, '未登录'); return; }
      await reply(order); return;
    }
    if (url.pathname === '/pay/orders') {
      state.creates++; state.submittedOrderId = request.postDataJSON().orderId;
      if (state.createError) { await reply(null, state.createError, '当前订单不可支付'); return; }
      await reply({payNo, orderId: '6171', payAmountFen: state.amount, status: state.paymentPaid ? 1 : 0, createTime: '2026-11-08T12:01:00', paidTime: state.paymentPaid ? '2026-11-08T12:02:00' : null});
      return;
    }
    if (url.pathname === '/pay/mock/orders/' + payNo + '/confirm') {
      state.confirms++;
      if (state.stallConfirmation) return;
      if (state.confirmError) { await reply(null, state.confirmError, '付款结果暂无法确认'); return; }
      await reply({payNo, channelTradeNo: 'b'.repeat(32), payAmountFen: state.amount, paidTime: '2026-11-08T12:02:00'});
      return;
    }
    await reply(null, 404, '未配置的测试接口');
  });
  return state;
}

async function openVoucher(page: Page) {
  await page.goto('/');
  await page.getByRole('navigation', {name: '主导航'}).getByRole('button', {name: '附近', exact: true}).click();
  await page.locator('.live-shop-card').first().click();
  await page.getByRole('button', {name: '查看详情', exact: true}).click();
  await expect(page.getByTestId('order-result')).toBeVisible();
}

test('payment uses the server amount, prevents duplicate confirmation, and waits for the owned order to become paid', async ({page}, testInfo) => {
  const state = await mockGateway(page);
  await openVoucher(page);
  await page.getByTestId('payment-open').click();
  await expect(page.getByTestId('payment-amount')).toContainText('8.80');
  expect(state.submittedOrderId).toBe('6171');
  expect(state.confirms).toBe(0);
  await page.mouse.move(20, 20); await page.waitForTimeout(500);
  await page.screenshot({path: testInfo.outputPath('payment-ready.png')});
  await page.getByTestId('payment-confirm').evaluate(button => { (button as HTMLButtonElement).click(); (button as HTMLButtonElement).click(); });
  await expect(page.getByTestId('payment-pending')).toBeVisible();
  await expect(page.getByTestId('payment-success')).toHaveCount(0);
  expect(state.confirms).toBe(1);
  state.orderStatus = 2;
  await expect(page.getByTestId('payment-success')).toBeVisible({timeout: 8000});
  await expect.poll(() => page.evaluate(key => localStorage.getItem(key), markerKey)).toBeNull();
  await page.mouse.move(20, 20); await page.waitForTimeout(500);
  await page.screenshot({path: testInfo.outputPath('payment-paid.png')});
});

test('failed payment-order creation exposes a retry and never sends a payment confirmation', async ({page}) => {
  const state = await mockGateway(page, {createError: 409});
  await openVoucher(page);
  await page.getByTestId('payment-open').click();
  await expect(page.getByTestId('payment-sheet')).toContainText('当前订单不可支付');
  await expect(page.getByTestId('payment-confirm')).toHaveCount(0);
  expect(state.confirms).toBe(0);
  state.createError = 0;
  await page.getByRole('button', {name: '重新获取支付单', exact: true}).click();
  await expect(page.getByTestId('payment-confirm')).toBeVisible();
});

test('an uncertain confirmation survives navigation/reload and resumes querying without another confirmation POST', async ({page}) => {
  const state = await mockGateway(page, {confirmError: 503});
  await openVoucher(page);
  await page.getByTestId('payment-open').click();
  await page.getByTestId('payment-confirm').click();
  await expect(page.getByTestId('payment-pending')).toBeVisible();
  await expect.poll(() => page.evaluate(key => Boolean(localStorage.getItem(key)), markerKey)).toBe(true);
  await openVoucher(page);
  await expect(page.getByTestId('payment-open')).toContainText('查看付款进度');
  expect(state.confirms).toBe(1);
  state.orderStatus = 2;
  await expect(page.getByTestId('payment-success')).toBeVisible({timeout: 8000});
  expect(state.confirms).toBe(1);
});

test('an already-paid payment order only checks the order and does not charge again', async ({page}) => {
  const state = await mockGateway(page, {paymentPaid: true});
  await openVoucher(page);
  await page.getByTestId('payment-open').click();
  await expect(page.getByTestId('payment-pending')).toBeVisible();
  await expect(page.getByTestId('payment-confirm')).toHaveCount(0);
  await expect(page.getByTestId('payment-success')).toHaveCount(0);
  expect(state.confirms).toBe(0);
  state.orderStatus = 2;
  await expect(page.getByTestId('payment-success')).toBeVisible({timeout: 8000});
});

test('a cancelled order has no pay action', async ({page}) => {
  const state = await mockGateway(page, {orderStatus: 3});
  await openVoucher(page);
  await expect(page.getByTestId('payment-open')).toHaveCount(0);
  await expect(page.getByTestId('order-result')).toContainText('订单已取消');
  expect(state.creates).toBe(0);
});

test('invalid server amounts cannot be confirmed', async ({page}) => {
  const state = await mockGateway(page, {amount: -1});
  await openVoucher(page);
  await page.getByTestId('payment-open').click();
  await expect(page.getByTestId('payment-sheet')).toContainText('支付单信息不完整');
  await expect(page.getByTestId('payment-confirm')).toHaveCount(0);
  expect(state.confirms).toBe(0);
});

test('an expired session removes payment UI and keeps the unresolved attempt scoped to its original user', async ({page}) => {
  await mockGateway(page, {expired: true});
  await openVoucher(page);
  await page.getByTestId('payment-open').click();
  await page.getByTestId('payment-confirm').click();
  await expect(page.getByTestId('payment-open')).toHaveCount(0);
  await expect.poll(() => page.evaluate(() => localStorage.getItem('m1kasa.token'))).toBeNull();
  expect(await page.evaluate(key => Boolean(localStorage.getItem(key)), markerKey)).toBe(true);
  await expect(page.getByTestId('payment-success')).toHaveCount(0);
});

test('a confirmation timeout keeps its original payment reference and only queries the result', async ({page}) => {
  const state = await mockGateway(page, {stallConfirmation: true});
  await page.clock.install();
  await openVoucher(page);
  await page.getByTestId('payment-open').click();
  await page.getByTestId('payment-confirm').click();
  await expect.poll(() => state.confirms).toBe(1);
  await page.clock.fastForward(12500);
  await expect(page.getByTestId('payment-pending')).toBeVisible();
  await expect(page.getByTestId('payment-success')).toHaveCount(0);
  const saved = await page.evaluate(key => JSON.parse(localStorage.getItem(key) || 'null'), markerKey);
  expect(saved.payNo).toBe(payNo);
  expect(saved.phase).toBe('submitted');
  expect(state.confirms).toBe(1);
  state.orderStatus = 2;
  await page.clock.fastForward(2100);
  await expect(page.getByTestId('payment-success')).toBeVisible();
  expect(state.confirms).toBe(1);
});

test('the payment sheet and confirmation action stay inside the Pixel app viewport', async ({page}, testInfo) => {
  await mockGateway(page);
  await openVoucher(page);
  await page.getByTestId('device-picker').click();
  await page.getByTestId('device-option-pixel-10').click();
  await page.getByTestId('payment-open').click();
  await expect(page.getByTestId('payment-confirm')).toBeVisible();
  await page.mouse.move(20, 20);
  await page.waitForTimeout(500);
  const action = await page.getByTestId('payment-confirm').boundingBox();
  const viewport = await page.locator('.mobile-app-viewport').boundingBox();
  expect(action).not.toBeNull();
  expect(viewport).not.toBeNull();
  expect(action!.y).toBeGreaterThanOrEqual(viewport!.y);
  expect(action!.y + action!.height).toBeLessThanOrEqual(viewport!.y + viewport!.height + 1);
  await page.screenshot({path: testInfo.outputPath('payment-pixel.png')});
});
