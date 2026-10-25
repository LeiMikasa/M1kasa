import { useCallback, useEffect, useRef, useState } from 'react';
import { ArrowLeftIcon, BookmarkIcon, CameraIcon, CheckIcon, ChevronRightIcon, ChatBubbleIcon, Cross2Icon, DrawingPinIcon as MapPinIcon, HeartFilledIcon, HeartIcon, HomeIcon, MagnifyingGlassIcon, PaperPlaneIcon, PersonIcon, PlusIcon, StarFilledIcon } from '@radix-ui/react-icons';
import { Carousel, KeyboardInput, KeyboardTextarea, MobileScroll, useKeyboard } from './mobile';
import './prototype.css';

type Page = 'home' | 'nearby' | 'publish' | 'messages' | 'profile' | 'story' | 'shop' | 'search' | 'checkout' | 'login';
type FeedTab = '推荐' | '附近' | '关注';
type Result<T> = { success: boolean; errorMsg: string | null; data: T; total: number | null };
type User = { id: number; nickName: string; icon: string | null };
type ShopType = { id: number; name: string; icon: string | null };
type Shop = { id: number; name: string; typeId: number; images: string | null; area: string | null; address: string; avgPrice: number | null; score: number | null; comments: number | null; openHours: string | null; distance: number | null };
type Voucher = { id: number; shopId: number; title: string; subTitle: string | null; rules: string | null; payValue: number; actualValue: number; type: number; status: number; stock: number | null; beginTime: string | null; endTime: string | null };

const API_BASE = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const TOKEN_KEY = 'm1kasa.token';
const AUTH_EVENT = 'm1kasa:unauthorized';
const asset = '/assets/stories/';
const posts = [
  { id: 1, photo: 'pasta-hero.png', author: '一颗栗子', time: '10月20日', location: '上海', title: '藏在老洋房里的意式小馆，晚餐时的氛围太加分了', excerpt: '和朋友临时起意来这家店，没想到出乎意料地好吃。手工意面酱汁浓郁但不腻，虎虾很新鲜，配一杯白葡萄酒，在老洋房的庭院里聊天，整个人都放松下来了。', shop: 'La Piccola 小房子意大利餐厅', likes: 326 },
  { id: 2, photo: 'cafe-interior.png', author: '阿栗散步中', time: '昨天', location: '上海 · 静安', title: '午后的老洋房，也值得来坐一坐。', excerpt: '不是赶路的日子，就在窗边喝杯咖啡，坐久一点。', shop: 'La Piccola 小房子意大利餐厅', likes: 86 },
];

class ApiError extends Error {
  constructor(message: string, public status: number) { super(message); }
}

// All business requests use the gateway; never fall back to demonstration data.
async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const controller = new AbortController();
  const cancel = () => controller.abort();
  if (options.signal?.aborted) controller.abort();
  options.signal?.addEventListener('abort', cancel, { once: true });
  const timeout = window.setTimeout(cancel, 12000);
  const headers = new Headers(options.headers);
  if (options.body) headers.set('Content-Type', 'application/json');
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) headers.set('Authorization', `Bearer ${token}`);
  try {
    const response = await fetch(`${API_BASE}${path}`, { ...options, headers, signal: controller.signal });
    if (response.status === 401) {
      // An old in-flight request must not clear a newer login session.
      if (token && localStorage.getItem(TOKEN_KEY) === token) {
        localStorage.removeItem(TOKEN_KEY);
        window.dispatchEvent(new Event(AUTH_EVENT));
      }
      throw new ApiError('登录已过期，请重新登录', 401);
    }
    if (response.status === 204) return undefined as T;
    const text = await response.text();
    let payload: Result<T> | { message?: string } | null = null;
    try { payload = text ? JSON.parse(text) : null; } catch { /* Report a readable protocol error below. */ }
    if (!response.ok) {
      const message = payload && 'errorMsg' in payload ? payload.errorMsg : null;
      throw new ApiError(message || (response.status >= 500 ? '服务暂时不可用，请稍后重试' : `请求失败（${response.status}）`), response.status);
    }
    if (!payload || !('success' in payload)) throw new ApiError('接口响应格式不正确，请检查服务配置', response.status);
    if (!payload.success) throw new ApiError(payload.errorMsg || '操作未完成，请重试', response.status);
    return payload.data;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError(controller.signal.aborted ? '请求已取消或超时，请重试' : '无法连接服务，请确认网关和对应后端已启动', 0);
  } finally {
    window.clearTimeout(timeout);
    options.signal?.removeEventListener('abort', cancel);
  }
}

function useResource<T>(path: string | null) {
  const [version, setVersion] = useState(0);
  const [state, setState] = useState<{ path: string | null; data: T | null; loading: boolean; error: string }>({ path: null, data: null, loading: false, error: '' });
  useEffect(() => {
    const controller = new AbortController();
    if (!path) { setState({ path, data: null, loading: false, error: '' }); return; }
    setState({ path, data: null, loading: true, error: '' });
    api<T>(path, { signal: controller.signal }).then(data => {
      if (!controller.signal.aborted) setState({ path, data, loading: false, error: '' });
    }).catch((error: Error) => {
      if (!controller.signal.aborted) setState({ path, data: null, loading: false, error: error.message });
    });
    return () => controller.abort();
  }, [path, version]);
  const reload = useCallback(() => setVersion(value => value + 1), []);
  return { ...(state.path === path ? state : { path, data: null, loading: Boolean(path), error: '' }), reload };
}

function money(cents: number) { return (cents / 100).toFixed(2); }
function activityTime(value: string | null) {
  if (!value) return NaN;
  return Date.parse(/(?:Z|[+-]\d{2}:?\d{2})$/.test(value) ? value : `${value}+08:00`);
}
function timeLabel(value: string | null) { return value ? value.replace('T', ' ').slice(0, 16) : '未设置'; }
function voucherState(voucher: Voucher, now: number) {
  if (voucher.status !== 1) return '已下架';
  if (voucher.type === 0) return '代金券';
  if (!Number.isFinite(activityTime(voucher.beginTime)) || !Number.isFinite(activityTime(voucher.endTime))) return '活动信息待完善';
  if (now >= activityTime(voucher.endTime)) return '已结束';
  if (now < activityTime(voucher.beginTime)) return '未开始';
  if (voucher.stock !== null && voucher.stock <= 0) return '已售罄';
  return '进行中';
}

function ShopImage({ shop, className = '' }: { shop: Shop; className?: string }) {
  const [failed, setFailed] = useState(false);
  const source = shop.images?.split(',')[0]?.trim();
  useEffect(() => setFailed(false), [source]);
  const url = source && (/^https?:\/\//i.test(source) ? source : `${API_BASE}/${source.replace(/^\/+/, '')}`);
  return url && !failed
    ? <img className={className} src={url} alt={`${shop.name}店铺图片`} referrerPolicy="no-referrer" onError={() => setFailed(true)} />
    : <div className={`shop-image-placeholder ${className}`} role="img" aria-label="店铺暂无可用图片"><MapPinIcon /><span>街角好店</span></div>;
}

function RequestState({ loading, error, empty, onRetry, emptyText = '暂时没有内容' }: { loading: boolean; error: string; empty?: boolean; onRetry: () => void; emptyText?: string }) {
  if (loading) return <div className="request-state" role="status">正在加载…</div>;
  if (error) return <div className="request-state request-error" role="alert"><p>{error}</p><button className="outline-button" onClick={onRetry}>重新加载</button></div>;
  if (empty) return <div className="request-state"><p>{emptyText}</p></div>;
  return null;
}

export default function Prototype() {
  const keyboard = useKeyboard();
  const [page, setPage] = useState<Page>('home');
  const [tab, setTab] = useState<FeedTab>('推荐');
  const [liked, setLiked] = useState<number[]>([]);
  const [saved, setSaved] = useState<number[]>([]);
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [draft, setDraft] = useState({ title: '', body: '' });
  const [draftPhoto, setDraftPhoto] = useState('');
  const photoInput = useRef<HTMLInputElement>(null);
  const [toast, setToast] = useState('');
  const toastTimer = useRef<number | undefined>(undefined);
  const [selectedPost, setSelectedPost] = useState(1);
  const [selectedShopId, setSelectedShopId] = useState<number | null>(null);
  const [selectedVoucher, setSelectedVoucher] = useState<Voucher | null>(null);
  const [typeId, setTypeId] = useState<number | null>(null);
  const [shopPage, setShopPage] = useState(1);
  const [searchPage, setSearchPage] = useState(1);
  const [position, setPosition] = useState<{ x: number; y: number } | null>(null);
  const [locating, setLocating] = useState(false);
  const [now, setNow] = useState(Date.now());
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY) || '');
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [codeUntil, setCodeUntil] = useState(0);
  const [sendingCode, setSendingCode] = useState(false);
  const [loggingIn, setLoggingIn] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const [loginError, setLoginError] = useState('');
  const [loginReturn, setLoginReturn] = useState<Page>('profile');
  const loginAttempt = useRef(0);
  const types = useResource<ShopType[]>('/shop-type/list');
  const listPath = typeId === null ? null : `/shop/of/type?typeId=${typeId}&current=${shopPage}${position ? `&x=${position.x}&y=${position.y}` : ''}`;
  const shops = useResource<Shop[]>(listPath);
  const shop = useResource<Shop>(selectedShopId === null ? null : `/shop/${selectedShopId}`);
  const vouchers = useResource<Voucher[]>(selectedShopId === null ? null : `/voucher/list/${selectedShopId}`);
  const search = useResource<Shop[]>(page === 'search' ? `/shop/of/name?name=${encodeURIComponent(debouncedQuery.trim())}&current=${searchPage}` : null);
  const me = useResource<User>(token ? '/user/me' : null);
  const activePost = posts.find(post => post.id === selectedPost) || posts[0];
  const showNav = ['home', 'nearby', 'publish', 'messages', 'profile'].includes(page);
  const secondsLeft = Math.max(0, Math.ceil((codeUntil - now) / 1000));

  const flash = useCallback((message: string) => {
    window.clearTimeout(toastTimer.current);
    setToast(message);
    toastTimer.current = window.setTimeout(() => setToast(''), 3200);
  }, []);
  function navigate(destination: Page) { keyboard.hide(); setPage(destination); }
  function openShop(id: number) { setSelectedShopId(id); setSelectedVoucher(null); navigate('shop'); }
  function openLogin(returnTo: Page = 'profile') { setLoginReturn(returnTo); setLoginError(''); navigate('login'); }
  function openStory(id: number) { setSelectedPost(id); navigate('story'); }
  function toggle(id: number, values: number[], setter: (value: number[]) => void) { setter(values.includes(id) ? values.filter(value => value !== id) : [...values, id]); }

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => { window.clearInterval(timer); window.clearTimeout(toastTimer.current); };
  }, []);
  useEffect(() => {
    if (types.data?.length && !types.data.some(type => type.id === typeId)) setTypeId(types.data[0].id);
  }, [types.data, typeId]);
  useEffect(() => {
    const timer = window.setTimeout(() => { setDebouncedQuery(query); setSearchPage(1); }, 350);
    return () => window.clearTimeout(timer);
  }, [query]);
  useEffect(() => {
    const expired = () => { setToken(''); flash('登录已过期，请重新登录'); };
    const storage = (event: StorageEvent) => { if (event.key === TOKEN_KEY) setToken(event.newValue || ''); };
    window.addEventListener(AUTH_EVENT, expired);
    window.addEventListener('storage', storage);
    return () => { window.removeEventListener(AUTH_EVENT, expired); window.removeEventListener('storage', storage); };
  }, [flash]);
  useEffect(() => () => { if (draftPhoto) URL.revokeObjectURL(draftPhoto); }, [draftPhoto]);

  async function sendCode() {
    if (sendingCode || secondsLeft > 0) return;
    if (!/^1[3-9]\d{9}$/.test(phone)) { setLoginError('请输入正确的 11 位手机号'); return; }
    setSendingCode(true); setLoginError('');
    try {
      await api<void>(`/user/code?phone=${encodeURIComponent(phone)}`, { method: 'POST' });
      setCodeUntil(Date.now() + 60000);
      setNow(Date.now());
      flash('验证码请求成功；本地调试请查看用户服务控制台');
    } catch (error) { setLoginError((error as Error).message); }
    finally { setSendingCode(false); }
  }

  async function login() {
    if (loggingIn) return;
    if (!/^1[3-9]\d{9}$/.test(phone) || !/^\d{6}$/.test(code)) { setLoginError('请输入正确的手机号和六位验证码'); return; }
    keyboard.hide(); setLoggingIn(true); setLoginError('');
    const attempt = ++loginAttempt.current;
    let issuedToken = '';
    try {
      issuedToken = await api<string>('/user/login', { method: 'POST', body: JSON.stringify({ phone, code }) });
      if (typeof issuedToken !== 'string' || !issuedToken) throw new Error('登录接口没有返回有效 token');
      if (attempt !== loginAttempt.current) return;
      localStorage.setItem(TOKEN_KEY, issuedToken);
      setToken(issuedToken);
      setCode('');
      navigate(loginReturn);
      flash('登录成功');
    } catch (error) { if (attempt === loginAttempt.current) setLoginError((error as Error).message); }
    finally { if (attempt === loginAttempt.current) setLoggingIn(false); }
  }

  async function logout() {
    if (loggingOut) return;
    setLoggingOut(true);
    try {
      await api<void>('/user/logout', { method: 'POST' });
      localStorage.removeItem(TOKEN_KEY); setToken(''); flash('已退出登录');
    } catch (error) { flash((error as Error).message); }
    finally { setLoggingOut(false); }
  }

  function locate() {
    if (locating) return;
    if (!navigator.geolocation) { flash('当前浏览器不支持定位，可继续按分类浏览'); return; }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(result => {
      setPosition({ x: result.coords.longitude, y: result.coords.latitude });
      setShopPage(1); setLocating(false); flash('已按当前位置查询附近店铺');
    }, () => { setLocating(false); flash('未能获取位置，可继续按分类浏览店铺'); }, { timeout: 10000, maximumAge: 300000 });
  }

  function shopCard(item: Shop) {
    return <button className="live-shop-card" key={item.id} onClick={() => openShop(item.id)}>
      <ShopImage shop={item} />
      <span className="live-shop-copy"><strong>{item.name}</strong><span className="rating"><StarFilledIcon />{item.score == null ? '暂无评分' : (item.score / 10).toFixed(1)}<i>·</i>{item.avgPrice == null ? '人均待更新' : `人均 ¥${item.avgPrice}`}</span><small>{item.area || item.address || '地址待更新'}</small>{item.distance != null && <em>距你 {item.distance >= 1000 ? `${(item.distance / 1000).toFixed(1)} km` : `${Math.round(item.distance)} m`}</em>}</span>
      <ChevronRightIcon />
    </button>;
  }

  return <div className="m1-app">
    <MobileScroll className={`m1-scroll${showNav ? '' : ' full-page'}`} key={page}>
      {page === 'home' && <>
        <header className="home-header"><div className="brand-lockup"><span className="brand-mark">巷里</span><span className="brand-sub">发现身边值得一去的小店</span></div><button className="icon-button header-search" aria-label="搜索店铺" onClick={() => navigate('search')}><MagnifyingGlassIcon /><span>搜附近的小店</span></button></header>
        <div className="feed-tabs" role="tablist" aria-label="内容分类">{(['推荐', '附近', '关注'] as FeedTab[]).map(item => <button key={item} role="tab" aria-selected={tab === item} className={tab === item ? 'active' : ''} onClick={() => { if (item === '附近') navigate('nearby'); else setTab(item); }}>{item}</button>)}</div>
        <button className="live-entry" onClick={() => navigate('nearby')}><MapPinIcon /><span><strong>去逛逛真实的小店</strong><small>店铺信息与优惠券已连接服务端</small></span><ChevronRightIcon /></button>
        {tab === '关注' ? <div className="empty-state"><span className="empty-symbol">✳</span><h2>期待下一次相遇</h2><p>关注与故事服务正在准备中。</p><button className="primary-button" onClick={() => setTab('推荐')}>看看推荐</button></div> : <div className="feed-list"><p className="preview-caption">灵感故事 · 内容预览，互动仅在当前页面保留</p>{posts.map((post, index) => <article className="story-card" key={post.id}>
          <button className="story-photo-button" onClick={() => openStory(post.id)} aria-label={`查看${post.title}`}><img className="story-photo" src={asset + post.photo} alt={post.title} /><span className="photo-counter">0{index + 1} / 0{posts.length}</span></button>
          <div className="author-row"><img className="avatar" src={asset + 'creator-avatar.png'} alt="" /><div className="author-meta"><strong>{post.author}</strong><span>{post.time} · {post.location}</span></div></div>
          <div className="story-content"><button className="story-text-button" onClick={() => openStory(post.id)}><h2>{post.title}</h2><p>{post.excerpt}</p></button><div className="story-actions"><button onClick={() => toggle(post.id, liked, setLiked)} aria-label="预览点赞">{liked.includes(post.id) ? <HeartFilledIcon className="filled-heart" /> : <HeartIcon />}<span>{post.likes + (liked.includes(post.id) ? 1 : 0)}</span></button><button onClick={() => openStory(post.id)}><ChatBubbleIcon /><span>读故事</span></button><button onClick={() => toggle(post.id, saved, setSaved)} aria-label="预览收藏"><BookmarkIcon className={saved.includes(post.id) ? 'saved-icon' : ''} /><span>{saved.includes(post.id) ? '已收藏' : '收藏'}</span></button></div><button className="coral-button visit-button" onClick={() => navigate('nearby')}>发现身边的小店 <ChevronRightIcon /></button></div>
        </article>)}</div>}
      </>}

      {page === 'nearby' && <>
        <header className="sub-header"><div><span className="eyebrow">AROUND YOU</span><h1>附近的好地方<span className="accent-dot">.</span></h1></div><button className="icon-button" onClick={() => navigate('search')} aria-label="搜索附近店铺"><MagnifyingGlassIcon /></button></header>
        <div className="location-toolbar"><button className="location-chip" onClick={locate} disabled={locating}><MapPinIcon />{locating ? '正在定位…' : position ? '已使用当前位置' : '获取位置，看看附近'}<ChevronRightIcon /></button>{position && <button className="text-button" onClick={() => { setPosition(null); setShopPage(1); }}>取消定位</button>}</div>
        <RequestState loading={types.loading} error={types.error} empty={types.data?.length === 0} onRetry={types.reload} emptyText="暂时没有店铺分类" />
        {Boolean(types.data?.length) && <Carousel ariaLabel="店铺分类" className="category-carousel" contentClassName="category-track">{types.data!.map(type => <button key={type.id} className={type.id === typeId ? 'selected' : ''} onClick={() => { setTypeId(type.id); setShopPage(1); }}>{type.name}</button>)}</Carousel>}
        <div className="live-shop-list"><RequestState loading={shops.loading} error={shops.error} empty={typeId !== null && shops.data?.length === 0} onRetry={shops.reload} emptyText={position ? '附近暂未找到这个分类的店铺，可取消定位再试试' : '这个分类暂时没有店铺'} />{shops.data?.map(shopCard)}</div>
        {typeId !== null && !shops.loading && !shops.error && <div className="pagination"><button disabled={shopPage === 1} onClick={() => setShopPage(value => value - 1)}>上一页</button><span>第 {shopPage} 页</span><button disabled={!shops.data?.length} onClick={() => setShopPage(value => value + 1)}>下一页</button></div>}
      </>}

      {page === 'search' && <>
        <header className="search-header"><button onClick={() => navigate('nearby')} aria-label="返回"><ArrowLeftIcon /></button><div><MagnifyingGlassIcon /><KeyboardInput value={query} onChange={event => setQuery(event.target.value)} placeholder="输入店铺名称" aria-label="搜索店铺名称" />{query && <button onClick={() => setQuery('')} aria-label="清除搜索"><Cross2Icon /></button>}</div></header>
        <div className="search-content"><span className="eyebrow">FIND YOUR NEXT STOP</span><h2>{debouncedQuery ? `关于“${debouncedQuery}”` : '从一家小店开始'}</h2><RequestState loading={search.loading || query !== debouncedQuery} error={query === debouncedQuery ? search.error : ''} empty={query === debouncedQuery && search.data?.length === 0} onRetry={search.reload} emptyText="没有找到这家店，换个关键词试试" />{query === debouncedQuery && search.data?.map(shopCard)}{!search.loading && !search.error && query === debouncedQuery && <div className="pagination"><button disabled={searchPage === 1} onClick={() => setSearchPage(value => value - 1)}>上一页</button><span>第 {searchPage} 页</span><button disabled={!search.data?.length} onClick={() => { keyboard.hide(); setSearchPage(value => value + 1); }}>下一页</button></div>}</div>
      </>}

      {page === 'shop' && <>
        <header className="page-header shop-header"><button onClick={() => navigate('nearby')} aria-label="返回"><ArrowLeftIcon /></button><span>店铺详情</span><button onClick={() => flash('店铺分享功能正在准备中')} aria-label="分享"><PaperPlaneIcon /></button></header>
        <RequestState loading={shop.loading} error={shop.error} onRetry={shop.reload} />
        {shop.data && <><ShopImage shop={shop.data} className="shop-hero" /><div className="shop-detail"><span className="eyebrow">A LITTLE PLACE TO STAY</span><h1>{shop.data.name}<span className="accent-dot">.</span></h1><p className="shop-description">{shop.data.area || '发现身边值得一去的小店'}</p><div className="shop-score"><StarFilledIcon /><strong>{shop.data.score == null ? '暂无评分' : (shop.data.score / 10).toFixed(1)}</strong><span>· 人均 {shop.data.avgPrice == null ? '待更新' : `¥${shop.data.avgPrice}`}{shop.data.comments != null ? ` · ${shop.data.comments} 条评价` : ''}</span></div><div className="address-row"><MapPinIcon /><span>{shop.data.address || '地址待更新'}<br /><small>营业时间：{shop.data.openHours || '待更新'}</small></span></div></div></>}
        <div className="shop-detail live-vouchers"><div className="section-title coupon-heading"><span>店铺优惠</span><small>{vouchers.data ? `${vouchers.data.length} 张券` : '优惠券'}</small></div><RequestState loading={vouchers.loading} error={vouchers.error} empty={vouchers.data?.length === 0} onRetry={vouchers.reload} emptyText="这家店暂时没有上架的优惠券" />{vouchers.data?.map(voucher => {
          const state = voucherState(voucher, now);
          return <div className="coupon" key={voucher.id}><div className="coupon-copy"><div className="coupon-title"><strong>{voucher.title}</strong><em className={state === '进行中' ? 'active' : ''}>{state}</em></div><small>{voucher.subTitle || '使用规则请查看详情'}</small><span><b>¥{money(voucher.payValue)}</b><s>抵 ¥{money(voucher.actualValue)}</s></span>{voucher.type === 1 && <small>{timeLabel(voucher.beginTime)} 开始 · 北京时间</small>}</div><button onClick={() => { setSelectedVoucher(voucher); navigate('checkout'); }}>查看详情</button></div>;
        })}</div>
      </>}

      {page === 'checkout' && selectedVoucher && <>
        <header className="page-header"><button onClick={() => navigate('shop')} aria-label="返回"><ArrowLeftIcon /></button><span>优惠券详情</span><span /></header>
        <div className="checkout-body"><span className="eyebrow">A SMALL TREAT FOR TODAY</span><h1>{selectedVoucher.title}<span className="accent-dot">.</span></h1><div className="voucher-summary"><span>{voucherState(selectedVoucher, now)}</span><strong>¥{money(selectedVoucher.payValue)}</strong><small>可抵扣 ¥{money(selectedVoucher.actualValue)}</small></div><div className="checkout-info"><span>所属店铺 <b>{shop.data?.name || `店铺 ${selectedVoucher.shopId}`}</b></span>{selectedVoucher.type === 1 && <><span>开始时间 <b>{timeLabel(selectedVoucher.beginTime)}</b></span><span>结束时间 <b>{timeLabel(selectedVoucher.endTime)}</b></span><span>参考剩余库存 <b>{selectedVoucher.stock ?? '待更新'}</b></span></>}</div><h2 className="rules-heading">使用规则</h2><p className="voucher-rules">{selectedVoucher.rules?.replace(/\\n/g, '\n') || '暂无补充说明'}</p><p className="checkout-note">{selectedVoucher.type === 1 ? '活动时间以北京时间为准，展示库存仅供参考。' : ''}购买与订单功能尚未开放，当前可以查看优惠券信息。</p><button className="coral-button" disabled>购买功能暂未开放</button>{!token && <button className="login-link" onClick={() => openLogin('checkout')}>先登录，发现更多好去处 <ChevronRightIcon /></button>}</div>
      </>}

      {page === 'profile' && <>
        <header className="profile-header"><span className="eyebrow">YOUR LITTLE CORNER</span><h1>我的巷里<span className="accent-dot">.</span></h1><div className="profile-identity"><div className="profile-avatar"><PersonIcon /></div><span><strong>{me.data?.nickName || (token ? me.error ? '暂时无法加载个人信息' : '正在确认登录状态' : '欢迎来巷里走走')}</strong><small>{me.data ? `账号 ID：${me.data.id}` : token ? me.error ? '请重试下方请求' : '连接你的巷里账号' : '登录后，开启你的巷里生活'}</small></span>{!token && <button className="outline-button" onClick={() => openLogin()}>登录</button>}</div></header>
        {token && <RequestState loading={me.loading} error={me.error} onRetry={me.reload} />}
        <div className="profile-menu"><button onClick={() => navigate('nearby')}><MapPinIcon /> 发现店铺 <ChevronRightIcon /></button><button onClick={() => flash('购买和订单功能尚未开放，暂无已购优惠券入口')}><StarFilledIcon /> 我的优惠 <ChevronRightIcon /></button><button onClick={() => flash('巷里，发现身边值得一去的小店')}><HeartIcon /> 关于巷里 <ChevronRightIcon /></button>{token ? <button onClick={logout} disabled={loggingOut}><PersonIcon />{loggingOut ? '正在退出…' : '退出登录'}<ChevronRightIcon /></button> : <button onClick={() => openLogin()}><PersonIcon /> 手机号登录 <ChevronRightIcon /></button>}</div>
      </>}

      {page === 'login' && <>
        <header className="page-header"><button aria-label="返回" onClick={() => { loginAttempt.current += 1; setLoggingIn(false); navigate(loginReturn); }}><ArrowLeftIcon /></button><span>登录巷里</span><span /></header>
        <div className="login-body"><span className="eyebrow">GOOD TO SEE YOU</span><h1>好久不见<span className="accent-dot">.</span></h1><p>用手机号，找回你的巷里生活。</p><form onSubmit={event => { event.preventDefault(); void login(); }}><label htmlFor="login-phone">手机号</label><KeyboardInput id="login-phone" type="tel" inputMode="numeric" autoComplete="tel" value={phone} maxLength={11} disabled={loggingIn || sendingCode} onChange={event => setPhone(event.target.value.replace(/\D/g, ''))} placeholder="请输入 11 位手机号" /><label htmlFor="login-code">验证码</label><div className="verification-row"><KeyboardInput id="login-code" inputMode="numeric" autoComplete="one-time-code" value={code} maxLength={6} disabled={loggingIn} onChange={event => setCode(event.target.value.replace(/\D/g, ''))} placeholder="六位验证码" /><button type="button" onClick={() => void sendCode()} disabled={sendingCode || secondsLeft > 0 || loggingIn}>{sendingCode ? '请求中…' : secondsLeft > 0 ? `${secondsLeft}s 后重试` : '获取验证码'}</button></div>{loginError && <p className="form-error" role="alert">{loginError}</p>}<button className="coral-button" type="submit" disabled={loggingIn}>{loggingIn ? '正在登录…' : '登录 / 注册'}</button></form><p className="login-note">首次登录将自动创建账号。本地调试的验证码请查看用户服务控制台，当前未接入真实短信。</p></div>
      </>}

      {page === 'story' && <>
        <header className="page-header"><button onClick={() => navigate('home')} aria-label="返回"><ArrowLeftIcon /></button><span>巷里故事 · 内容预览</span><button onClick={() => toggle(activePost.id, saved, setSaved)} aria-label="预览收藏"><BookmarkIcon className={saved.includes(activePost.id) ? 'saved-icon' : ''} /></button></header><img className="detail-hero" src={asset + activePost.photo} alt={activePost.title} /><div className="detail-body"><div className="detail-author"><img className="avatar" src={asset + 'creator-avatar.png'} alt="" /><span><strong>{activePost.author}</strong><small>{activePost.time} · {activePost.location}</small></span></div><h1>{activePost.title}</h1><p>{activePost.excerpt}</p><p>有时候，最喜欢的地方是走着走着碰到的。店里放着轻轻的音乐，热腾腾的一餐刚好抚平忙碌的一天。</p><div className="story-location"><MapPinIcon />{activePost.location} · {activePost.shop}</div><button className="coral-button" onClick={() => navigate('nearby')}>逛逛真实的小店 <ChevronRightIcon /></button><div className="detail-divider" /><p className="preview-caption">这是一篇内容预览，故事发布、评论与关注功能正在准备中。</p></div>
      </>}

      {page === 'publish' && <>
        <header className="sub-header"><div><span className="eyebrow">SHARE YOUR MOMENT</span><h1>记下一个好地方<span className="accent-dot">.</span></h1></div></header><div className="publish-body"><p className="preview-caption">故事服务尚未开放，当前内容仅在本次页面中暂存。</p><input ref={photoInput} className="visually-hidden" type="file" accept="image/*" aria-label="选择故事照片" onChange={event => { const file = event.target.files?.[0]; if (file) setDraftPhoto(URL.createObjectURL(file)); }} /><button className="upload-box" onClick={() => photoInput.current?.click()}>{draftPhoto ? <img className="draft-preview" src={draftPhoto} alt="故事照片预览" /> : <><CameraIcon /><strong>添加照片</strong><span>让故事从一张照片开始</span></>}</button><label>一句话标题<KeyboardInput value={draft.title} onChange={event => setDraft({ ...draft, title: event.target.value })} maxLength={60} placeholder="比如：转角遇到的那家小店" /></label><label>说说你的故事<KeyboardTextarea value={draft.body} onChange={event => setDraft({ ...draft, body: event.target.value })} placeholder="今天发生了什么好事？" rows={5} /></label><p className="checkout-note">内容尚未上传，刷新页面后不会保留。</p><button className="coral-button" disabled>故事发布功能暂未开放</button></div>
      </>}

      {page === 'messages' && <><header className="sub-header"><div><span className="eyebrow">STAY IN TOUCH</span><h1>消息<span className="accent-dot">.</span></h1></div></header><div className="empty-state"><ChatBubbleIcon /><h2>期待新的问候</h2><p>消息功能正在准备中。</p><button className="primary-button" onClick={() => navigate('nearby')}>先去逛逛小店</button></div></>}
    </MobileScroll>
    {showNav && <nav className="bottom-nav" aria-label="主导航"><button className={page === 'home' ? 'active' : ''} onClick={() => navigate('home')}><HomeIcon /><span>首页</span></button><button className={page === 'nearby' ? 'active' : ''} onClick={() => navigate('nearby')}><MapPinIcon /><span>附近</span></button><button className="nav-create" onClick={() => navigate('publish')} aria-label="记录故事"><PlusIcon /></button><button className={page === 'messages' ? 'active' : ''} onClick={() => navigate('messages')}><ChatBubbleIcon /><span>消息</span></button><button className={page === 'profile' ? 'active' : ''} onClick={() => navigate('profile')}><PersonIcon /><span>我的</span></button></nav>}
    {toast && <div className="toast" role="status">{toast}</div>}
  </div>;
}
