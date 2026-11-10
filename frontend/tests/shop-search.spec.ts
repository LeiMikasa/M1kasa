import {expect, test, type Page} from '@playwright/test';

async function gateway(page: Page, options: {total?: number; failure?: number} = {}) {
  const state = {total: options.total ?? 12, failure: options.failure ?? 0, malformed: '', delayed: '',
    calls: [] as {keyword: string; current: number; size: string | null; origin: string; authorization?: string}[],
    details: [] as string[], legacy: 0};
  const shop = (id: number, keyword = '') => ({id: String(id), typeId: '2', name: (keyword || '西湖咖啡') + ' ' + id,
    images: null, area: '西湖区', address: '文三路100号', avgPrice: 50, score: 45, comments: 8, openHours: '09:00–21:00',
    location: {lon: 120.15, lat: 30.28}, sold: 10});
  await page.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url());
    if (/fonts\.(googleapis|gstatic)\.com/.test(url.hostname)) {await route.abort(); return;}
    if (!/^\/(user|shop|shop-type|search|voucher|blog|pay|order)(\/|$)/.test(url.pathname)) {await route.continue(); return;}
    const headers = {'access-control-allow-origin': '*', 'access-control-allow-headers': 'authorization,content-type', 'access-control-allow-methods': 'GET,POST,OPTIONS'};
    const reply = (data: unknown, status = 200, errorMsg: string | null = null) => route.fulfill({status, headers,
      contentType: 'application/json', body: JSON.stringify({success: status < 400, data, errorMsg, total: null})});
    if (request.method() === 'OPTIONS') {await route.fulfill({status: 204, headers}); return;}
    if (url.pathname === '/shop-type/list') {await reply([{id: 2, name: '咖啡', icon: null}]); return;}
    if (url.pathname === '/blog/feed') {await reply({records: [], total: 0, current: 1, size: 10}); return;}
    if (url.pathname === '/shop/of/type') {await reply([{...shop(101), id: 101, typeId: 2, distance: null}]); return;}
    if (url.pathname === '/shop/of/name') {state.legacy++; await reply([]); return;}
    if (url.pathname === '/search/shops') {
      const keyword = url.searchParams.get('keyword') || '', current = Number(url.searchParams.get('current'));
      state.calls.push({keyword, current, size: url.searchParams.get('size'), origin: url.origin, authorization: request.headers()['authorization']});
      if (state.failure) {await reply(null, state.failure, '搜索暂不可用，请稍后重试'); return;}
      if (state.malformed === 'null') {await reply(null); return;}
      if (state.malformed === 'array') {await reply([]); return;}
      const offset = (current - 1) * 10;
      const records = Array.from({length: Math.min(10, Math.max(0, state.total - offset))}, (_, index) => shop(101 + offset + index, keyword));
      if (state.malformed === 'id' && records[0]) records[0].id = '9007199254740993';
      if (state.delayed === keyword && keyword) await new Promise(resolve => setTimeout(resolve, 1200));
      await reply({records, total: state.total, current, size: state.malformed === 'page' ? 20 : 10}); return;
    }
    const detail = url.pathname.match(/^\/shop\/(\d+)$/);
    if (detail) {state.details.push(detail[1]); await reply({...shop(Number(detail[1])), id: Number(detail[1]), typeId: 2, distance: null}); return;}
    if (/^\/voucher\/list\/\d+$/.test(url.pathname)) {await reply([]); return;}
    await reply(null, 404, '未配置的测试接口');
  });
  return state;
}

async function openSearch(page: Page) {
  await page.goto('/');
  await page.getByRole('button', {name: '搜索店铺', exact: true}).click();
}

test('anonymous search uses the gateway page contract, normalizes IDs, and preserves the last page after shop details', async ({page}, info) => {
  const state = await gateway(page);
  await openSearch(page);
  const results = page.getByTestId('shop-search-results');
  await expect(results.locator('.live-shop-card')).toHaveCount(10);
  await expect(results).toContainText('找到 12 家店铺');
  await expect(results.locator('.rating').first()).toContainText('4.5');
  await expect(results.locator('.rating').first()).toContainText('人均 ¥50');
  expect(state.calls[0]).toEqual({keyword: '', current: 1, size: '10', origin: 'http://localhost:8080', authorization: undefined});
  await expect(page.getByRole('navigation', {name: '店铺搜索分页'})).toContainText('第 1 / 2 页');
  await page.getByRole('navigation', {name: '店铺搜索分页'}).getByRole('button', {name: '下一页'}).click();
  await expect(results.locator('.live-shop-card')).toHaveCount(2);
  await expect(page.getByRole('navigation', {name: '店铺搜索分页'}).getByRole('button', {name: '下一页'})).toBeDisabled();
  await page.mouse.move(20, 20);
  await page.screenshot({path: info.outputPath('search-iphone.png'), animations: 'disabled'});
  await results.locator('.live-shop-card').first().click();
  await expect(page.getByRole('heading', {name: '西湖咖啡 111.', exact: true})).toBeVisible();
  expect(state.details.at(-1)).toBe('111');
  await page.getByRole('button', {name: '返回', exact: true}).click();
  await expect(page.getByRole('navigation', {name: '店铺搜索分页'})).toContainText('第 2 / 2 页');
  await expect(page.getByTestId('shop-search-results').locator('.live-shop-card')).toHaveCount(2);
  expect(state.legacy).toBe(0);
});

test('a changed keyword is encoded, debounced, clears old results and resets pagination', async ({page}) => {
  const state = await gateway(page);
  await openSearch(page);
  await expect(page.locator('.live-shop-card')).toHaveCount(10);
  await page.getByRole('navigation', {name: '店铺搜索分页'}).getByRole('button', {name: '下一页'}).click();
  await expect(page.locator('.live-shop-card')).toHaveCount(2);
  await page.getByRole('textbox', {name: '搜索店铺关键词'}).fill('杭州 & 咖啡');
  await expect(page.locator('.live-shop-card')).toHaveCount(0);
  await expect(page.getByTestId('shop-search-results').locator('.live-shop-card')).toHaveCount(10);
  expect(state.calls.at(-1)).toMatchObject({keyword: '杭州 & 咖啡', current: 1, size: '10'});
  await expect(page.getByRole('navigation', {name: '店铺搜索分页'})).toContainText('第 1 / 2 页');
  await expect(page.locator('.live-shop-card').first()).toContainText('杭州 & 咖啡');
  expect(state.legacy).toBe(0);
});

test('server failures and malformed responses remain retryable errors, not empty results', async ({page}) => {
  const state = await gateway(page, {failure: 503});
  await openSearch(page);
  const results = page.getByTestId('shop-search-results');
  await expect(results.getByRole('alert')).toContainText('搜索暂不可用');
  await expect(results).not.toContainText('没有找到相关店铺');
  state.failure = 0;
  for (const malformed of ['null', 'array', 'page', 'id']) {
    state.malformed = malformed;
    await results.getByRole('button', {name: '重新加载', exact: true}).click();
    await expect(results.getByRole('alert')).toContainText('返回格式不正确');
    await expect(results.locator('.live-shop-card')).toHaveCount(0);
    await expect(results).not.toContainText('没有找到相关店铺');
  }
  state.malformed = '';
  await results.getByRole('button', {name: '重新加载', exact: true}).click();
  await expect(results.locator('.live-shop-card')).toHaveCount(10);
  expect(state.legacy).toBe(0);
});

test('an empty result is distinct from an error and has no forward pagination', async ({page}) => {
  await gateway(page, {total: 0});
  await openSearch(page);
  const results = page.getByTestId('shop-search-results');
  await expect(results).toContainText('没有找到相关店铺');
  await expect(results.getByRole('alert')).toHaveCount(0);
  await expect(results.locator('.live-shop-card')).toHaveCount(0);
  await expect(page.getByRole('navigation', {name: '店铺搜索分页'})).toHaveCount(0);
});

test('a slow old keyword cannot replace a newer search', async ({page}) => {
  const state = await gateway(page);
  await openSearch(page);
  await expect(page.locator('.live-shop-card')).toHaveCount(10);
  state.delayed = '旧词';
  const input = page.getByRole('textbox', {name: '搜索店铺关键词'});
  await input.fill('旧词');
  await expect.poll(() => state.calls.at(-1)?.keyword).toBe('旧词');
  await input.fill('新词');
  await expect(page.locator('.live-shop-card').first()).toContainText('新词');
  await page.waitForTimeout(1300);
  await expect(page.locator('.live-shop-card').first()).toContainText('新词');
  await expect(page.getByTestId('shop-search-results')).not.toContainText('旧词 101');
});

test('a changed total redirects an out-of-range page to the last valid page', async ({page}) => {
  const state = await gateway(page);
  await openSearch(page);
  await expect(page.locator('.live-shop-card')).toHaveCount(10);
  state.total = 1;
  await page.getByRole('navigation', {name: '店铺搜索分页'}).getByRole('button', {name: '下一页'}).click();
  await expect(page.getByRole('navigation', {name: '店铺搜索分页'})).toContainText('第 1 / 1 页');
  await expect(page.locator('.live-shop-card')).toHaveCount(1);
  expect(state.calls.slice(-2).map(call => call.current)).toEqual([2, 1]);
});

test('the result window is bounded and Pixel keeps the fixed search header clear of results', async ({page}, info) => {
  await gateway(page, {total: 20_001});
  await openSearch(page);
  await expect(page.getByRole('navigation', {name: '店铺搜索分页'})).toContainText('第 1 / 1000 页');
  await expect(page.getByTestId('shop-search-results')).toContainText('缩小范围');
  await page.getByTestId('device-picker').click();
  await page.getByTestId('device-option-pixel-10').click();
  await expect(page.locator('.live-shop-card')).toHaveCount(10);
  const header = await page.locator('.shop-search-header').boundingBox();
  const card = await page.locator('.live-shop-card').first().boundingBox();
  const viewport = await page.locator('.mobile-app-viewport').boundingBox();
  expect(header).not.toBeNull(); expect(card).not.toBeNull(); expect(viewport).not.toBeNull();
  expect(card!.y).toBeGreaterThanOrEqual(header!.y + header!.height);
  expect(card!.x).toBeGreaterThanOrEqual(viewport!.x);
  expect(card!.x + card!.width).toBeLessThanOrEqual(viewport!.x + viewport!.width + 1);
  await page.mouse.move(20, 20);
  await page.screenshot({path: info.outputPath('search-pixel.png'), animations: 'disabled'});
});
