import { useCallback, useEffect, useRef, useState } from 'react';
import { ArrowLeftIcon, CameraIcon, CheckIcon, ChevronLeftIcon, ChevronRightIcon, ChatBubbleIcon, Cross2Icon, DrawingPinIcon as MapPinIcon, HeartFilledIcon, HeartIcon, HomeIcon, MagnifyingGlassIcon, PaperPlaneIcon, PersonIcon, Pencil1Icon, PlusIcon, StarFilledIcon } from '@radix-ui/react-icons';
import { BottomSheet, Carousel, KeyboardInput, KeyboardTextarea, MobileScroll, useKeyboard, useMobileDevice } from './mobile';
import './prototype.css';

type Page = 'home' | 'nearby' | 'publish' | 'messages' | 'profile' | 'story' | 'shop' | 'search' | 'checkout' | 'login' | 'myNotes' | 'following' | 'followers' | 'editProfile' | 'userHome';
type StoryReturn = 'home' | 'myNotes' | 'userHome';
type JourneySnapshot = { page: Page; blogId: string | null; storyReturn: StoryReturn; userId: string | null; notesPage: number; relationsReturn: 'profile' | 'userHome' };
type PublicUserProfile = { userId: string; nickName: string; icon: string | null; followingCount: number; followersCount: number };
type FeedTab = '推荐' | '附近' | '关注';
type FollowListMode = 'following' | 'followers';
type FollowPerson = { userId: string; nickName: string; icon: string | null; followTime: string | null; followed: boolean };
type FollowPeoplePage = { records: FollowPerson[]; total: number; current: number; size: number };
type Result<T> = { success: boolean; errorMsg: string | null; data: T; total: number | null };
type User = { id: string | number; nickName: string; icon: string | null };
type ShopType = { id: number; name: string; icon: string | null };
type Shop = { id: number; name: string; typeId: number; images: string | null; area: string | null; address: string; avgPrice: number | null; score: number | null; comments: number | null; openHours: string | null; distance: number | null };
type Voucher = { id: number; shopId: number; title: string; subTitle: string | null; rules: string | null; payValue: number; actualValue: number; type: number; status: number; stock: number | null; beginTime: string | null; endTime: string | null };
type VoucherOrder = { id: string; orderNo?: string | null; voucherId: string; status: number; createTime: string };
type UploadedImage = { objectKey: string; url: string };
type ProfileUpdate = { nickName: string; avatarObjectKey: string | null };
type BlogLike = { likedByMe: boolean; liked: number };
type AuthorFollow = { targetUserId: string; followed: boolean };
type AuthorFollowRequestState = { identity: string; data: AuthorFollow | null; phase: 'idle' | 'loading' | 'saving'; error: string; message: string; uncertainTarget: boolean | null };
type BlogLikeState = { identity: string; data: BlogLike | null; phase: 'idle' | 'loading' | 'saving'; message: string; error: string; uncertainTarget: boolean | null };
type BlogDetail = { id: string | number; shopId: string | number | null; userId: string | number; authorName: string; authorIcon: string | null; title: string; coverImage: string; imageUrls?: string[]; content: string; liked: number; comments: number; createTime: string | null };
type BlogSummary = { id: string; title: string; coverImage: string | null; liked: number | null; comments: number | null; createTime: string | null };
type BlogPage = { records: BlogSummary[]; total: number; current: number; size: number };
type BlogFeedItem = BlogSummary & { userId: string | null; authorName: string; authorIcon: string | null };
type BlogFeedPage = { records: BlogFeedItem[]; total: number; current: number; size: number };
type PublishPhase = 'idle' | 'submitting' | 'uncertain';
type PhotoUploadState = { preview: string; uploaded: UploadedImage | null; phase: 'idle' | 'uploading' | 'success' | 'error'; error: string; retryable: boolean; previewUnavailable: boolean };
type NotePhoto = { id: string; ownerId: string; file: File; fingerprint: string; localPreview: string; preview: string; uploaded: UploadedImage | null; phase: 'queued' | 'uploading' | 'success' | 'error'; error: string; retryable: boolean; previewUnavailable: boolean };
type ReservationPhase = 'ready' | 'submitting' | 'accepted' | 'uncertain' | 'ordered';
type ReservationMarker = { phase: 'accepted' | 'uncertain'; attemptId: string };

const API_BASE = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const TOKEN_KEY = 'm1kasa.token';
const AUTH_EVENT = 'm1kasa:unauthorized';

class ApiError extends Error {
  constructor(message: string, public status: number) { super(message); }
}

// All business requests use the gateway; never fall back to demonstration data.
async function api<T>(path: string, options: RequestInit = {}, timeoutMs = 12000): Promise<T> {
  const controller = new AbortController();
  const cancel = () => controller.abort();
  if (options.signal?.aborted) controller.abort();
  options.signal?.addEventListener('abort', cancel, { once: true });
  const timeout = window.setTimeout(cancel, timeoutMs);
  const headers = new Headers(options.headers);
  // The browser adds the multipart boundary when sending FormData.
  if (options.body instanceof FormData) headers.delete('Content-Type');
  else if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
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

const emptyPhotoUpload = (): PhotoUploadState => ({ preview: '', uploaded: null, phase: 'idle', error: '', retryable: false, previewUnavailable: false });

function usePhotoUpload(token: string, endpoint = '/blog/images', ownerId: string | null = null) {
  const [photo, setPhoto] = useState<PhotoUploadState>(emptyPhotoUpload);
  const fileRef = useRef<File | null>(null);
  const localPreview = useRef('');
  const request = useRef<AbortController | null>(null);
  const sequence = useRef(0);
  const currentToken = useRef(token);
  currentToken.current = token;

  const discard = useCallback(() => {
    sequence.current += 1;
    request.current?.abort();
    request.current = null;
    fileRef.current = null;
    if (localPreview.current) URL.revokeObjectURL(localPreview.current);
    localPreview.current = '';
  }, []);

  useEffect(() => {
    discard();
    setPhoto(emptyPhotoUpload());
    return discard;
  }, [token, endpoint, ownerId, discard]);

  async function send(file: File) {
    if (request.current) return;
    if (!token || localStorage.getItem(TOKEN_KEY) !== token) {
      setPhoto(value => ({ ...value, phase: 'error', error: '请先登录后上传照片', retryable: false }));
      return;
    }
    const controller = new AbortController();
    request.current = controller;
    const run = ++sequence.current;
    const active = () => !controller.signal.aborted && sequence.current === run && currentToken.current === token && localStorage.getItem(TOKEN_KEY) === token;
    setPhoto({ preview: localPreview.current, uploaded: null, phase: 'uploading', error: '', retryable: false, previewUnavailable: false });
    const body = new FormData();
    body.append('file', file);
    try {
      const result = await api<UploadedImage>(endpoint, { method: 'POST', body, signal: controller.signal }, 45000);
      if (!active()) return;
      if (!result || typeof result.objectKey !== 'string' || !result.objectKey.trim() || typeof result.url !== 'string' || !result.url.trim()) {
        throw new Error('上传结果缺少图片地址，请稍后重试');
      }
      const address = new URL(result.url);
      if (address.protocol !== 'http:' && address.protocol !== 'https:') throw new Error('图片预览地址不可用，请稍后重试');
      // Avatar keys must match the authenticated owner's upload directory.
      if (endpoint === '/user/avatar' && (!ownerId || !new RegExp(
        '^avatar/' + ownerId + '/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)$'
      ).test(result.objectKey))) throw new Error('上传结果与当前账号不一致，请重新上传');
      setPhoto({ preview: result.url, uploaded: result, phase: 'success', error: '', retryable: false, previewUnavailable: false });
    } catch (error) {
      if (!active()) return;
      const retryable = !(error instanceof ApiError) || error.status === 0 || error.status >= 500;
      const message = error instanceof ApiError && error.status === 413 ? '图片或上传请求过大，请选择不超过 5MB 的照片' : endpoint === '/user/avatar' && error instanceof ApiError && error.status === 404 ? '头像上传暂不可用，请稍后再试。你可以先修改昵称。' : (error as Error).message;
      setPhoto({ preview: localPreview.current, uploaded: null, phase: 'error', error: message, retryable, previewUnavailable: false });
    } finally {
      if (request.current === controller) request.current = null;
    }
  }

  function select(file: File) {
    discard();
    const error = file.size === 0 ? '请选择有内容的照片' : file.size > 5 * 1024 * 1024 ? '照片不能超过 5MB' : file.type && !['image/jpeg', 'image/png'].includes(file.type) ? '目前仅支持 JPEG、PNG 照片' : '';
    if (error) {
      setPhoto({ ...emptyPhotoUpload(), phase: 'error', error });
      return;
    }
    fileRef.current = file;
    localPreview.current = URL.createObjectURL(file);
    void send(file);
  }

  function retry() {
    if (fileRef.current && !request.current) void send(fileRef.current);
  }

  function previewFailed() {
    setPhoto(value => value.uploaded && value.preview === value.uploaded.url
      ? { ...value, preview: localPreview.current, previewUnavailable: true }
      : { ...value, preview: '' });
  }

  function clear() {
    discard();
    setPhoto(emptyPhotoUpload());
  }

  return { photo, select, retry, previewFailed, clear };
}


const MAX_NOTE_PHOTOS = 9;

function useNotePhotos(token: string, ownerId: string | null) {
  const [saved, setSaved] = useState<{token: string; photos: NotePhoto[]; error: string}>({token, photos: [], error: ''});
  const items = useRef<NotePhoto[]>([]);
  const selectionError = useRef('');
  const controllers = useRef(new Map<string, AbortController>());
  const epoch = useRef(0);
  const currentToken = useRef(token);
  currentToken.current = token;

  const update = useCallback((change: (photos: NotePhoto[]) => NotePhoto[]) => {
    items.current = change(items.current);
    setSaved({token: currentToken.current, photos: items.current, error: selectionError.current});
  }, []);
  const dispose = useCallback(() => {
    epoch.current += 1;
    controllers.current.forEach(controller => controller.abort());
    controllers.current.clear();
    items.current.forEach(photo => URL.revokeObjectURL(photo.localPreview));
    items.current = [];
    selectionError.current = '';
  }, []);
  useEffect(() => {
    dispose();
    setSaved({token, photos: [], error: ''});
    return dispose;
  }, [token, dispose]);

  function pump() {
    if (!token || currentToken.current !== token || localStorage.getItem(TOKEN_KEY) !== token) return;
    while (controllers.current.size < 2) {
      const photo = items.current.find(item => item.phase === 'queued');
      if (!photo) return;
      const controller = new AbortController();
      const run = epoch.current;
      controllers.current.set(photo.id, controller);
      update(list => list.map(item => item.id === photo.id ? {...item, phase: 'uploading', error: '', retryable: false} : item));
      void send(photo, controller, run);
    }
  }

  async function send(photo: NotePhoto, controller: AbortController, run: number) {
    const active = () => !controller.signal.aborted && epoch.current === run && currentToken.current === token
      && localStorage.getItem(TOKEN_KEY) === token && controllers.current.get(photo.id) === controller;
    const body = new FormData();
    body.append('file', photo.file);
    try {
      const result = await api<UploadedImage>('/blog/images', {method: 'POST', body, signal: controller.signal}, 45000);
      if (!active()) return;
      const pattern = new RegExp('^blog/' + photo.ownerId + '/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)$');
      if (!result || typeof result.objectKey !== 'string' || !pattern.test(result.objectKey)
          || typeof result.url !== 'string' || !/^https?:\/\//i.test(result.url)) {
        throw new Error('上传结果与当前图片或账号不一致，请重新上传');
      }
      update(list => list.map(item => item.id === photo.id ? {...item, uploaded: result, preview: result.url, phase: 'success', error: '', previewUnavailable: false} : item));
    } catch (caught) {
      if (!active()) return;
      const retryable = !(caught instanceof ApiError) || caught.status === 0 || caught.status >= 500;
      const message = caught instanceof ApiError && caught.status === 413 ? '图片不能超过 5MB，请重新选择' : (caught as Error).message;
      update(list => list.map(item => item.id === photo.id ? {...item, uploaded: null, preview: item.localPreview, phase: 'error', error: message, retryable} : item));
    } finally {
      if (controllers.current.get(photo.id) === controller) controllers.current.delete(photo.id);
      if (epoch.current === run && currentToken.current === token) pump();
    }
  }

  function reject(message: string) {
    selectionError.current = message;
    update(list => list);
  }

  function add(files: File[]) {
    if (!files.length) return;
    if (!token || localStorage.getItem(TOKEN_KEY) !== token || !ownerId) { reject('请先确认登录状态后添加图片'); return; }
    if (files.length + items.current.length > MAX_NOTE_PHOTOS) { reject('每篇笔记最多 9 张图片，还可以添加 ' + (MAX_NOTE_PHOTOS - items.current.length) + ' 张'); return; }
    const seen = new Set(items.current.map(photo => photo.fingerprint));
    const fingerprints: string[] = [];
    for (const file of files) {
      const reason = file.size === 0 ? '文件没有内容' : file.size > 5 * 1024 * 1024 ? '图片不能超过 5MB' : file.type && !['image/jpeg', 'image/png'].includes(file.type) ? '仅支持 JPEG、PNG 图片' : '';
      if (reason) { reject(file.name + '：' + reason); return; }
      const fingerprint = JSON.stringify([file.name, file.size, file.type, file.lastModified]);
      if (seen.has(fingerprint)) { reject('已添加这张图片：' + file.name); return; }
      seen.add(fingerprint);
      fingerprints.push(fingerprint);
    }
    const additions: NotePhoto[] = files.map((file, index) => {
      const localPreview = URL.createObjectURL(file);
      return {id: crypto.randomUUID(), ownerId, file, fingerprint: fingerprints[index], localPreview, preview: localPreview, uploaded: null, phase: 'queued', error: '', retryable: false, previewUnavailable: false};
    });
    selectionError.current = '';
    update(list => [...list, ...additions]);
    pump();
  }

  function remove(id: string) {
    const photo = items.current.find(item => item.id === id);
    if (!photo) return;
    controllers.current.get(id)?.abort();
    controllers.current.delete(id);
    URL.revokeObjectURL(photo.localPreview);
    selectionError.current = '';
    update(list => list.filter(item => item.id !== id));
    pump();
  }

  function move(id: string, delta: number) {
    update(list => {
      const from = list.findIndex(item => item.id === id), to = from + delta;
      if (from < 0 || to < 0 || to >= list.length) return list;
      const next = [...list];
      [next[from], next[to]] = [next[to], next[from]];
      return next;
    });
  }

  function retry(id: string) {
    const photo = items.current.find(item => item.id === id);
    if (!photo || photo.phase !== 'error' || !photo.retryable) return;
    update(list => list.map(item => item.id === id ? {...item, phase: 'queued', error: '', retryable: false} : item));
    pump();
  }

  function previewFailed(id: string) {
    update(list => list.map(item => item.id !== id ? item : {...item,
      preview: item.uploaded && item.preview === item.uploaded.url ? item.localPreview : '', previewUnavailable: true
    }));
  }

  function clear() {
    dispose();
    setSaved({token: currentToken.current, photos: [], error: ''});
  }

  const photos = saved.token === token ? saved.photos : [];
  return {photos, error: saved.token === token ? saved.error : '', add, remove, move, retry, previewFailed, clear,
    busy: photos.some(photo => photo.phase === 'queued' || photo.phase === 'uploading'),
    ready: photos.length > 0 && photos.every(photo => photo.phase === 'success' && photo.uploaded !== null)};
}

function NotePhotoGrid({upload, disabled, onChoose, token}: {upload: ReturnType<typeof useNotePhotos>; disabled: boolean; onChoose: () => void; token: string}) {
  const {photos} = upload;
  return <section className="note-photo-section" aria-label="笔记图片">
    <div className="note-photo-heading"><strong>图片</strong><span>{photos.length} / 9</span></div>
    <div className={'note-photo-grid' + (photos.length ? '' : ' is-empty')}>
      {photos.map((photo, index) => <article className="note-photo-item" data-testid="note-photo" data-phase={photo.phase} data-file-name={photo.file.name} key={photo.id}>
        <div className="note-photo-preview">
          {photo.preview ? <img src={photo.preview} alt={'第 ' + (index + 1) + ' 张笔记图片'} decoding="async" onError={() => upload.previewFailed(photo.id)} /> : <span className="note-photo-unavailable"><CameraIcon /><small>预览暂不可用</small></span>}
          <span className={'note-photo-position' + (index === 0 ? ' is-cover' : '')}>{index === 0 ? '封面' : index + 1}</span>
          <button type="button" className="note-photo-remove" aria-label={'删除第' + (index + 1) + '张图片'} disabled={disabled} onClick={() => upload.remove(photo.id)}><Cross2Icon /></button>
          {(photo.phase === 'queued' || photo.phase === 'uploading') && <span className="note-photo-progress" role="status">{photo.phase === 'uploading' && <i className="photo-upload-spinner" aria-hidden="true" />}{photo.phase === 'queued' ? '等待上传' : '上传中'}</span>}
        </div>
        <div className="note-photo-actions"><button type="button" aria-label={'前移第' + (index + 1) + '张图片'} disabled={disabled || index === 0} onClick={() => upload.move(photo.id, -1)}><ChevronLeftIcon /></button><button type="button" aria-label={'后移第' + (index + 1) + '张图片'} disabled={disabled || index === photos.length - 1} onClick={() => upload.move(photo.id, 1)}><ChevronRightIcon /></button></div>
        {photo.phase === 'error' && <div className="note-photo-error"><p role="alert">{photo.error}</p>{photo.retryable && <button type="button" className="text-button" disabled={disabled} onClick={() => upload.retry(photo.id)}>重试上传</button>}</div>}
        {photo.previewUnavailable && photo.phase === 'success' && <small className="note-photo-preview-note">已上传，在线预览暂不可用</small>}
      </article>)}
      {photos.length < MAX_NOTE_PHOTOS && <button type="button" className="note-photo-add" onClick={onChoose} disabled={disabled} aria-label={token ? '添加笔记图片' : '登录后添加笔记图片'}><PlusIcon /><span>{token ? '添加图片' : '登录后添加图片'}</span></button>}
    </div>
    <p className="note-photo-guide">第一张作为封面，可用箭头调整顺序。</p>
    {upload.error && <p className="note-photo-selection-error" role="alert">{upload.error}</p>}
    {upload.ready && <p className="note-photo-complete" role="status"><CheckIcon />已上传 {photos.length} 张图片，尚未发布</p>}
  </section>;
}

function useResource<T>(path: string | null, identity = '') {
  const [version, setVersion] = useState(0);
  const [state, setState] = useState<{ path: string | null; identity: string; data: T | null; loading: boolean; error: string }>({ path: null, identity: '', data: null, loading: false, error: '' });
  useEffect(() => {
    const controller = new AbortController();
    if (!path) { setState({ path, identity, data: null, loading: false, error: '' }); return; }
    setState({ path, identity, data: null, loading: true, error: '' });
    api<T>(path, { signal: controller.signal }).then(data => {
      if (!controller.signal.aborted) setState({ path, identity, data, loading: false, error: '' });
    }).catch((error: Error) => {
      if (!controller.signal.aborted) setState({ path, identity, data: null, loading: false, error: error.message });
    });
    return () => controller.abort();
  }, [path, version, identity]);
  const reload = useCallback(() => setVersion(value => value + 1), []);
  return { ...(state.path === path && state.identity === identity ? state : { path, identity, data: null, loading: Boolean(path), error: '' }), reload };
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
  return '进行中';
}

// Persist only an unresolved submission marker, scoped to the authenticated user.
// The server remains the source of truth for eligibility and order creation.
function reservationMarker(key: string | null, value?: ReservationMarker | null, expectedAttempt?: string): ReservationMarker | null {
  if (!key) return null;
  try {
    const text = localStorage.getItem(key);
    const stored = text === 'accepted' || text === 'uncertain' ? { phase: text, attemptId: 'legacy' } : text ? JSON.parse(text) : null;
    const valid: ReservationMarker | null = stored && (stored.phase === 'accepted' || stored.phase === 'uncertain') && typeof stored.attemptId === 'string' ? stored : null;
    if (expectedAttempt && valid?.attemptId !== expectedAttempt) return valid;
    if (value === null) { localStorage.removeItem(key); return null; }
    if (value) { localStorage.setItem(key, JSON.stringify(value)); return value; }
    return valid;
  } catch { return null; }
}

function SeckillPurchase({ voucher, token, userId, now, identityLoading, identityError, onLogin, onIdentityRetry }: {
  voucher: Voucher; token: string; userId: string | null; now: number;
  identityLoading: boolean; identityError: string; onLogin: () => void; onIdentityRetry: () => void;
}) {
  const markerKey = userId ? `m1kasa.seckill.pending:${userId}:${voucher.id}` : null;
  const [phase, setPhase] = useState<ReservationPhase>(() => reservationMarker(markerKey)?.phase || 'ready');
  const [order, setOrder] = useState<VoucherOrder | null>(null);
  const [checked, setChecked] = useState(false);
  const [querying, setQuerying] = useState(false);
  const [polling, setPolling] = useState(false);
  const [lookupError, setLookupError] = useState('');
  const [lookupNote, setLookupNote] = useState('');
  const [reserveError, setReserveError] = useState('');
  const [soldOut, setSoldOut] = useState(false);
  const mounted = useRef(false);
  const submitting = useRef(false);
  const lookupRun = useRef(0);
  const lookupAbort = useRef<AbortController | null>(null);
  const submitAbort = useRef<AbortController | null>(null);
  const pollTimer = useRef<number | undefined>(undefined);

  const checkOrder = useCallback((follow: boolean) => {
    if (!token || !userId || voucher.type !== 1) return;
    lookupAbort.current?.abort();
    window.clearTimeout(pollTimer.current);
    const controller = new AbortController();
    lookupAbort.current = controller;
    const runId = ++lookupRun.current;
    const startedAt = Date.now();
    const current = () => mounted.current && !controller.signal.aborted && lookupRun.current === runId && localStorage.getItem(TOKEN_KEY) === token;
    setPolling(follow); setLookupError(''); setLookupNote('');

    const read = async (attempt: number) => {
      if (!current()) return;
      setQuerying(true);
      let keepChecking = follow;
      try {
        const result = await api<VoucherOrder | null>(`/order/of/voucher/${voucher.id}`, { signal: controller.signal });
        if (!current()) return;
        setLookupError('');
        if (result !== null) {
          if (!result || typeof result.id !== 'string' || !result.id || String(result.voucherId) !== String(voucher.id)) throw new Error('订单响应与当前优惠券不一致，请稍后重新查询');
          setChecked(true); setOrder(result); setPhase('ordered'); setPolling(false);
          reservationMarker(markerKey, null);
          return;
        }
        setChecked(true);
        setLookupNote(follow ? '暂未查到订单，正在继续确认。' : '暂未查到订单；如果刚刚提交，请稍后刷新确认。');
      } catch (error) {
        if (!current()) return;
        setLookupError((error as Error).message);
        if (error instanceof ApiError && error.status === 401) keepChecking = false;
      } finally {
        if (current()) setQuerying(false);
      }
      if (!current()) return;
      if (keepChecking && attempt < 12 && Date.now() - startedAt < 30000) {
        pollTimer.current = window.setTimeout(() => { void read(attempt + 1); }, 2000);
      } else {
        setPolling(false);
        if (follow) setLookupNote('本轮自动确认已结束，订单可能仍在处理中。请稍后手动刷新，无需再次抢券。');
      }
    };
    void read(1);
  }, [token, userId, voucher.id, voucher.type, markerKey]);

  useEffect(() => {
    mounted.current = true;
    if (token && userId && voucher.type === 1) checkOrder(Boolean(reservationMarker(markerKey)));
    return () => {
      mounted.current = false; lookupRun.current += 1;
      lookupAbort.current?.abort(); submitAbort.current?.abort();
      window.clearTimeout(pollTimer.current);
    };
  }, [token, userId, voucher.type, markerKey, checkOrder]);

  async function reserve(explicitRetry = false) {
    if (!token) { onLogin(); return; }
    if (localStorage.getItem(TOKEN_KEY) !== token) return;
    const retryAllowed = explicitRetry && (phase === 'accepted' || phase === 'uncertain') && !polling && !querying && !lookupError;
    if (!userId || !checked || submitting.current || order || (phase !== 'ready' && !retryAllowed) || voucherState(voucher, Date.now()) !== '进行中' || soldOut) return;
    submitting.current = true;
    const controller = new AbortController();
    submitAbort.current = controller;
    const attemptId = globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    const current = () => mounted.current && !controller.signal.aborted && localStorage.getItem(TOKEN_KEY) === token;
    setPhase('submitting'); setReserveError(''); setLookupNote('');
    // A navigation/timeout cannot prove that the server did not accept the POST.
    reservationMarker(markerKey, { phase: 'uncertain', attemptId });
    try {
      await api<string>(`/voucher/seckill/${voucher.id}/reserve`, { method: 'POST', signal: controller.signal });
      reservationMarker(markerKey, { phase: 'accepted', attemptId }, attemptId);
      if (!current()) return;
      setPhase('accepted'); checkOrder(true);
    } catch (error) {
      const failure = error as ApiError;
      const duplicate = failure.status === 409 && failure.message.includes('重复');
      // A definite rejection must clear this attempt even if a 401 already changed the session.
      // The attempt ID prevents an old response from deleting a newer submission marker.
      if (duplicate) reservationMarker(markerKey, { phase: 'accepted', attemptId }, attemptId);
      else if (failure.status >= 400 && failure.status < 500) reservationMarker(markerKey, null, attemptId);
      if (!current()) return;
      if (duplicate) {
        setPhase('accepted'); checkOrder(true);
      } else if (failure.status >= 400 && failure.status < 500) {
        setPhase('ready'); setReserveError(failure.message);
        if (failure.status === 409 && failure.message.includes('抢完')) setSoldOut(true);
      } else {
        setPhase('uncertain');
        setReserveError('本次请求结果暂时无法确认，请先查询订单状态，避免重复提交。');
        checkOrder(true);
      }
    } finally {
      submitting.current = false;
    }
  }

  if (voucher.type !== 1) return <p className="checkout-note">普通优惠券购买暂未开放，当前可查看优惠信息。</p>;

  const state = voucherState(voucher, now);
  const unresolved = phase === 'accepted' || phase === 'uncertain';
  const orderStatus = order ? ({ 1: '待支付', 2: '已支付', 3: '已取消' } as Record<number, string>)[order.status] || '状态待确认' : '';
  const reserveDisabled = Boolean(order) || phase !== 'ready' || state !== '进行中' || soldOut || Boolean(token && (!userId || !checked));
  const buttonLabel = order ? '已生成订单' : phase === 'submitting' ? '正在提交…' : unresolved ? '请先确认订单状态' : soldOut ? '优惠券已抢完' : state !== '进行中' ? state : token ? '立即抢券' : '登录后抢券';

  return <section className="seckill-purchase" aria-label="抢券与订单">
    <p className="checkout-note">活动时间以北京时间为准，展示库存仅供参考，实际资格以提交结果为准。</p>
    {token && !userId && <div className="reservation-message" role={identityError ? 'alert' : 'status'}><p>{identityError || (identityLoading ? '正在确认登录身份…' : '正在读取账号信息…')}</p>{identityError && <button className="outline-button" onClick={onIdentityRetry}>重新确认登录身份</button>}</div>}
    {order ? <div className="live-order-card" data-testid="order-result" role="status"><div className="live-order-heading"><span><CheckIcon /> 订单已创建</span><em>{orderStatus}</em></div><dl><div><dt>订单编号</dt><dd data-testid="order-id">{order.orderNo ?? order.id}</dd></div><div><dt>创建时间</dt><dd>{timeLabel(order.createTime)}</dd></div></dl>{order.status === 1 && <p>支付功能暂未开放，订单当前为待支付状态。</p>}{order.status === 3 && <p>此订单已取消，当前活动按一人一券规则处理。</p>}<button className="outline-button" data-testid="order-status-refresh" disabled={querying || polling} onClick={() => checkOrder(false)}>{querying ? '正在刷新…' : '刷新订单状态'}</button></div> : <>
      {unresolved && <div className="reservation-message" role="status"><strong>{phase === 'accepted' ? '已获得抢券资格，正在确认订单' : '抢券结果待确认'}</strong><p>{phase === 'accepted' ? '订单正在处理，请以查询到的订单结果为准。' : '请求可能已经到达服务端，请先查询订单结果。'}</p></div>}
      {reserveError && <p className="reservation-error" role="alert">{reserveError}</p>}
      <button className="coral-button" data-testid="seckill-submit" disabled={reserveDisabled} onClick={() => void reserve()}>{buttonLabel}</button>
    </>}
    {token && userId && <div className="order-lookup"><p className={lookupError ? 'reservation-error' : ''} role={lookupError ? 'alert' : 'status'}>{lookupError ? `订单查询暂未完成：${lookupError}` : querying ? '正在查询我的订单…' : order ? '' : lookupNote}</p>{!order && <button className="outline-button" data-testid="order-status-refresh" disabled={querying || polling || phase === 'submitting'} onClick={() => checkOrder(unresolved)}>{polling ? '正在自动确认…' : querying ? '正在查询…' : '刷新订单状态'}</button>}</div>}
    {unresolved && checked && !querying && !polling && !lookupError && state === '进行中' && !soldOut && <div className="reservation-retry"><p>仍未查到订单？可以手动重新提交；如果上次已受理，系统会按一人一券处理。</p><button className="outline-button" data-testid="seckill-retry" onClick={() => void reserve(true)}>重新提交抢券</button></div>}
  </section>;
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

const emptyBlogLike = (identity: string, loading = false): BlogLikeState => ({ identity, data: null, phase: loading ? 'loading' : 'idle', message: '', error: '', uncertainTarget: null });

function validBlogLike(value: unknown): value is BlogLike {
  if (!value || typeof value !== 'object') return false;
  const result = value as Partial<BlogLike>;
  return typeof result.likedByMe === 'boolean' && typeof result.liked === 'number'
    && Number.isSafeInteger(result.liked) && result.liked >= 0 && result.liked <= 2147483647;
}

function useBlogLike(blogId: string, token: string) {
  const identity = `${blogId}:${token}`;
  const [saved, setSaved] = useState<BlogLikeState>(() => emptyBlogLike(identity, Boolean(token)));
  const current = useRef(identity);
  current.current = identity;
  const request = useRef<AbortController | null>(null);
  const path = `/blog/${encodeURIComponent(blogId)}/like`;

  const reload = useCallback(async () => {
    if (!token || localStorage.getItem(TOKEN_KEY) !== token || request.current) return;
    const controller = new AbortController();
    request.current = controller;
    const active = () => !controller.signal.aborted && current.current === identity && localStorage.getItem(TOKEN_KEY) === token;
    setSaved(previous => ({ ...(previous.identity === identity ? previous : emptyBlogLike(identity)), phase: 'loading', message: '' }));
    try {
      const result = await api<BlogLike>(path, { signal: controller.signal });
      if (!active()) return;
      if (!validBlogLike(result)) throw new ApiError('点赞状态返回格式不正确', 200);
      setSaved({ identity, data: result, phase: 'idle', message: '', error: '', uncertainTarget: null });
    } catch {
      if (!active()) return;
      // A missing GET endpoint does not mean the user has not liked this note.
      setSaved(previous => ({ ...previous, phase: 'idle',
        message: previous.data ? '状态暂未刷新，当前显示上次确认的结果。' : '当前点赞状态待确认，你可以明确选择点赞或取消点赞。',
        error: previous.uncertainTarget !== null ? '上次操作结果仍待确认，可刷新状态或重新提交相同选择。' : '' }));
    } finally {
      if (request.current === controller) request.current = null;
    }
  }, [identity, path, token]);

  useEffect(() => {
    request.current?.abort();
    request.current = null;
    setSaved(emptyBlogLike(identity, Boolean(token)));
    void reload();
    return () => { request.current?.abort(); request.current = null; };
  }, [identity, reload, token]);

  async function setLiked(liked: boolean) {
    if (!token || localStorage.getItem(TOKEN_KEY) !== token || request.current) return;
    const controller = new AbortController();
    request.current = controller;
    const active = () => !controller.signal.aborted && current.current === identity && localStorage.getItem(TOKEN_KEY) === token;
    setSaved(previous => ({ ...(previous.identity === identity ? previous : emptyBlogLike(identity)), phase: 'saving', error: '', message: liked ? '正在点赞…' : '正在取消点赞…' }));
    try {
      const result = await api<BlogLike>(path, { method: 'PUT', body: JSON.stringify({ liked }), signal: controller.signal });
      if (!active()) return;
      if (!validBlogLike(result) || result.likedByMe !== liked) throw new ApiError('点赞响应与提交状态不一致', 200);
      setSaved({ identity, data: result, phase: 'idle', message: liked ? '已点赞' : '已取消点赞', error: '', uncertainTarget: null });
    } catch (error) {
      if (!active()) return;
      const uncertain = !(error instanceof ApiError) || error.status === 0 || error.status >= 500 || (error.status >= 200 && error.status < 300);
      setSaved(previous => ({ ...previous, phase: 'idle', data: uncertain ? null : previous.data,
        uncertainTarget: uncertain ? liked : null, message: '',
        error: uncertain ? '这次操作结果暂未确认。可刷新状态，或重新提交相同选择。' : (error as Error).message }));
    } finally {
      if (request.current === controller) request.current = null;
    }
  }

  const state = saved.identity === identity ? saved : emptyBlogLike(identity, Boolean(token));
  return { state, busy: state.phase !== 'idle', reload, setLiked };
}

function StoryLikeActions({ likes, comments }: { likes: ReturnType<typeof useBlogLike>; comments: number }) {
  const { state, busy, reload, setLiked } = likes;
  const target = state.uncertainTarget ?? (state.data ? !state.data.likedByMe : true);
  const label = state.phase === 'saving' ? state.message : state.uncertainTarget !== null
    ? state.uncertainTarget ? '重新确认点赞' : '重新确认取消点赞'
    : state.data?.likedByMe ? '取消点赞' : '点赞';
  return <section className="live-like-section" aria-label="笔记互动">
    <div className="live-story-stats">
      <button type="button" className={`live-like-button${state.data?.likedByMe ? ' is-liked' : ''}`} data-testid="live-like-submit" aria-label={label} aria-pressed={state.data?.likedByMe} aria-busy={state.phase === 'saving'} disabled={busy} onClick={() => void setLiked(target)}>
        {state.data?.likedByMe ? <HeartFilledIcon aria-hidden="true" /> : <HeartIcon aria-hidden="true" />}<span>{label}</span><small data-testid="live-like-count">{state.data ? `${state.data.liked} 次赞` : '赞数待确认'}</small>
      </button>
      <span><ChatBubbleIcon aria-hidden="true" />{comments} 条评论</span>
    </div>
    <div className="live-like-feedback" aria-live="polite">
      {state.phase === 'loading' && <p role="status">正在读取点赞状态…</p>}
      {state.message && <p role="status">{state.message}</p>}
      {state.error && <p className="live-like-error" role="alert">{state.error}</p>}
      <div className="live-like-tools">
        {!state.data && state.uncertainTarget === null && <button type="button" className="outline-button" data-testid="live-like-cancel" disabled={busy} onClick={() => void setLiked(false)}>取消点赞</button>}
        <button type="button" className="text-button" disabled={busy} onClick={() => void reload()}>刷新点赞状态</button>
      </div>
    </div>
  </section>;
}

function positiveUserId(value: unknown): string | null {
  if (typeof value === 'string' && /^[1-9]\d*$/.test(value)) return value;
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0 ? String(value) : null;
}

const emptyAuthorFollow = (identity: string, loading = false): AuthorFollowRequestState => ({ identity, data: null, phase: loading ? 'loading' : 'idle', error: '', message: '', uncertainTarget: null });

function validAuthorFollow(value: unknown, targetUserId: string): value is AuthorFollow {
  if (!value || typeof value !== 'object') return false;
  const result = value as Partial<AuthorFollow>;
  return result.targetUserId === targetUserId && typeof result.followed === 'boolean';
}

function seedAuthorFollow(identity: string, enabled: boolean, targetUserId: string | null, followed?: boolean): AuthorFollowRequestState {
  return { ...emptyAuthorFollow(identity, enabled && followed === undefined),
    data: enabled && targetUserId && typeof followed === 'boolean' ? { targetUserId, followed } : null };
}

function useAuthorFollow(targetUserId: string | null, token: string, enabled: boolean, initialFollowed?: boolean, onConfirmed?: (state: AuthorFollow) => void) {
  const identity = `${targetUserId || ''}:${token}:${enabled}`;
  const [saved, setSaved] = useState<AuthorFollowRequestState>(() => seedAuthorFollow(identity, enabled, targetUserId, initialFollowed));
  const current = useRef(identity);
  current.current = identity;
  const request = useRef<AbortController | null>(null);
  const confirmedCallback = useRef(onConfirmed);
  confirmedCallback.current = onConfirmed;
  const hasUnconfirmedWrite = useRef(false);
  const path = targetUserId ? `/user/${encodeURIComponent(targetUserId)}/follow` : null;

  const reload = useCallback(async () => {
    if (!enabled || !path || !targetUserId || !token || localStorage.getItem(TOKEN_KEY) !== token || request.current) return;
    const controller = new AbortController();
    request.current = controller;
    const active = () => !controller.signal.aborted && current.current === identity && localStorage.getItem(TOKEN_KEY) === token;
    setSaved(previous => ({ ...(previous.identity === identity ? previous : emptyAuthorFollow(identity)), phase: 'loading', error: '', message: '' }));
    try {
      const result = await api<AuthorFollow>(path, { signal: controller.signal });
      if (!active()) return;
      if (!validAuthorFollow(result, targetUserId)) throw new ApiError('关注状态返回格式不正确，请重试', 200);
      const resolvedWrite = hasUnconfirmedWrite.current;
      hasUnconfirmedWrite.current = false;
      setSaved({ identity, data: result, phase: 'idle', error: '', message: '', uncertainTarget: null });
      if (resolvedWrite) confirmedCallback.current?.(result);
    } catch (error) {
      if (!active()) return;
      setSaved(previous => ({ ...previous, phase: 'idle', error: (error as Error).message,
        message: previous.uncertainTarget !== null ? '上次操作结果仍待确认。' : '' }));
    } finally {
      if (request.current === controller) request.current = null;
    }
  }, [enabled, identity, path, targetUserId, token]);

  useEffect(() => {
    request.current?.abort();
    request.current = null;
    hasUnconfirmedWrite.current = false;
    setSaved(seedAuthorFollow(identity, enabled, targetUserId, initialFollowed));
    // Lists already include the confirmed state; avoid one GET per row.
    if (initialFollowed === undefined) void reload();
    return () => { request.current?.abort(); request.current = null; };
  }, [enabled, identity, initialFollowed, reload, targetUserId]);

  async function setFollowed(followed: boolean) {
    if (!enabled || !path || !targetUserId || !token || localStorage.getItem(TOKEN_KEY) !== token || request.current) return;
    const controller = new AbortController();
    request.current = controller;
    const active = () => !controller.signal.aborted && current.current === identity && localStorage.getItem(TOKEN_KEY) === token;
    setSaved(previous => ({ ...(previous.identity === identity ? previous : emptyAuthorFollow(identity)), phase: 'saving', error: '', message: followed ? '正在关注…' : '正在取消关注…' }));
    try {
      const result = await api<AuthorFollow>(path, { method: 'PUT', body: JSON.stringify({ followed }), signal: controller.signal });
      if (!active()) return;
      if (!validAuthorFollow(result, targetUserId) || result.followed !== followed) throw new ApiError('关注响应与提交状态不一致', 200);
      hasUnconfirmedWrite.current = false;
      setSaved({ identity, data: result, phase: 'idle', error: '', message: followed ? '已关注这位作者' : '已取消关注', uncertainTarget: null });
      confirmedCallback.current?.(result);
    } catch (error) {
      if (!active()) return;
      const uncertain = !(error instanceof ApiError) || error.status === 0 || error.status >= 500 || (error.status >= 200 && error.status < 300);
      hasUnconfirmedWrite.current = uncertain;
      setSaved(previous => ({ ...previous, phase: 'idle', data: uncertain ? null : previous.data,
        uncertainTarget: uncertain ? followed : null, message: '',
        error: uncertain ? '这次操作结果暂未确认，可以刷新状态，或重新提交相同选择。' : (error as Error).message }));
    } finally {
      if (request.current === controller) request.current = null;
    }
  }

  const state = saved.identity === identity ? saved : seedAuthorFollow(identity, enabled, targetUserId, initialFollowed);
  return { state, busy: state.phase !== 'idle', reload, setFollowed };
}

function StoryAuthor({ blog, token, viewerId, identityLoading, identityError, onIdentityRetry, onLogin, onBusyChange, onOpenUser }: {
  blog: BlogDetail; token: string; viewerId: string | null; identityLoading: boolean; identityError: string;
  onIdentityRetry: () => void; onLogin: () => void; onBusyChange: (busy: boolean) => void; onOpenUser: (id: string) => void;
}) {
  const authorId = positiveUserId(blog.userId);
  const isSelf = Boolean(viewerId && authorId === viewerId);
  const enabled = Boolean(token && viewerId && authorId && !isSelf && !identityLoading && !identityError);
  const follow = useAuthorFollow(authorId, token, enabled);
  const { state, busy, reload, setFollowed } = follow;
  const needsIdentity = Boolean(token && (!viewerId || identityLoading || identityError));
  useEffect(() => { onBusyChange(busy); return () => onBusyChange(false); }, [busy, onBusyChange]);

  const label = !token ? '+ 关注' : needsIdentity ? identityLoading ? '确认身份…' : '重试登录状态'
    : state.phase === 'loading' ? '查询中…' : state.phase === 'saving' ? state.message
    : state.uncertainTarget !== null ? state.uncertainTarget ? '确认关注' : '确认取消'
    : state.data ? state.data.followed ? '已关注' : '+ 关注' : '重试查询';
  const actionLabel = !token ? '登录后关注作者' : needsIdentity ? '重试登录状态'
    : busy ? label : state.uncertainTarget !== null ? state.uncertainTarget ? '重新确认关注作者' : '重新确认取消关注'
    : state.data ? state.data.followed ? '取消关注作者' : '关注作者' : '重试查询关注状态';

  function act() {
    if (!token) { onLogin(); return; }
    if (needsIdentity) { onIdentityRetry(); return; }
    if (state.uncertainTarget !== null) { void setFollowed(state.uncertainTarget); return; }
    if (!state.data) { void reload(); return; }
    void setFollowed(!state.data.followed);
  }

  return <section className="story-author" aria-label="作者信息">
    <div className="detail-author"><button type="button" className="author-profile-link" aria-label={"查看" + (blog.authorName || "作者") + "的主页"} disabled={!authorId || state.phase === 'saving'} onClick={() => authorId && onOpenUser(authorId)}><FeedAvatar source={blog.authorIcon} /><span className="detail-author-info"><strong>{blog.authorName || '未知用户'}</strong><small>{timeLabel(blog.createTime)} 发布</small></span></button>
      {authorId && !isSelf && <button type="button" className={`author-follow-button${state.data?.followed ? ' is-followed' : ''}`} data-testid="author-follow" aria-label={actionLabel} aria-pressed={token && !needsIdentity ? state.data?.followed : undefined} aria-busy={busy || identityLoading} disabled={busy || Boolean(token && identityLoading)} onClick={act}>{state.data?.followed && <CheckIcon aria-hidden="true" />}<span>{label}</span></button>}
    </div>
    {token && authorId && !isSelf && <div className="author-follow-feedback" aria-live="polite">
      {needsIdentity && !identityLoading && <p className="author-follow-error" role="alert">暂时无法确认当前账号，请重试登录状态。</p>}
      {state.message && <p role="status">{state.message}</p>}
      {state.error && <p className="author-follow-error" role="alert">{state.error}</p>}
      {!needsIdentity && (state.error || state.uncertainTarget !== null) && <button type="button" className="text-button" disabled={busy} onClick={() => void reload()}>刷新关注状态</button>}
    </div>}
  </section>;
}


function StoryGalleryImage({source, title, index, onReload}: {source: string; title: string; index: number; onReload: () => void}) {
  const [failed, setFailed] = useState(false);
  useEffect(() => { setFailed(false); }, [source]);
  return !failed ? <img src={source} alt={title + '，第 ' + (index + 1) + ' 张图片'} decoding="async" onError={() => setFailed(true)} /> : <div className="live-story-image-error"><CameraIcon /><span>这张图片暂时无法显示。</span><button type="button" className="outline-button" onClick={onReload}>刷新图片</button></div>;
}

function StoryGallery({imageUrls, coverImage, title, onReload}: {imageUrls?: string[]; coverImage: string; title: string; onReload: () => void}) {
  const {device} = useMobileDevice();
  const wrapper = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState(0);
  const candidates = imageUrls === undefined ? coverImage ? [coverImage] : [] : imageUrls;
  const valid = Array.isArray(candidates) && candidates.every(source => typeof source === 'string' && (/^https?:\/\//i.test(source) || source.startsWith('/assets/')));
  const sources: string[] = valid ? candidates : [];
  const identity = sources.join('|');
  useEffect(() => {
    const node = wrapper.current?.querySelector<HTMLElement>('.story-image-carousel');
    if (node) node.scrollLeft = 0;
    setActive(0);
  }, [identity, device.geometry.screen.width]);
  function show(index: number) {
    const node = wrapper.current?.querySelector<HTMLElement>('.story-image-carousel');
    if (!node) return;
    node.scrollTo({left: index * node.clientWidth, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth'});
  }
  if (!valid || !sources.length) return <div className="live-story-image-error"><CameraIcon /><span>{valid ? '图片暂时无法显示，可以刷新笔记重试。' : '笔记图片返回格式不正确，请刷新重试。'}</span><button type="button" className="outline-button" onClick={onReload}>重新加载</button></div>;
  return <div className="story-gallery" ref={wrapper} onScrollCapture={event => {
    const node = event.target;
    if (node instanceof HTMLElement && node.classList.contains('story-image-carousel')) {
      setActive(Math.max(0, Math.min(sources.length - 1, Math.round(node.scrollLeft / Math.max(1, node.clientWidth)))));
    }
  }}>
    <Carousel className="story-image-carousel" contentClassName="story-image-track" ariaLabel="笔记图片轮播" draggingEnabled={sources.length > 1}>
      {sources.map((source, index) => <figure className="story-image-slide" key={index + ':' + source} style={{width: device.geometry.screen.width}} aria-hidden={index !== active} role="group" aria-label={'第 ' + (index + 1) + ' 张图片'}>
        <StoryGalleryImage source={source} title={title} index={index} onReload={onReload} />
        {sources.length > 1 && <><figcaption>{index + 1} / {sources.length}</figcaption><button type="button" className="story-gallery-prev" aria-label="上一张图片" tabIndex={index === active ? 0 : -1} disabled={index === 0} onClick={() => show(index - 1)}><ChevronLeftIcon /></button><button type="button" className="story-gallery-next" aria-label="下一张图片" tabIndex={index === active ? 0 : -1} disabled={index === sources.length - 1} onClick={() => show(index + 1)}><ChevronRightIcon /></button></>}
      </figure>)}
    </Carousel>
    {sources.length > 1 && <div className="story-gallery-guide"><span>左右滑动查看图片</span><span className="visually-hidden" role="status" aria-live="polite">{active + 1} / {sources.length}</span></div>}
  </div>;
}

function PublishedStory({ blogId, token, viewerId, identityLoading, identityError, onIdentityRetry, onBack, onLogin, onNew, onOpenUser }: {
  blogId: string; token: string; viewerId: string | null; identityLoading: boolean; identityError: string;
  onIdentityRetry: () => void; onBack: () => void; onLogin: () => void; onNew: () => void; onOpenUser: (id: string) => void;
}) {
  const story = useResource<BlogDetail>(`/blog/${encodeURIComponent(blogId)}`, token);
  const likes = useBlogLike(blogId, token);
  const [followBusy, setFollowBusy] = useState(false);
  const reload = () => { story.reload(); void likes.reload(); };
  const blog = story.data;
  const mismatched = Boolean(blog && String(blog.id) !== blogId);

  return <>
    <header className="page-header story-detail-header"><button onClick={onBack} aria-label="返回上一页"><ArrowLeftIcon /></button><span>巷里故事</span><button className="text-button live-story-refresh" onClick={reload} disabled={story.loading || likes.busy || followBusy}>刷新</button></header>
    <MobileScroll className="m1-scroll full-page story-detail-scroll">
    <RequestState loading={story.loading} error={story.error || (mismatched ? '笔记响应与当前内容不一致，请重新加载' : '')} onRetry={reload} />
    {blog && !mismatched && !story.error && <>
      <StoryGallery imageUrls={blog.imageUrls} coverImage={blog.coverImage} title={blog.title} onReload={reload} />
      <div className="detail-body">
        <StoryAuthor blog={blog} token={token} viewerId={viewerId} identityLoading={identityLoading} identityError={identityError} onIdentityRetry={onIdentityRetry} onLogin={onLogin} onBusyChange={setFollowBusy} onOpenUser={onOpenUser} />
        <h1>{blog.title}</h1>
        <p className="live-story-content">{blog.content}</p>
        {token ? <StoryLikeActions likes={likes} comments={blog.comments ?? 0} /> : <section className="live-like-section" aria-label="笔记互动"><div className="live-story-stats"><button type="button" className="live-like-button" onClick={onLogin}><HeartIcon aria-hidden="true" /><span>登录后点赞</span><small>{blog.liked ?? 0} 次赞</small></button><span><ChatBubbleIcon aria-hidden="true" />{blog.comments ?? 0} 条评论</span></div></section>}
        <p className="preview-caption">评论功能正在准备中。</p>
        <button className="coral-button" onClick={onNew}>继续记录好地方 <PlusIcon /></button>
      </div>
    </>}
    </MobileScroll>
  </>;
}

function FeedCover({ source, title }: { source: string | null; title: string }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => { setFailed(false); }, [source]);
  const readable = Boolean(source && (/^https?:\/\//i.test(source) || source.startsWith('/assets/')));
  return readable && !failed
    ? <img className="story-photo" src={source!} alt={title} loading="lazy" decoding="async" onError={() => setFailed(true)} />
    : <span className="story-photo feed-cover-empty"><CameraIcon aria-hidden="true" /><span>封面暂不可用</span></span>;
}

function FeedAvatar({ source }: { source: string | null }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => { setFailed(false); }, [source]);
  return <span className="live-author-avatar">{source && !failed ? <img src={source} alt="" loading="lazy" onError={() => setFailed(true)} /> : <PersonIcon aria-hidden="true" />}</span>;
}

function validBlogFeedPage(value: unknown, current: number): value is BlogFeedPage {
  if (!value || typeof value !== 'object') return false;
  const page = value as Partial<BlogFeedPage>;
  const validCount = (count: unknown) => count === null || (typeof count === 'number' && Number.isSafeInteger(count) && count >= 0);
  return typeof page.total === 'number' && Number.isSafeInteger(page.total) && page.total >= 0
    && page.current === current && typeof page.size === 'number' && Number.isInteger(page.size) && page.size >= 1 && page.size <= 50
    && Array.isArray(page.records) && page.records.length <= page.size
    && page.records.every(note => note && typeof note.id === 'string' && /^[1-9]\d*$/.test(note.id)
      && (note.userId === null || (typeof note.userId === 'string' && /^[1-9]\d*$/.test(note.userId)))
      && typeof note.title === 'string' && typeof note.authorName === 'string'
      && (note.coverImage === null || typeof note.coverImage === 'string')
      && (note.authorIcon === null || typeof note.authorIcon === 'string')
      && (note.createTime === null || typeof note.createTime === 'string')
      && validCount(note.liked) && validCount(note.comments))
    && new Set(page.records.map(note => note.id)).size === page.records.length;
}

function BlogFeed({ current, refreshKey, onPageChange, onOpenNote, onNew, onOpenUser }: {
  current: number; refreshKey: string; onPageChange: (page: number) => void;
  onOpenNote: (id: string) => void; onNew: () => void; onOpenUser: (id: string) => void;
}) {
  const result = useResource<BlogFeedPage>(`/blog/feed?current=${current}&size=10`, refreshKey);
  const page = validBlogFeedPage(result.data, current) ? result.data : null;
  const pages = page ? Math.max(1, Math.ceil(page.total / page.size)) : 1;
  const outOfRange = Boolean(page && current > pages);
  useEffect(() => { if (outOfRange) onPageChange(pages); }, [outOfRange, pages, onPageChange]);
  const error = result.error || (!result.loading && !page ? '笔记列表返回格式不正确，请重新加载' : '');

  return <section className="feed-list live-feed" aria-label="笔记广场">
    <div className="feed-toolbar"><div><strong>笔记广场</strong><span>{page ? `最新发布 · ${page.total} 篇笔记` : '看看大家新发现的好地方'}</span></div><button type="button" className="text-button" onClick={result.reload} disabled={result.loading}>刷新列表</button></div>
    <RequestState loading={result.loading || outOfRange} error={error} onRetry={result.reload} />
    {page && !result.loading && !error && !outOfRange && <>
      {page.records.length === 0 ? <div className="empty-state"><CameraIcon /><h2>第一个好地方，等你来记录</h2><p>暂时还没有笔记，分享你最近的一次发现吧。</p><button className="primary-button" onClick={onNew}>写一篇笔记</button></div> : <>
        {page.records.map(note => <article className="story-card live-feed-card" key={note.id} data-testid="feed-note">
          <button className="story-photo-button" onClick={() => onOpenNote(note.id)} aria-label={`查看笔记：${note.title}`}><FeedCover source={note.coverImage} title={note.title} /><span className="photo-counter">读一篇故事 <ChevronRightIcon aria-hidden="true" /></span></button>
          <button type="button" className="author-row feed-author-link" aria-label={"查看" + (note.authorName || "作者") + "的主页"} disabled={!note.userId} onClick={() => note.userId && onOpenUser(note.userId)}><FeedAvatar source={note.authorIcon} /><span className="author-meta"><strong>{note.authorName || '未知用户'}</strong><span>{note.createTime ? `${timeLabel(note.createTime)} 发布` : '分享身边的好地方'}</span></span></button>
          <div className="story-content"><button className="story-text-button" onClick={() => onOpenNote(note.id)}><h2>{note.title}</h2></button><div className="feed-card-footer"><div className="feed-card-stats"><span><HeartIcon aria-hidden="true" />{note.liked ?? 0}</span><span><ChatBubbleIcon aria-hidden="true" />{note.comments ?? 0}</span></div><button type="button" className="text-button" onClick={() => onOpenNote(note.id)}>阅读全文 <ChevronRightIcon aria-hidden="true" /></button></div></div>
        </article>)}
        <nav className="pagination feed-pagination" aria-label="笔记广场分页"><button type="button" disabled={current <= 1} onClick={() => onPageChange(current - 1)}>上一页</button><span>第 {current} / {pages} 页</span><button type="button" disabled={current >= pages} onClick={() => onPageChange(current + 1)}>下一页</button></nav>
      </>}
    </>}
  </section>;
}

function NoteThumbnail({ source }: { source: string | null }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => { setFailed(false); }, [source]);
  const readable = Boolean(source && (/^https?:\/\//i.test(source) || source.startsWith('/assets/')));
  return readable && !failed
    ? <img className="my-note-cover" src={source!} alt="" loading="lazy" decoding="async" onError={() => setFailed(true)} />
    : <span className="my-note-cover my-note-cover-empty" aria-label="封面暂不可用"><CameraIcon /></span>;
}

function MyNotes({ token, current, refreshKey, onPageChange, onLogin, onNew, onOpenNote }: {
  token: string; current: number; refreshKey: string;
  onPageChange: (page: number) => void; onLogin: () => void;
  onNew: () => void; onOpenNote: (id: string) => void;
}) {
  const result = useResource<BlogPage>(token ? `/blog/of/me?current=${current}&size=10` : null, `${token}:${refreshKey}`);
  const data = result.data;
  const valid = Boolean(data && Array.isArray(data.records)
    && Number.isSafeInteger(data.total) && data.total >= 0
    && data.current === current && Number.isInteger(data.size) && data.size >= 1 && data.size <= 50
    && data.records.every(note => note && typeof note.id === 'string' && /^[1-9]\d*$/.test(note.id)
      && typeof note.title === 'string' && (note.coverImage === null || typeof note.coverImage === 'string')));
  const page = valid ? data : null;
  const pages = page ? Math.max(1, Math.ceil(page.total / page.size)) : 1;
  const outOfRange = Boolean(page && current > pages);
  useEffect(() => { if (outOfRange) onPageChange(pages); }, [outOfRange, pages, onPageChange]);
  const error = result.error || (!result.loading && token && !valid ? '笔记列表返回格式不正确，请重新加载' : '');

  if (!token) return <div className="empty-state"><PersonIcon /><h2>登录后，找回你的记录</h2><p>已发布的笔记会保存在你的账号中。</p><button className="primary-button" onClick={onLogin}>登录后查看</button></div>;

  return <div className="my-notes-body">
    <div className="my-notes-toolbar"><span>{page ? `已发布 ${page.total} 篇` : '你记录的好地方，都在这里'}</span><button className="text-button" onClick={result.reload} disabled={result.loading}>刷新列表</button></div>
    <RequestState loading={result.loading || outOfRange} error={error} onRetry={result.reload} />
    {page && !result.loading && !error && !outOfRange && <>
      {page.total === 0 ? <div className="empty-state my-notes-empty"><CameraIcon /><h2>把第一个好地方记下来</h2><p>还没有发布笔记，先从一张照片开始吧。</p><button className="primary-button" onClick={onNew}>写第一篇笔记</button></div> : <>
        <div className="my-notes-list">
          {page.records.map(note => <button className="my-note-card" key={note.id} onClick={() => onOpenNote(note.id)} aria-label={`查看笔记：${note.title}`}>
            <NoteThumbnail source={note.coverImage} />
            <span className="my-note-copy"><strong>{note.title}</strong><small>{timeLabel(note.createTime)} 发布</small><span className="my-note-stats"><span><HeartIcon aria-hidden="true" />{note.liked ?? 0}</span><span><ChatBubbleIcon aria-hidden="true" />{note.comments ?? 0}</span></span></span>
            <ChevronRightIcon className="my-note-arrow" aria-hidden="true" />
          </button>)}
        </div>
        <div className="pagination my-notes-pagination"><button disabled={current <= 1} onClick={() => onPageChange(current - 1)}>上一页</button><span>第 {current} / {pages} 页</span><button disabled={current >= pages} onClick={() => onPageChange(current + 1)}>下一页</button></div>
      </>}
    </>}
  </div>;
}

function validFollowPeoplePage(value: unknown, current: number): value is FollowPeoplePage {
  if (!value || typeof value !== 'object') return false;
  const page = value as Partial<FollowPeoplePage>;
  return typeof page.total === 'number' && Number.isSafeInteger(page.total) && page.total >= 0
    && page.current === current && typeof page.size === 'number' && Number.isInteger(page.size) && page.size >= 1 && page.size <= 50
    && Array.isArray(page.records) && page.records.length <= page.size
    && page.records.every(person => person && typeof person.userId === 'string' && positiveUserId(person.userId) !== null
      && typeof person.nickName === 'string' && (person.icon === null || typeof person.icon === 'string')
      && (person.followTime === null || typeof person.followTime === 'string') && typeof person.followed === 'boolean')
    && new Set(page.records.map(person => person.userId)).size === page.records.length;
}

function FollowPersonRow({ person, mode, token, viewerId, identityLoading, identityError, onIdentityRetry, onConfirmed, onBusyChange, onOpenUser }: {
  person: FollowPerson; mode: FollowListMode; token: string; viewerId: string | null;
  identityLoading: boolean; identityError: string; onIdentityRetry: () => void;
  onConfirmed: () => void; onBusyChange: (id: string, busy: boolean) => void; onOpenUser: (id: string) => void;
}) {
  const isSelf = person.userId === viewerId;
  const needsIdentity = !viewerId || identityLoading || Boolean(identityError);
  const follow = useAuthorFollow(person.userId, token, Boolean(token && !needsIdentity && !isSelf), person.followed, onConfirmed);
  const { state, busy, reload, setFollowed } = follow;
  useEffect(() => { onBusyChange(person.userId, busy); return () => onBusyChange(person.userId, false); }, [busy, onBusyChange, person.userId]);
  const name = person.nickName || '未知用户';
  const label = needsIdentity ? identityLoading ? '确认身份…' : '重试登录状态'
    : state.phase === 'loading' ? '查询中…' : state.phase === 'saving' ? state.message
    : state.uncertainTarget !== null ? state.uncertainTarget ? '确认关注' : '确认取消'
    : state.data ? state.data.followed ? '已关注' : mode === 'followers' ? '回关' : '+ 关注' : '重试查询';
  const actionLabel = needsIdentity ? '重试登录状态' : busy ? label
    : state.uncertainTarget !== null ? `${state.uncertainTarget ? '重新确认关注' : '重新确认取消关注'}${name}`
    : state.data ? `${state.data.followed ? '取消关注' : mode === 'followers' ? '回关' : '关注'}${name}` : `重试查询${name}的关注状态`;

  function act() {
    if (needsIdentity) { onIdentityRetry(); return; }
    if (state.uncertainTarget !== null) { void setFollowed(state.uncertainTarget); return; }
    if (!state.data) { void reload(); return; }
    void setFollowed(!state.data.followed);
  }

  return <article className="follow-person" data-testid="follow-person" data-user-id={person.userId}>
    <div className="follow-person-main"><button type="button" className="follow-person-link" aria-label={"查看" + name + "的主页"} disabled={state.phase === 'saving'} onClick={() => onOpenUser(person.userId)}><FeedAvatar source={person.icon} /><span className="follow-person-info"><strong>{name}</strong><small>{person.followTime ? `${timeLabel(person.followTime)} ${mode === 'followers' ? '关注了你' : '关注'}` : '关注时间待更新'}</small></span></button>
      {isSelf ? <span className="follow-person-self">自己</span> : <button type="button" className={`author-follow-button${state.data?.followed ? ' is-followed' : ''}`} data-testid="relation-follow" aria-label={actionLabel} aria-pressed={!needsIdentity ? state.data?.followed : undefined} aria-busy={busy || identityLoading} disabled={busy || identityLoading} onClick={act}>{state.data?.followed && <CheckIcon aria-hidden="true" />}<span>{label}</span></button>}
    </div>
    {!isSelf && <div className="author-follow-feedback" aria-live="polite">{state.message && <p role="status">{state.message}</p>}{state.error && <p className="author-follow-error" role="alert">{state.error}</p>}{!needsIdentity && (state.error || state.uncertainTarget !== null) && <button type="button" className="text-button" disabled={busy} onClick={() => void reload()}>刷新关注状态</button>}</div>}
  </article>;
}

function FollowPeople({ mode, token, current, viewerId, identityLoading, identityError, onIdentityRetry, onPageChange, onModeChange, onLogin, onDiscover, onOpenUser }: {
  mode: FollowListMode; token: string; current: number; viewerId: string | null; identityLoading: boolean; identityError: string;
  onIdentityRetry: () => void; onPageChange: (page: number) => void; onModeChange: (mode: FollowListMode) => void;
  onLogin: () => void; onDiscover: () => void; onOpenUser: (id: string) => void;
}) {
  const result = useResource<FollowPeoplePage>(token ? `/user/me/${mode}?current=${current}&size=10` : null, token);
  const [busyIds, setBusyIds] = useState<Set<string>>(() => new Set());
  const onBusyChange = useCallback((id: string, busy: boolean) => {
    setBusyIds(previous => {
      if (previous.has(id) === busy) return previous;
      const next = new Set(previous);
      if (busy) next.add(id); else next.delete(id);
      return next;
    });
  }, []);
  const busy = busyIds.size > 0;
  const page = validFollowPeoplePage(result.data, current) ? result.data : null;
  const pages = page ? Math.max(1, Math.ceil(page.total / page.size)) : 1;
  const outOfRange = Boolean(page && current > pages);
  useEffect(() => { if (outOfRange) onPageChange(pages); }, [onPageChange, outOfRange, pages]);
  const error = result.error || (!result.loading && token && !page ? '列表返回格式不正确，请重新加载' : '');

  if (!token) return <div className="empty-state"><PersonIcon /><h2>登录后，看看彼此的发现</h2><p>你的关注和粉丝会保存在账号中。</p><button className="primary-button" onClick={onLogin}>登录后查看</button></div>;

  return <section className="follow-people-body" aria-label={mode === 'following' ? '我的关注列表' : '我的粉丝列表'}>
    <div className="follow-people-tabs" role="tablist" aria-label="关注与粉丝"><button type="button" role="tab" aria-selected={mode === 'following'} disabled={busy} onClick={() => onModeChange('following')}>关注</button><button type="button" role="tab" aria-selected={mode === 'followers'} disabled={busy} onClick={() => onModeChange('followers')}>粉丝</button></div>
    <div className="my-notes-toolbar"><span>{page ? mode === 'following' ? `已关注 ${page.total} 人` : `${page.total} 位粉丝` : '每一次相遇，都值得记下来'}</span><button type="button" className="text-button" disabled={result.loading || busy} onClick={result.reload}>刷新列表</button></div>
    {identityError && <RequestState loading={false} error={identityError} onRetry={onIdentityRetry} />}
    <RequestState loading={result.loading || outOfRange} error={error} onRetry={result.reload} />
    {page && !result.loading && !error && !outOfRange && <>
      {page.total === 0 ? <div className="empty-state follow-people-empty"><PersonIcon /><h2>{mode === 'following' ? '从一位喜欢的作者开始' : '下一次相遇，正在路上'}</h2><p>{mode === 'following' ? '读一篇喜欢的笔记，关注分享它的人。' : '继续分享好地方，让更多人认识你。'}</p><button className="primary-button" onClick={onDiscover}>去发现笔记</button></div> : <>
        {page.records.length === 0 && <div className="request-state"><p>本页记录已变化，请刷新列表。</p><button type="button" className="outline-button" onClick={result.reload}>重新加载</button></div>}
        <div className="follow-people-list">{page.records.map(person => <FollowPersonRow key={person.userId} person={person} mode={mode} token={token} viewerId={viewerId} identityLoading={identityLoading} identityError={identityError} onIdentityRetry={onIdentityRetry} onConfirmed={result.reload} onBusyChange={onBusyChange} onOpenUser={onOpenUser} />)}</div>
        <nav className="pagination" aria-label="关注与粉丝分页"><button type="button" disabled={busy || current <= 1} onClick={() => onPageChange(current - 1)}>上一页</button><span>第 {current} / {pages} 页</span><button type="button" disabled={busy || current >= pages} onClick={() => onPageChange(current + 1)}>下一页</button></nav>
      </>}
    </>}
  </section>;
}



function validPublicProfile(value: unknown, userId: string): value is PublicUserProfile {
  if (!value || typeof value !== 'object') return false;
  const profile = value as Partial<PublicUserProfile>;
  return profile.userId === userId && typeof profile.nickName === 'string'
    && (profile.icon === null || typeof profile.icon === 'string')
    && typeof profile.followingCount === 'number' && Number.isSafeInteger(profile.followingCount) && profile.followingCount >= 0
    && typeof profile.followersCount === 'number' && Number.isSafeInteger(profile.followersCount) && profile.followersCount >= 0;
}

function validAuthorNotes(value: unknown, current: number): value is BlogPage {
  if (!value || typeof value !== 'object') return false;
  const page = value as Partial<BlogPage>;
  const count = (n: unknown) => n === null || (typeof n === 'number' && Number.isSafeInteger(n) && n >= 0);
  return typeof page.total === 'number' && Number.isSafeInteger(page.total) && page.total >= 0
    && page.current === current && typeof page.size === 'number' && Number.isInteger(page.size) && page.size >= 1 && page.size <= 50
    && Array.isArray(page.records) && page.records.length <= page.size
    && page.records.every(note => note && typeof note.id === 'string' && positiveUserId(note.id) !== null
      && typeof note.title === 'string' && (note.coverImage === null || typeof note.coverImage === 'string')
      && (note.createTime === null || typeof note.createTime === 'string') && count(note.liked) && count(note.comments))
    && new Set(page.records.map(note => note.id)).size === page.records.length;
}

function AuthorNotes({ userId, token, current, refreshKey, isSelf, onPageChange, onOpenNote, onNew }: {
  userId: string; token: string; current: number; refreshKey: number; isSelf: boolean;
  onPageChange: (page: number) => void; onOpenNote: (id: string) => void; onNew: () => void;
}) {
  const result = useResource<BlogPage>('/blog/of/user/' + userId + '?current=' + current + '&size=10', token + ':' + refreshKey);
  const page = validAuthorNotes(result.data, current) ? result.data : null;
  const pages = page ? Math.max(1, Math.ceil(page.total / page.size)) : 1;
  const outOfRange = Boolean(page && current > pages);
  useEffect(() => { if (outOfRange) onPageChange(pages); }, [outOfRange, onPageChange, pages]);
  const error = result.error || (!result.loading && !page ? '笔记列表返回格式不正确，请重新加载' : '');
  return <section className="user-home-notes" aria-label={isSelf ? '我的公开笔记' : 'TA的笔记'}>
    <div className="user-home-notes-heading"><h2>{isSelf ? '我的笔记' : 'TA 的笔记'}</h2>{page && <span>{page.total} 篇</span>}</div>
    <RequestState loading={result.loading || outOfRange} error={error} onRetry={result.reload} />
    {page && !result.loading && !error && !outOfRange && <>
      {page.total === 0 ? <div className="empty-state user-home-empty"><CameraIcon /><h2>{isSelf ? '记录你的第一个好地方' : '还没有公开笔记'}</h2><p>{isSelf ? '把值得分享的发现，留在这里。' : '下次再来，看看新的发现。'}</p>{isSelf && <button type="button" className="primary-button" onClick={onNew}>写一篇笔记</button>}</div> : <>
        {page.records.length === 0 && <div className="request-state"><p>笔记列表已变化，请刷新后查看。</p><button type="button" className="outline-button" onClick={result.reload}>重新加载</button></div>}
        <div className="user-home-notes-list">{page.records.map(note => <button type="button" className="my-note-card" data-testid="user-home-note" key={note.id} onClick={() => onOpenNote(note.id)} aria-label={'查看笔记：' + note.title}>
          <NoteThumbnail source={note.coverImage} /><span className="my-note-copy"><strong>{note.title}</strong><small>{timeLabel(note.createTime)} 发布</small><span className="my-note-stats"><span><HeartIcon aria-hidden="true" />{note.liked ?? 0}</span><span><ChatBubbleIcon aria-hidden="true" />{note.comments ?? 0}</span></span></span><ChevronRightIcon className="my-note-arrow" aria-hidden="true" />
        </button>)}</div>
        <nav className="pagination" aria-label="用户笔记分页"><button type="button" disabled={current <= 1} onClick={() => onPageChange(current - 1)}>上一页</button><span>第 {current} / {pages} 页</span><button type="button" disabled={current >= pages} onClick={() => onPageChange(current + 1)}>下一页</button></nav>
      </>}
    </>}
  </section>;
}

function UserHomeIdentity({ profile, token, viewerId, identityLoading, identityError, onIdentityRetry, onLogin, onEdit, onRelations, onConfirmed, onBusyChange }: {
  profile: PublicUserProfile; token: string; viewerId: string | null; identityLoading: boolean; identityError: string;
  onIdentityRetry: () => void; onLogin: () => void; onEdit: () => void; onRelations: (mode: FollowListMode) => void;
  onConfirmed: () => void; onBusyChange: (busy: boolean) => void;
}) {
  const isSelf = Boolean(token && viewerId === profile.userId);
  const needsIdentity = Boolean(token && (!viewerId || identityLoading || identityError));
  const enabled = Boolean(token && viewerId && !isSelf && !needsIdentity);
  const {state, busy, reload, setFollowed} = useAuthorFollow(profile.userId, token, enabled, undefined, onConfirmed);
  useEffect(() => { onBusyChange(state.phase === 'saving'); return () => onBusyChange(false); }, [state.phase, onBusyChange]);
  const label = !token ? '+ 关注' : needsIdentity ? identityLoading ? '确认身份…' : '重试登录状态'
    : state.phase === 'loading' ? '查询中…' : state.phase === 'saving' ? state.message
    : state.uncertainTarget !== null ? state.uncertainTarget ? '确认关注' : '确认取消'
    : state.data ? state.data.followed ? '已关注' : '+ 关注' : '重试查询';
  const actionLabel = !token ? '登录后关注作者' : needsIdentity ? '重试登录状态' : busy ? label
    : state.uncertainTarget !== null ? state.uncertainTarget ? '重新确认关注作者' : '重新确认取消关注'
    : state.data ? state.data.followed ? '取消关注作者' : '关注作者' : '重试查询关注状态';
  function act() {
    if (!token) { onLogin(); return; }
    if (needsIdentity) { onIdentityRetry(); return; }
    if (state.uncertainTarget !== null) { void setFollowed(state.uncertainTarget); return; }
    if (!state.data) { void reload(); return; }
    void setFollowed(!state.data.followed);
  }
  return <section className="user-home-summary" aria-label="用户公开资料">
    <div className="user-home-identity"><ProfileAvatar source={profile.icon} /><h1>{profile.nickName || '未知用户'}</h1>
      {isSelf ? <button type="button" className="outline-button user-home-edit" onClick={onEdit}>编辑资料</button> : <button type="button" className={'author-follow-button' + (state.data?.followed ? ' is-followed' : '')} data-testid="user-home-follow" aria-label={actionLabel} aria-pressed={!needsIdentity && token ? state.data?.followed : undefined} aria-busy={busy || identityLoading} disabled={busy || Boolean(token && identityLoading)} onClick={act}>{state.data?.followed && <CheckIcon aria-hidden="true" />}{label}</button>}
    </div>
    {!isSelf && token && <div className="author-follow-feedback" aria-live="polite">{needsIdentity && !identityLoading && <p className="author-follow-error" role="alert">暂时无法确认当前账号，请重试登录状态。</p>}{state.message && <p role="status">{state.message}</p>}{state.error && <p className="author-follow-error" role="alert">{state.error}</p>}{!needsIdentity && (state.error || state.uncertainTarget !== null) && <button type="button" className="text-button" disabled={busy} onClick={() => void reload()}>刷新关注状态</button>}</div>}
    <div className="user-home-stats" aria-label="用户统计">
      {isSelf ? <><button type="button" aria-label={'关注 ' + profile.followingCount + ' 人'} onClick={() => onRelations('following')}><strong data-testid="following-count">{profile.followingCount}</strong><span>关注<ChevronRightIcon aria-hidden="true" /></span></button><button type="button" aria-label={'粉丝 ' + profile.followersCount + ' 人'} onClick={() => onRelations('followers')}><strong data-testid="followers-count">{profile.followersCount}</strong><span>粉丝<ChevronRightIcon aria-hidden="true" /></span></button></> : <><div><strong data-testid="following-count">{profile.followingCount}</strong><span>关注</span></div><div><strong data-testid="followers-count">{profile.followersCount}</strong><span>粉丝</span></div></>}
    </div>
  </section>;
}

function UserHome({ userId, token, viewerId, identityLoading, identityError, current, onPageChange, onIdentityRetry, onBack, onLogin, onEdit, onRelations, onOpenNote, onNew }: {
  userId: string; token: string; viewerId: string | null; identityLoading: boolean; identityError: string; current: number;
  onPageChange: (page: number) => void; onIdentityRetry: () => void; onBack: () => void; onLogin: () => void;
  onEdit: () => void; onRelations: (mode: FollowListMode) => void; onOpenNote: (id: string) => void; onNew: () => void;
}) {
  const result = useResource<PublicUserProfile>('/user/public/' + userId, token);
  const [known, setKnown] = useState<PublicUserProfile | null>(null);
  const [notesRefresh, setNotesRefresh] = useState(0);
  const [followRefresh, setFollowRefresh] = useState(0);
  const [followBusy, setFollowBusy] = useState(false);
  const valid = validPublicProfile(result.data, userId);
  useEffect(() => { if (validPublicProfile(result.data, userId)) setKnown(result.data); }, [result.data, userId]);
  const error = result.error || (!result.loading && !valid ? '用户主页返回格式不正确，请重新加载' : '');
  const profile = !error ? valid ? result.data : known?.userId === userId ? known : null : null;
  const isSelf = Boolean(token && viewerId === userId);
  function refresh() {
    if (followBusy) return;
    result.reload();
    setNotesRefresh(v => v + 1);
    setFollowRefresh(v => v + 1);
  }
  return <>
    <header className="page-header user-home-header"><button type="button" aria-label="返回上一页" onClick={onBack} disabled={followBusy}><ArrowLeftIcon /></button><span>用户主页</span><button type="button" className="text-button" onClick={refresh} disabled={result.loading || followBusy}>刷新</button></header>
    <MobileScroll className="m1-scroll full-page user-home-scroll" key={userId + ':' + current}>
      <div className="user-home-body"><RequestState loading={result.loading && !profile} error={error} onRetry={result.reload} />
        {profile && <><UserHomeIdentity key={userId + ':' + token + ':' + followRefresh} profile={profile} token={token} viewerId={viewerId} identityLoading={identityLoading} identityError={identityError} onIdentityRetry={onIdentityRetry} onLogin={onLogin} onEdit={onEdit} onRelations={onRelations} onConfirmed={result.reload} onBusyChange={setFollowBusy} /><AuthorNotes userId={userId} token={token} current={current} refreshKey={notesRefresh} isSelf={isSelf} onPageChange={onPageChange} onOpenNote={onOpenNote} onNew={onNew} /></>}
      </div>
    </MobileScroll>
  </>;
}

function ProfileAvatar({ source, large = false, onError }: { source: string | null; large?: boolean; onError?: () => void }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => { setFailed(false); }, [source]);
  return <span className={'profile-avatar' + (large ? ' profile-edit-avatar' : '')}>
    {source && !failed ? <img src={source} alt={large ? '头像预览' : '当前头像'} onError={() => { setFailed(true); onError?.(); }} /> : <PersonIcon aria-hidden="true" />}
  </span>;
}

function validProfile(value: unknown): value is User {
  if (!value || typeof value !== 'object') return false;
  const user = value as Partial<User>;
  return Boolean(positiveUserId(user.id)) && typeof user.nickName === 'string' && (user.icon === null || typeof user.icon === 'string');
}

function matchesProfileUpdate(user: User, desired: ProfileUpdate) {
  if (user.nickName !== desired.nickName) return false;
  if (desired.avatarObjectKey === null) return true;
  if (desired.avatarObjectKey === '') return !user.icon?.trim();
  if (!user.icon) return false;
  try {
    return decodeURIComponent(new URL(user.icon, API_BASE).pathname).endsWith('/' + desired.avatarObjectKey);
  } catch { return false; }
}

function EditProfile({ token, onBack, onLogin, onSaved }: {
  token: string; onBack: () => void; onLogin: () => void; onSaved: () => void;
}) {
  const profile = useResource<User>(token ? '/user/me' : null, token);
  const user = validProfile(profile.data) ? profile.data : null;
  if (token && user && !profile.loading && !profile.error) {
    return <ProfileForm key={token + ':' + positiveUserId(user.id)} user={user} token={token} onBack={onBack} onSaved={onSaved} />;
  }
  const error = profile.error || (token && !profile.loading && !user ? '个人资料返回格式不正确，请重新加载' : '');
  return <>
    <header className="page-header profile-edit-header"><button type="button" onClick={onBack} aria-label="返回我的"><ArrowLeftIcon /></button><span>编辑资料</span><span /></header>
    <MobileScroll className="m1-scroll full-page profile-edit-scroll">
      {token ? <RequestState loading={profile.loading} error={error} onRetry={profile.reload} /> : <div className="empty-state"><PersonIcon /><h2>登录后编辑资料</h2><p>设置头像和昵称，让大家认识你。</p><button type="button" className="primary-button" onClick={onLogin}>去登录</button></div>}
    </MobileScroll>
  </>;
}

function ProfileForm({ user, token, onBack, onSaved }: { user: User; token: string; onBack: () => void; onSaved: () => void }) {
  const keyboard = useKeyboard();
  const userId = positiveUserId(user.id)!;
  const upload = usePhotoUpload(token, '/user/avatar', userId);
  const input = useRef<HTMLInputElement>(null);
  const [nickName, setNickName] = useState(user.nickName);
  const [avatarMode, setAvatarMode] = useState<'keep' | 'replace' | 'remove'>('keep');
  const [phase, setPhase] = useState<'idle' | 'saving' | 'uncertain' | 'checking'>('idle');
  const [error, setError] = useState('');
  const [discardOpen, setDiscardOpen] = useState(false);
  const request = useRef<AbortController | null>(null);
  const pending = useRef<ProfileUpdate | null>(null);
  const busy = phase === 'saving' || phase === 'checking';
  const locked = busy || phase === 'uncertain';
  const normalizedName = nickName.trim();
  const nameLength = Array.from(normalizedName).length;
  const nameError = nameLength < 1 ? '请输入昵称' : nameLength > 20 ? '昵称不能超过 20 个字符' : '';
  const dirty = nickName !== user.nickName || avatarMode !== 'keep';
  const uploading = upload.photo.phase === 'uploading';
  const ready = avatarMode !== 'replace' || (upload.photo.phase === 'success' && upload.photo.uploaded !== null);
  const avatar = avatarMode === 'remove' ? null : avatarMode === 'replace' ? upload.photo.preview || null : user.icon;

  useEffect(() => () => { request.current?.abort(); request.current = null; }, []);

  function back() {
    if (request.current) return;
    keyboard.hide();
    if (dirty || phase === 'uncertain') setDiscardOpen(true);
    else onBack();
  }

  function chooseAvatar() {
    if (locked || uploading) return;
    keyboard.hide();
    input.current?.click();
  }

  function changeAvatar(mode: 'keep' | 'remove') {
    if (locked) return;
    keyboard.hide();
    upload.clear();
    setAvatarMode(mode);
    setError('');
  }

  async function save(retry = false) {
    keyboard.hide();
    if (request.current || uploading) return;
    if (!token || localStorage.getItem(TOKEN_KEY) !== token) return;
    if (nameError) { setError(nameError); return; }
    if (!ready) { setError('请完成头像上传，或选择使用原头像'); return; }
    const desired: ProfileUpdate = retry && pending.current ? pending.current : {
      nickName: normalizedName,
      avatarObjectKey: avatarMode === 'keep' ? null : avatarMode === 'remove' ? '' : upload.photo.uploaded!.objectKey
    };
    const controller = new AbortController();
    request.current = controller;
    pending.current = desired;
    setPhase('saving');
    setError('');
    const active = () => !controller.signal.aborted && localStorage.getItem(TOKEN_KEY) === token;
    try {
      await api<void>('/user/me', { method: 'PUT', body: JSON.stringify(desired), signal: controller.signal });
      if (active()) onSaved();
    } catch (caught) {
      if (!active()) return;
      const uncertain = !(caught instanceof ApiError) || caught.status === 0 || caught.status >= 500 || caught.status === 200;
      setPhase(uncertain ? 'uncertain' : 'idle');
      if (!uncertain) pending.current = null;
      setError(uncertain ? '保存结果暂未确认，请先刷新当前资料核对。' : (caught as Error).message);
    } finally {
      if (request.current === controller) request.current = null;
    }
  }

  async function checkSaved() {
    keyboard.hide();
    if (request.current || !pending.current || localStorage.getItem(TOKEN_KEY) !== token) return;
    const desired = pending.current;
    const controller = new AbortController();
    request.current = controller;
    setPhase('checking');
    setError('');
    const active = () => !controller.signal.aborted && localStorage.getItem(TOKEN_KEY) === token;
    try {
      const fresh = await api<User>('/user/me', { signal: controller.signal });
      if (!active()) return;
      if (!validProfile(fresh) || positiveUserId(fresh.id) !== userId) throw new Error('个人资料返回格式不正确，请重新查询');
      if (matchesProfileUpdate(fresh, desired)) { onSaved(); return; }
      pending.current = null;
      setPhase('idle');
      setError('当前资料与本次修改尚不一致，请核对后重新保存。');
    } catch (caught) {
      if (!active()) return;
      setPhase('uncertain');
      setError('暂时无法确认保存结果：' + (caught as Error).message);
    } finally {
      if (request.current === controller) request.current = null;
    }
  }

  return <>
    <header className="page-header profile-edit-header">
      <button type="button" onClick={back} disabled={busy} aria-label="返回我的"><ArrowLeftIcon /></button>
      <span>编辑资料</span>
      <button type="submit" form="profile-edit-form" className="text-button profile-save" disabled={locked || uploading || !ready || !dirty || Boolean(nameError)}>{phase === 'saving' ? '保存中…' : '保存'}</button>
    </header>
    <MobileScroll className="m1-scroll full-page profile-edit-scroll">
      <form id="profile-edit-form" className="profile-edit-body" onSubmit={event => { event.preventDefault(); void save(); }}>
        <section className="profile-avatar-section" aria-label="修改头像">
          <input ref={input} className="visually-hidden" type="file" accept="image/jpeg,image/png" aria-label="选择头像文件" disabled={locked || uploading} onChange={event => {
            const file = event.currentTarget.files?.[0];
            event.currentTarget.value = '';
            if (!file || locked) return;
            setAvatarMode('replace');
            setError('');
            upload.select(file);
          }} />
          <button type="button" className="profile-avatar-picker" aria-label="选择新头像" aria-describedby="avatar-upload-feedback" onClick={chooseAvatar} disabled={locked || uploading} aria-busy={uploading}>
            <ProfileAvatar source={avatar} large onError={avatarMode === 'replace' ? upload.previewFailed : undefined} />
            <span className="profile-camera-badge"><CameraIcon aria-hidden="true" /></span>
          </button>
          <button type="button" className="text-button profile-avatar-change" onClick={chooseAvatar} disabled={locked || uploading}>{uploading ? '头像上传中…' : '更换头像'}</button>
          <div id="avatar-upload-feedback" className={'photo-upload-feedback ' + upload.photo.phase} aria-live="polite">
            {avatarMode === 'replace' && uploading && <span role="status">正在上传头像…</span>}
            {avatarMode === 'replace' && upload.photo.phase === 'success' && <><span className="photo-upload-success"><CheckIcon />新头像已上传，保存后生效</span>{upload.photo.previewUnavailable && <span>在线预览暂时无法加载，当前显示本地照片。</span>}</>}
            {avatarMode === 'replace' && upload.photo.phase === 'error' && <><span role="alert">{upload.photo.error}</span>{upload.photo.retryable && <button type="button" className="outline-button" disabled={locked} onClick={upload.retry}>重试上传头像</button>}</>}
            {avatarMode === 'remove' && <span role="status">保存后将恢复默认头像</span>}
          </div>
          <div className="profile-avatar-tools">
            {avatarMode !== 'keep' && <button type="button" className="text-button" disabled={locked} onClick={() => changeAvatar('keep')}>使用原头像</button>}
            {(Boolean(user.icon) || avatarMode === 'replace') && avatarMode !== 'remove' && <button type="button" className="text-button" disabled={locked} onClick={() => changeAvatar('remove')}>恢复默认头像</button>}
          </div>
        </section>
        <section className="profile-fields" aria-label="个人资料">
          <label htmlFor="profile-nickname">昵称</label>
          <KeyboardInput id="profile-nickname" autoComplete="nickname" value={nickName} maxLength={80} disabled={locked} aria-invalid={Boolean(nameError)} aria-describedby="profile-nickname-hint" placeholder="给自己起个昵称" onChange={event => { setNickName(event.target.value); setError(''); }} />
          <div className="profile-field-hint" id="profile-nickname-hint"><span className={nameError ? 'profile-field-invalid' : ''}>{nameError}</span><span>{nameLength} / 20</span></div>
        </section>
        {error && <p className="profile-form-error" role="alert">{error}</p>}
        {(phase === 'uncertain' || phase === 'checking') && <section className="profile-save-check" aria-label="确认资料保存结果"><strong>先确认这次保存结果</strong><p>修改可能已经生效，刷新后会核对当前资料。</p><button type="button" className="outline-button" disabled={busy} onClick={() => void checkSaved()}>{phase === 'checking' ? '正在确认…' : '刷新当前资料'}</button><button type="button" className="text-button" disabled={busy} onClick={() => void save(true)}>重新保存这份资料</button></section>}
        <p className="profile-edit-note">修改完成后，点击右上角「保存」。</p>
      </form>
    </MobileScroll>
    <BottomSheet open={discardOpen} onOpenChange={setDiscardOpen} title={phase === 'uncertain' ? '返回前确认一下' : '放弃这次修改？'} description={phase === 'uncertain' ? '保存结果还未确认，返回后请刷新个人资料核对。' : '修改还没有保存，返回后不会保留本次编辑。'} snap={0.38}>
      <div className="profile-discard-actions"><button type="button" className="coral-button" onClick={() => setDiscardOpen(false)}>继续编辑</button><button type="button" className="outline-button" onClick={() => { keyboard.hide(); setDiscardOpen(false); onBack(); }}>{phase === 'uncertain' ? '返回并刷新资料' : '放弃修改并返回'}</button></div>
    </BottomSheet>
  </>;
}

export default function Prototype() {
  const keyboard = useKeyboard();
  const [page, setPage] = useState<Page>('home');
  const currentPage = useRef<Page>(page);
  currentPage.current = page;
  const [tab, setTab] = useState<FeedTab>('推荐');
  const [feedPage, setFeedPage] = useState(1);
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [draft, setDraft] = useState({ title: '', body: '' });
  const photoInput = useRef<HTMLInputElement>(null);
  const [toast, setToast] = useState('');
  const toastTimer = useRef<number | undefined>(undefined);
  const [selectedBlogId, setSelectedBlogId] = useState<string | null>(null);
  const [storyReturn, setStoryReturn] = useState<StoryReturn>('home');
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);
  const [userNotesPage, setUserNotesPage] = useState(1);
  const [relationsReturn, setRelationsReturn] = useState<'profile' | 'userHome'>('profile');
  const [editReturn, setEditReturn] = useState<'profile' | 'userHome'>('profile');
  const journey = useRef<JourneySnapshot[]>([]);
  const [myNotesPage, setMyNotesPage] = useState(1);
  const [followingPage, setFollowingPage] = useState(1);
  const [followersPage, setFollowersPage] = useState(1);
  const [lastPublished, setLastPublished] = useState<{ id: string; title: string; token: string } | null>(null);
  const [publishPhase, setPublishPhase] = useState<PublishPhase>('idle');
  const [publishError, setPublishError] = useState('');
  const publishRequest = useRef<AbortController | null>(null);
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
  const me = useResource<User>(token ? '/user/me' : null, token);
  const notePhotos = useNotePhotos(token, positiveUserId(me.data?.id));
  const showNav = ['home', 'nearby', 'publish', 'messages', 'profile'].includes(page);
  const relationMode: FollowListMode | null = page === 'following' || page === 'followers' ? page : null;
  const secondsLeft = Math.max(0, Math.ceil((codeUntil - now) / 1000));

  const flash = useCallback((message: string) => {
    window.clearTimeout(toastTimer.current);
    setToast(message);
    toastTimer.current = window.setTimeout(() => setToast(''), 3200);
  }, []);
  function navigate(destination: Page) {
    keyboard.hide();
    if (['home', 'nearby', 'profile', 'publish', 'messages', 'search', 'shop', 'checkout'].includes(destination)) journey.current = [];
    currentPage.current = destination; setPage(destination);
  }
  function openShop(id: number) { setSelectedShopId(id); setSelectedVoucher(null); navigate('shop'); }
  function openLogin(returnTo: Page = 'profile') { setLoginReturn(returnTo); setLoginError(''); navigate('login'); }
  function openEditProfile() { setEditReturn('profile'); if (!token) { openLogin('editProfile'); return; } navigate('editProfile'); }
  function editOwnHome() { setEditReturn('userHome'); if (!token) { openLogin('userHome'); return; } navigate('editProfile'); }
  function finishEditing(saved: boolean) { me.reload(); navigate(editReturn); if (saved) flash('个人资料已保存'); }
  function captureJourney(): JourneySnapshot {
    return { page: currentPage.current, blogId: selectedBlogId, storyReturn, userId: selectedUserId, notesPage: userNotesPage, relationsReturn };
  }
  function restoreJourney() {
    const previous = journey.current.pop();
    if (!previous) { navigate('home'); return; }
    setSelectedBlogId(previous.blogId);
    setStoryReturn(previous.storyReturn);
    setSelectedUserId(previous.userId);
    setUserNotesPage(previous.notesPage);
    setRelationsReturn(previous.relationsReturn);
    navigate(previous.page);
  }
  function openUserHome(id: string) {
    const target = positiveUserId(id);
    if (!target || currentPage.current === 'userHome') return;
    journey.current.push(captureJourney());
    setSelectedUserId(target);
    setUserNotesPage(1);
    navigate('userHome');
  }
  function openMyHome() {
    if (!token) { openLogin('profile'); return; }
    const id = positiveUserId(me.data?.id);
    if (!id) { me.reload(); flash('正在获取个人资料，请稍后再试'); return; }
    openUserHome(id);
  }
  function backFromUserHome() { if (currentPage.current === 'userHome') restoreJourney(); }
  function backFromStory() {
    if (currentPage.current !== 'story') return;
    if (storyReturn === 'userHome') restoreJourney();
    else navigate(storyReturn);
  }
  function startNewNote() { if (!token) { openLogin('publish'); return; } navigate('publish'); }
  function openPublishedStory(id: string, returnTo: StoryReturn = 'home') {
    if (currentPage.current === 'story') return;
    if (returnTo === 'userHome') journey.current.push(captureJourney());
    setSelectedBlogId(id); setStoryReturn(returnTo); navigate('story');
  }
  function openMyNotes() {
    setMyNotesPage(1);
    if (!token) { openLogin('myNotes'); return; }
    navigate('myNotes');
  }
  function openFollowPeople(mode: FollowListMode, returnTo?: 'profile' | 'userHome') {
    if (returnTo) setRelationsReturn(returnTo);
    else if (!relationMode) setRelationsReturn('profile');
    if (mode === 'following') setFollowingPage(1); else setFollowersPage(1);
    if (!token) { openLogin(mode); return; }
    navigate(mode);
  }
  function choosePhoto() {
    if (publishRequest.current) return;
    keyboard.hide();
    if (!token) { openLogin('publish'); return; }
    if (!positiveUserId(me.data?.id) || me.loading || me.error) { me.reload(); flash('请先确认登录状态后添加图片'); return; }
    if (notePhotos.photos.length < MAX_NOTE_PHOTOS) photoInput.current?.click();
  }

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

  useEffect(() => {
    setPublishPhase('idle');
    setPublishError('');
    setLastPublished(null);
    setMyNotesPage(1);
    setFollowingPage(1);
    setFollowersPage(1);
    return () => {
      publishRequest.current?.abort();
      publishRequest.current = null;
    };
  }, [token]);

  async function publishStory(allowUncertainRetry = false) {
    if (publishRequest.current || (publishPhase === 'uncertain' && !allowUncertainRetry)) return;
    if (!token) { openLogin('publish'); return; }
    if (localStorage.getItem(TOKEN_KEY) !== token) return;
    const title = draft.title.trim();
    const content = draft.body.trim();
    const imageKeys = notePhotos.photos.map(photo => photo.uploaded?.objectKey);
    if (!title || title.length > 60) { setPublishError('请输入不超过 60 个字符的标题'); return; }
    if (!content || content.length > 10000) { setPublishError('请输入正文，最多 10000 个字符'); return; }
    if (!notePhotos.photos.length || notePhotos.photos.length > MAX_NOTE_PHOTOS || !notePhotos.ready) { setPublishError('请先成功上传 1～9 张图片'); return; }
    if (new Set(imageKeys).size !== imageKeys.length) { setPublishError('上传结果中有重复图片，请删除重复项后重新上传'); return; }
    keyboard.hide();
    const controller = new AbortController();
    publishRequest.current = controller;
    const active = () => !controller.signal.aborted && localStorage.getItem(TOKEN_KEY) === token;
    setPublishPhase('submitting');
    setPublishError('');
    try {
      const result = await api<string | number>('/blog', {
        method: 'POST',
        body: JSON.stringify({ title, imageObjectKeys: imageKeys, content }),
        signal: controller.signal,
      });
      if (!active()) return;
      const id = typeof result === 'string' ? result : typeof result === 'number' && Number.isSafeInteger(result) ? String(result) : '';
      if (!/^[1-9]\d*$/.test(id)) throw new Error('发布响应没有返回有效的笔记编号');
      setLastPublished({ id, title, token });
      setFeedPage(1);
      setMyNotesPage(1);
      setPublishPhase('idle');
      setDraft({ title: '', body: '' });
      notePhotos.clear();
      flash('故事发布成功');
      if (currentPage.current === 'publish') openPublishedStory(id, 'myNotes');
    } catch (error) {
      if (!active()) return;
      const uncertain = !(error instanceof ApiError) || error.status === 0 || error.status >= 500 || (error.status >= 200 && error.status < 300);
      setPublishPhase(uncertain ? 'uncertain' : 'idle');
      setPublishError(uncertain ? '发布结果暂未确认，笔记可能已经创建。再次提交可能产生重复内容，请先确认后再决定是否重试。' : (error as Error).message);
    } finally {
      if (publishRequest.current === controller) publishRequest.current = null;
    }
  }

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
    {page === 'myNotes' && <header className="page-header my-notes-header"><button onClick={() => navigate('profile')} aria-label="返回我的"><ArrowLeftIcon /></button><span>我的笔记</span><button onClick={() => navigate('publish')} aria-label="写笔记"><PlusIcon /></button></header>}
    {relationMode && <header className="page-header account-list-header"><button onClick={() => navigate(relationsReturn)} aria-label={relationsReturn === 'userHome' ? '返回用户主页' : '返回我的'}><ArrowLeftIcon /></button><span>{relationMode === 'following' ? '我的关注' : '我的粉丝'}</span><span /></header>}
    {page === 'editProfile' ? <EditProfile key={token} token={token} onBack={() => finishEditing(false)} onLogin={() => openLogin('editProfile')} onSaved={() => finishEditing(true)} /> : page === 'userHome' && selectedUserId ? <UserHome key={selectedUserId} userId={selectedUserId} token={token} viewerId={positiveUserId(me.data?.id)} identityLoading={me.loading} identityError={me.error} current={userNotesPage} onPageChange={setUserNotesPage} onIdentityRetry={me.reload} onBack={backFromUserHome} onLogin={() => openLogin('userHome')} onEdit={editOwnHome} onRelations={mode => openFollowPeople(mode, 'userHome')} onOpenNote={id => openPublishedStory(id, 'userHome')} onNew={startNewNote} /> : page === 'story' && selectedBlogId ? <PublishedStory key={selectedBlogId} blogId={selectedBlogId} token={token} viewerId={positiveUserId(me.data?.id)} identityLoading={me.loading} identityError={me.error} onIdentityRetry={me.reload} onBack={backFromStory} onLogin={() => openLogin('story')} onNew={startNewNote} onOpenUser={openUserHome} /> : <MobileScroll className={`m1-scroll${showNav ? '' : ' full-page'}${page === 'myNotes' ? ' my-notes-scroll' : ''}${relationMode ? ' account-list-scroll' : ''}`} key={page === 'home' ? `home:${feedPage}` : relationMode ? `${relationMode}:${relationMode === 'following' ? followingPage : followersPage}` : page}>
      {page === 'home' && <>
        <header className="home-header"><div className="brand-lockup"><span className="brand-mark">巷里</span><span className="brand-sub">发现身边值得一去的小店</span></div><button className="icon-button header-search" aria-label="搜索店铺" onClick={() => navigate('search')}><MagnifyingGlassIcon /><span>搜附近的小店</span></button></header>
        <div className="feed-tabs" role="tablist" aria-label="内容分类">{(['推荐', '附近', '关注'] as FeedTab[]).map(item => <button key={item} role="tab" aria-selected={tab === item} className={tab === item ? 'active' : ''} onClick={() => { if (item === '附近') navigate('nearby'); else setTab(item); }}>{item}</button>)}</div>
        <button className="live-entry" onClick={() => navigate('nearby')}><MapPinIcon /><span><strong>去逛逛附近的小店</strong><small>发现附近小店和优惠券</small></span><ChevronRightIcon /></button>
        {lastPublished && lastPublished.token === token && <button className="live-entry recent-story-entry" onClick={() => openPublishedStory(lastPublished.id)}><CheckIcon /><span><strong>{lastPublished.title}</strong><small>刚刚发布 · 查看你的故事</small></span><ChevronRightIcon /></button>}
        {tab === '关注' ? <div className="empty-state"><span className="empty-symbol">✳</span><h2>期待下一次相遇</h2><p>关注功能正在准备中，先看看大家的最新笔记。</p><button className="primary-button" onClick={() => setTab('推荐')}>看看推荐</button></div> : <BlogFeed current={feedPage} refreshKey={lastPublished?.id || ''} onPageChange={setFeedPage} onOpenNote={id => openPublishedStory(id)} onNew={startNewNote} onOpenUser={openUserHome} />}
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
        <div className="checkout-body"><span className="eyebrow">A SMALL TREAT FOR TODAY</span><h1>{selectedVoucher.title}<span className="accent-dot">.</span></h1><div className="voucher-summary"><span>{voucherState(selectedVoucher, now)}</span><strong>¥{money(selectedVoucher.payValue)}</strong><small>可抵扣 ¥{money(selectedVoucher.actualValue)}</small></div><div className="checkout-info"><span>所属店铺 <b>{shop.data?.name || `店铺 ${selectedVoucher.shopId}`}</b></span>{selectedVoucher.type === 1 && <><span>开始时间 <b>{timeLabel(selectedVoucher.beginTime)}</b></span><span>结束时间 <b>{timeLabel(selectedVoucher.endTime)}</b></span><span>参考剩余库存 <b>{selectedVoucher.stock ?? '待更新'}</b></span></>}</div><h2 className="rules-heading">使用规则</h2><p className="voucher-rules">{selectedVoucher.rules?.replace(/\\n/g, '\n') || '暂无补充说明'}</p><SeckillPurchase key={`${token}:${me.data?.id || 'loading'}:${selectedVoucher.id}`} voucher={selectedVoucher} token={token} userId={me.data ? String(me.data.id) : null} now={now} identityLoading={me.loading} identityError={me.error} onLogin={() => openLogin('checkout')} onIdentityRetry={me.reload} />{!token && <button className="login-link" onClick={() => openLogin('checkout')}>先登录，发现更多好去处 <ChevronRightIcon /></button>}</div>
      </>}

      {page === 'profile' && <>
        <header className="profile-header"><span className="eyebrow">YOUR LITTLE CORNER</span><h1>我的巷里<span className="accent-dot">.</span></h1><button type="button" className="profile-identity profile-identity-button" onClick={openEditProfile} aria-label={token ? '编辑个人资料' : '登录后编辑个人资料'}><ProfileAvatar source={me.data?.icon || null} /><span className="profile-identity-copy"><strong>{me.data?.nickName || (token ? me.error ? '暂时无法加载个人信息' : '正在确认登录状态' : '欢迎来巷里走走')}</strong><small>{me.data ? '账号 ID：' + me.data.id : token ? me.error ? '请重试下方请求' : '连接你的巷里账号' : '登录后，开启你的巷里生活'}</small></span><span className="profile-edit-link">{token ? <><Pencil1Icon aria-hidden="true" />编辑资料</> : '登录'}<ChevronRightIcon aria-hidden="true" /></span></button></header>
        {token && <RequestState loading={me.loading} error={me.error} onRetry={me.reload} />}
        <div className="profile-menu"><button onClick={openMyHome} disabled={Boolean(token && me.loading)}><HomeIcon /> 我的主页 <ChevronRightIcon /></button><button onClick={openMyNotes}><CameraIcon /> 我的笔记 <ChevronRightIcon /></button><button onClick={() => openFollowPeople('following')}><PersonIcon /> 我的关注 <ChevronRightIcon /></button><button onClick={() => openFollowPeople('followers')}><HeartIcon /> 我的粉丝 <ChevronRightIcon /></button><button onClick={() => navigate('nearby')}><MapPinIcon /> 发现店铺 <ChevronRightIcon /></button><button onClick={() => { navigate('nearby'); flash('进入优惠券详情，可查看当前账号的对应订单'); }}><StarFilledIcon /> 优惠与订单 <ChevronRightIcon /></button><button onClick={() => flash('巷里，发现身边值得一去的小店')}><HeartIcon /> 关于巷里 <ChevronRightIcon /></button>{token ? <button onClick={logout} disabled={loggingOut}><PersonIcon />{loggingOut ? '正在退出…' : '退出登录'}<ChevronRightIcon /></button> : <button onClick={() => openLogin()}><PersonIcon /> 手机号登录 <ChevronRightIcon /></button>}</div>
      </>}

      {page === 'myNotes' && <MyNotes token={token} current={myNotesPage} refreshKey={lastPublished?.id || ''} onPageChange={setMyNotesPage} onLogin={() => openLogin('myNotes')} onNew={() => navigate('publish')} onOpenNote={id => openPublishedStory(id, 'myNotes')} />}

      {relationMode && <FollowPeople mode={relationMode} token={token} current={relationMode === 'following' ? followingPage : followersPage} viewerId={positiveUserId(me.data?.id)} identityLoading={me.loading} identityError={me.error} onIdentityRetry={me.reload} onPageChange={relationMode === 'following' ? setFollowingPage : setFollowersPage} onModeChange={openFollowPeople} onOpenUser={openUserHome} onLogin={() => openLogin(relationMode)} onDiscover={() => { setTab('推荐'); navigate('home'); }} />}

      {page === 'login' && <>
        <header className="page-header"><button aria-label="返回" onClick={() => { loginAttempt.current += 1; setLoggingIn(false); navigate(loginReturn); }}><ArrowLeftIcon /></button><span>登录巷里</span><span /></header>
        <div className="login-body"><span className="eyebrow">GOOD TO SEE YOU</span><h1>好久不见<span className="accent-dot">.</span></h1><p>用手机号，找回你的巷里生活。</p><form onSubmit={event => { event.preventDefault(); void login(); }}><label htmlFor="login-phone">手机号</label><KeyboardInput id="login-phone" type="tel" inputMode="numeric" autoComplete="tel" value={phone} maxLength={11} disabled={loggingIn || sendingCode} onChange={event => setPhone(event.target.value.replace(/\D/g, ''))} placeholder="请输入 11 位手机号" /><label htmlFor="login-code">验证码</label><div className="verification-row"><KeyboardInput id="login-code" inputMode="numeric" autoComplete="one-time-code" value={code} maxLength={6} disabled={loggingIn} onChange={event => setCode(event.target.value.replace(/\D/g, ''))} placeholder="六位验证码" /><button type="button" onClick={() => void sendCode()} disabled={sendingCode || secondsLeft > 0 || loggingIn}>{sendingCode ? '请求中…' : secondsLeft > 0 ? `${secondsLeft}s 后重试` : '获取验证码'}</button></div>{loginError && <p className="form-error" role="alert">{loginError}</p>}<button className="coral-button" type="submit" disabled={loggingIn}>{loggingIn ? '正在登录…' : '登录 / 注册'}</button></form><p className="login-note">首次登录将自动创建账号。本地调试的验证码请查看用户服务控制台，当前未接入真实短信。</p></div>
      </>}


      {page === 'publish' && <>
        <header className="sub-header"><div><span className="eyebrow">SHARE YOUR MOMENT</span><h1>记下一个好地方<span className="accent-dot">.</span></h1></div></header>
        <div className="publish-body">
          <p className="preview-caption">选几张照片，慢慢写下你的发现。</p>
          <input ref={photoInput} className="visually-hidden" type="file" accept="image/jpeg,image/png" multiple aria-label="选择故事照片" disabled={publishPhase === 'submitting'} onChange={event => {
            const files = Array.from(event.currentTarget.files || []);
            event.currentTarget.value = '';
            if (!files.length || publishRequest.current) return;
            if (!token) { openLogin('publish'); return; }
            notePhotos.add(files);
          }} />
          {token && (me.loading || me.error) && <RequestState loading={me.loading} error={me.error} onRetry={me.reload} />}
          <NotePhotoGrid upload={notePhotos} token={token} disabled={publishPhase === 'submitting'} onChoose={choosePhoto} />
          <label htmlFor="note-title">一句话标题</label><KeyboardInput id="note-title" onBlur={event => { if (!(event.relatedTarget instanceof HTMLInputElement || event.relatedTarget instanceof HTMLTextAreaElement)) keyboard.hide(); }} value={draft.title} disabled={publishPhase === 'submitting'} onChange={event => { setDraft({ ...draft, title: event.target.value }); if (publishPhase !== 'uncertain') setPublishError(''); }} maxLength={60} placeholder="比如：转角遇到的那家小店" />
          <label htmlFor="note-content">说说你的故事</label><KeyboardTextarea id="note-content" onBlur={event => { if (!(event.relatedTarget instanceof HTMLInputElement || event.relatedTarget instanceof HTMLTextAreaElement)) keyboard.hide(); }} value={draft.body} disabled={publishPhase === 'submitting'} maxLength={10000} onChange={event => { setDraft({ ...draft, body: event.target.value }); if (publishPhase !== 'uncertain') setPublishError(''); }} placeholder="今天发生了什么好事？" rows={5} />
          <small className="draft-counter">{draft.body.length} / 10000</small>
          <p className="checkout-note">发布后可以查看完整故事。发布前，草稿仅在当前页面暂存，刷新后不会保留。</p>
          {publishError && <p className="form-error publish-error" role="alert">{publishError}</p>}
          <button className="coral-button" type="button" disabled={publishPhase === 'submitting' || notePhotos.busy || Boolean(token && notePhotos.photos.length && !notePhotos.ready)} onClick={() => void publishStory(publishPhase === 'uncertain')}>{publishPhase === 'submitting' ? '正在发布…' : notePhotos.busy ? '图片上传中…' : !token ? '登录并发布' : publishPhase === 'uncertain' ? '仍要再次发布' : '发布故事'}</button>
        </div>
      </>}

      {page === 'messages' && <><header className="sub-header"><div><span className="eyebrow">STAY IN TOUCH</span><h1>消息<span className="accent-dot">.</span></h1></div></header><div className="empty-state"><ChatBubbleIcon /><h2>期待新的问候</h2><p>消息功能正在准备中。</p><button className="primary-button" onClick={() => navigate('nearby')}>先去逛逛小店</button></div></>}
    </MobileScroll>}
    {showNav && <nav className="bottom-nav" aria-label="主导航"><button className={page === 'home' ? 'active' : ''} onClick={() => navigate('home')}><HomeIcon /><span>首页</span></button><button className={page === 'nearby' ? 'active' : ''} onClick={() => navigate('nearby')}><MapPinIcon /><span>附近</span></button><button className="nav-create" onClick={() => navigate('publish')} aria-label="记录故事"><PlusIcon /></button><button className={page === 'messages' ? 'active' : ''} onClick={() => navigate('messages')}><ChatBubbleIcon /><span>消息</span></button><button className={page === 'profile' ? 'active' : ''} onClick={() => navigate('profile')}><PersonIcon /><span>我的</span></button></nav>}
    {toast && <div className="toast" role="status">{toast}</div>}
  </div>;
}
