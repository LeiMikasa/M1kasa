import {expect, test, type Page} from '@playwright/test';

async function gateway(page: Page, options: {anonymous?: boolean; total?: number} = {}) {
  const state = {total: options.total ?? 11, failure: 0, malformed: false, calls: [] as {target: string; current: number; size: string | null; authorization?: string}[]};
  if (!options.anonymous) await page.addInitScript(() => localStorage.setItem('m1kasa.token', 'common-test-token'));
  await page.route('**/*', async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (/fonts\.(googleapis|gstatic)\.com/.test(url.hostname)) { await route.abort(); return; }
    if (!/^\/(user|shop|shop-type|voucher|blog|pay|order)(\/|$)/.test(url.pathname)) { await route.continue(); return; }
    const headers = {'access-control-allow-origin': '*', 'access-control-allow-headers': 'authorization,content-type', 'access-control-allow-methods': 'GET,POST,PUT,OPTIONS'};
    const reply = (data: unknown, status = 200, errorMsg: string | null = null) => route.fulfill({status, headers, contentType: 'application/json', body: JSON.stringify({success: status < 400, data, errorMsg, total: null})});
    if (request.method() === 'OPTIONS') { await route.fulfill({status: 204, headers}); return; }
    if (url.pathname === '/user/me') { await reply({id: '1011', nickName: 'mikasa', icon: null}); return; }
    if (url.pathname === '/user/login') { await reply('common-test-token'); return; }
    if (url.pathname === '/shop-type/list') { await reply([]); return; }
    if (url.pathname === '/blog/feed') {
      await reply({records: [{id: '501', userId: '1012', title: '巷口的第一杯咖啡', authorName: '山间风物', authorIcon: null, coverImage: null, liked: 3, comments: 0, createTime: '2026-11-08T12:00:00'}], total: 1, current: 1, size: 10}); return;
    }
    const publicProfile = url.pathname.match(/^\/user\/public\/(\d+)$/);
    if (publicProfile) { const userId = publicProfile[1]; await reply({userId, nickName: userId === '1012' ? '山间风物' : userId === '1011' ? 'mikasa' : '共同作者' + userId, icon: null, followingCount: 23, followersCount: 12}); return; }
    if (/^\/blog\/of\/user\/\d+$/.test(url.pathname)) { await reply({records: [], total: 0, current: 1, size: 10}); return; }
    const follow = url.pathname.match(/^\/user\/(\d+)\/follow$/);
    if (follow) { await reply({targetUserId: follow[1], followed: true}); return; }
    const common = url.pathname.match(/^\/user\/(\d+)\/common-following$/);
    if (common) {
      const current = Number(url.searchParams.get('current'));
      state.calls.push({target: common[1], current, size: url.searchParams.get('size'), authorization: request.headers()['authorization']});
      if (state.failure) { await reply(null, state.failure, state.failure === 401 ? '未登录' : '共同关注暂不可用，请稍后重试'); return; }
      if (state.malformed) { await reply({records: [{userId: '__READY__', nickName: '错误数据', icon: null}], total: 1, current, size: 10}); return; }
      const offset = (current - 1) * 10;
      const records = Array.from({length: Math.min(10, Math.max(0, state.total - offset))}, (_, i) => ({userId: String(2001 + offset + i), nickName: '共同作者' + (2001 + offset + i), icon: null}));
      await reply({records, total: state.total, current, size: 10}); return;
    }
    await reply(null, 404, '未配置的测试接口');
  });
  return state;
}

async function openAuthor(page: Page) {
  await page.goto('/');
  await page.getByRole('button', {name: '查看山间风物的主页', exact: true}).click();
  await expect(page.getByRole('heading', {name: '山间风物', exact: true})).toBeVisible();
}

async function openCommon(page: Page) {
  await openAuthor(page);
  await page.getByTestId('common-following-open').click();
}

test('common following sends the target and auth, pages results, and restores the page after visiting another profile', async ({page}, info) => {
  const state = await gateway(page);
  await openAuthor(page);
  expect(state.calls).toHaveLength(0);
  await page.mouse.move(20, 20);
  await page.screenshot({path: info.outputPath('common-entry.png')});
  await page.getByTestId('common-following-open').click();
  const list = page.getByTestId('common-following-list');
  await expect(list).toContainText('共同关注 11 人');
  await expect(page.getByTestId('common-following-person')).toHaveCount(10);
  expect(state.calls[0]).toEqual({target: '1012', current: 1, size: '10', authorization: 'Bearer common-test-token'});
  await page.getByRole('navigation', {name: '共同关注分页'}).getByRole('button', {name: '下一页'}).click();
  await expect(page.getByTestId('common-following-person')).toHaveCount(1);
  await expect(list).toContainText('第 2 / 2 页');
  await page.mouse.move(20, 20);
  await page.screenshot({path: info.outputPath('common-page.png')});
  await page.getByRole('button', {name: '查看共同作者2011的主页'}).click();
  await expect(page.getByRole('heading', {name: '共同作者2011', exact: true})).toBeVisible();
  await page.getByRole('button', {name: '返回上一页', exact: true}).click();
  await expect(list).toContainText('第 2 / 2 页');
  await expect(list).toContainText('山间风物');
  await page.getByRole('button', {name: '返回上一页', exact: true}).click();
  await expect(page.getByRole('heading', {name: '山间风物', exact: true})).toBeVisible();
});

test('anonymous visitors do not query protected data and resume the selected target after login', async ({page}) => {
  const state = await gateway(page, {anonymous: true});
  await openCommon(page);
  await expect(page.getByRole('button', {name: '登录 / 注册', exact: true})).toBeVisible();
  expect(state.calls).toHaveLength(0);
  await page.getByLabel('手机号', {exact: true}).fill('13800138000');
  await page.getByLabel('验证码', {exact: true}).fill('123456');
  await page.locator('.login-body form').evaluate(form => (form as HTMLFormElement).requestSubmit());
  await expect(page.getByTestId('common-following-list')).toContainText('山间风物');
  await expect(page.getByTestId('common-following-person')).toHaveCount(10);
  expect(state.calls[0].target).toBe('1012');
});

test('a failed or malformed response remains an error, and a real empty result has its own state', async ({page}) => {
  const state = await gateway(page);
  state.failure = 503;
  await openCommon(page);
  const list = page.getByTestId('common-following-list');
  await expect(list.getByRole('alert')).toContainText('共同关注暂不可用');
  await expect(list.getByText('还没有共同关注', {exact: true})).toHaveCount(0);
  state.failure = 0; state.malformed = true;
  await list.getByRole('button', {name: '重新加载', exact: true}).click();
  await expect(list.getByRole('alert')).toContainText('返回格式不正确');
  state.malformed = false; state.total = 0;
  await list.getByRole('button', {name: '重新加载', exact: true}).click();
  await expect(list.getByRole('heading', {name: '还没有共同关注'})).toBeVisible();
  await expect(list.getByRole('alert')).toHaveCount(0);
});

test('relation changes that remove the last page redirect to the current last page', async ({page}) => {
  const state = await gateway(page);
  await openCommon(page);
  await page.getByRole('navigation', {name: '共同关注分页'}).getByRole('button', {name: '下一页'}).click();
  await expect(page.getByTestId('common-following-list')).toContainText('第 2 / 2 页');
  state.total = 1;
  await page.getByTestId('common-following-list').getByRole('button', {name: '刷新列表'}).click();
  await expect(page.getByTestId('common-following-list')).toContainText('第 1 / 1 页');
  await expect(page.getByTestId('common-following-person')).toHaveCount(1);
  expect(state.calls.at(-1)?.current).toBe(1);
});

test('an expired session removes private results and offers login without showing an empty result', async ({page}) => {
  const state = await gateway(page, {total: 1});
  await openCommon(page);
  await expect(page.getByTestId('common-following-person')).toHaveCount(1);
  state.failure = 401;
  await page.getByTestId('common-following-list').getByRole('button', {name: '刷新列表'}).click();
  await expect(page.getByRole('button', {name: '登录后查看', exact: true})).toBeVisible();
  await expect(page.getByTestId('common-following-person')).toHaveCount(0);
  expect(await page.evaluate(() => localStorage.getItem('m1kasa.token'))).toBeNull();
});

test('own profiles omit common following and Pixel keeps the list inside the app viewport', async ({page}, info) => {
  await gateway(page, {total: 1});
  await page.goto('/');
  await page.getByRole('navigation', {name: '主导航'}).getByRole('button', {name: '我的', exact: true}).click();
  await page.getByRole('button', {name: '我的主页', exact: true}).click();
  await expect(page.getByRole('heading', {name: 'mikasa', exact: true})).toBeVisible();
  await expect(page.getByTestId('common-following-open')).toHaveCount(0);
  await page.getByRole('button', {name: '返回上一页', exact: true}).click();
  await openCommon(page);
  await page.getByTestId('device-picker').click();
  await page.getByTestId('device-option-pixel-10').click();
  await expect(page.getByTestId('common-following-person')).toHaveCount(1);
  const row = await page.getByTestId('common-following-person').boundingBox();
  const viewport = await page.locator('.mobile-app-viewport').boundingBox();
  expect(row).not.toBeNull(); expect(viewport).not.toBeNull();
  expect(row!.x).toBeGreaterThanOrEqual(viewport!.x);
  expect(row!.x + row!.width).toBeLessThanOrEqual(viewport!.x + viewport!.width + 1);
  await page.mouse.move(20, 20);
  await page.screenshot({path: info.outputPath('common-pixel.png')});
});
