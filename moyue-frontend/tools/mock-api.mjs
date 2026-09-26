#!/usr/bin/env node
// 墨阅小说网前端 · dev-only 契约 mock 服务
//
// 用途：本机无 MySQL / Redis / Elasticsearch（项目禁用 Docker）时，让前端能真跑、真点，
// 并据此联调验证：baseURL、R<T> 解包、请求参数名、X-User-Id 身份头是否携带正确。
// 语义严格对齐后端：HTTP 统一 200，业务结果由 code 表达（0 成功 / 10001 参数 / 10002 未登录 / 20001 不存在）。
//
// 启动：npm run mock（默认 :8081）   前端联调：VITE_API_TARGET=http://localhost:8081 npm run dev
import http from 'node:http';

const PORT = Number(process.env.MOCK_PORT ?? 8081);

const CODE = { SUCCESS: 0, PARAM_ERROR: 10001, UNAUTHORIZED: 10002, NOT_FOUND: 20001 };

const ok = (data) => ({ code: CODE.SUCCESS, message: '操作成功', data: data ?? null, traceId: 'mock' + Date.now() });
const fail = (code, message) => ({ code, message, data: null, traceId: 'mock' + Date.now() });

// ------------------------------ 内存数据 ------------------------------
const BOOKS = [
  { bookId: 1, authorId: 101, title: '剑起沧澜', author: '墨十三', wordCount: 128000, category: '玄幻', categoryId: 1, status: 1, coverUrl: '', intro: '少年自沧澜而出，一剑惊天下。', clickCount: 1203 },
  { bookId: 2, authorId: 102, title: '都市医仙', author: '青衫故人', wordCount: 88000, category: '都市', categoryId: 2, status: 1, coverUrl: '', intro: '一手银针，济世也济己。', clickCount: 980 },
  { bookId: 3, authorId: 103, title: '长夜难明', author: '沈雾', wordCount: 210000, category: '悬疑', categoryId: 3, status: 2, coverUrl: '', intro: '真相在长夜尽头。', clickCount: 4321 },
  { bookId: 4, authorId: 104, title: '星海拾遗', author: '远舟', wordCount: 56000, category: '玄幻', categoryId: 1, status: 1, coverUrl: '', intro: '拾星者行于海。', clickCount: 233 },
  { bookId: 5, authorId: 105, title: '旧巷烟雨', author: '林晚', wordCount: 99000, category: '都市', categoryId: 2, status: 1, coverUrl: '', intro: '烟雨旧巷，故人未远。', clickCount: 517 },
];

const CHAPTERS = {};
for (const b of BOOKS) {
  CHAPTERS[b.bookId] = Array.from({ length: 6 }, (_, i) => ({
    id: b.bookId * 100 + i + 1,
    bookId: b.bookId,
    chapterNo: i + 1,
    title: ['初遇', '试锋', '夜话', '惊变', '抉择', '归途'][i],
    wordCount: 3000 + i * 120,
    status: 2,
    content: `（mock 正文）${b.title} 第${i + 1}章。\n\n这里是章节正文占位内容，用于联调验证阅读器渲染与上下章导航。\n风起于青萍之末，故事自此处展开……`,
  }));
}

const CATEGORIES = [
  { id: 1, name: '玄幻', sort: 1 },
  { id: 2, name: '都市', sort: 2 },
  { id: 3, name: '悬疑', sort: 3 },
];

let commentSeq = 1;
const COMMENTS = []; // { id, userId, bookId, content, status, likeCount, createTime }
const SHELF = new Map(); // userId -> Map(bookId -> { lastChapterId })
const SESSIONS = []; // { id, userId, title, createTime, updateTime }
const MESSAGES = []; // { id, sessionId, role, content, createTime }
let sessionSeq = 1;
let msgSeq = 1;

// ---- 积分域（points） ----
const ACCOUNTS = new Map(); // userId -> { userId, balance, totalEarned, totalSpent }
const PRODUCTS = [
  { id: 1, name: '月卡会员', description: '30 天全场畅读', imageUrl: '', costPoints: 500, stock: 99, status: 1 },
  { id: 2, name: '限量书签', description: '墨阅定制金属书签', imageUrl: '', costPoints: 200, stock: 50, status: 1 },
  { id: 3, name: '实体周边', description: '帆布包 + 明信片', imageUrl: '', costPoints: 800, stock: 20, status: 1 },
];
let orderSeq = 1;
const ORDERS = []; // { id, userId, productId, productName, costPoints, status, createTime }
let flowSeq = 1;
const FLOWS = []; // { id, userId, bizType, points, remark, createTime }
const LAST_CHECKIN = new Map(); // userId -> 'YYYY-MM-DD'

function getOrCreateAccount(uid) {
  let acc = ACCOUNTS.get(uid);
  if (!acc) {
    acc = { userId: uid, balance: 1280, totalEarned: 1280, totalSpent: 0 };
    ACCOUNTS.set(uid, acc);
    FLOWS.push({ id: flowSeq++, userId: uid, bizType: 4, points: 1280, remark: '账户初始化赠送', createTime: new Date().toISOString() });
  }
  return acc;
}

// ---- 作者域（book / chapter 写作链路） ----
let bookSeq = 1000;
let chapterSeq = 100000;
// 预置两本 demo 作品（作者 = 10001，登录 demo 用户），让作者工作台首屏非空
const MY_BOOKS = [
  { bookId: bookSeq++, authorId: 10001, title: '霜河夜行', author: '墨阅君', wordCount: 42000, category: '玄幻', categoryId: 1, status: 1, coverUrl: '', intro: '霜河之上，夜行人未眠。', clickCount: 311 },
  { bookId: bookSeq++, authorId: 10001, title: '人间烟火', author: '墨阅君', wordCount: 18000, category: '都市', categoryId: 2, status: 1, coverUrl: '', intro: '市井之中见众生。', clickCount: 87 },
];
// 预置草稿：第二章为草稿（status=0），第一章已发布（status=2）
CHAPTERS[MY_BOOKS[0].bookId] = [
  { id: chapterSeq++, bookId: MY_BOOKS[0].bookId, chapterNo: 1, title: '霜河初雪', wordCount: 3200, status: 2, content: '（mock 正文）霜河初雪……' },
  { id: chapterSeq++, bookId: MY_BOOKS[0].bookId, chapterNo: 2, title: '夜泊', wordCount: 0, status: 0, content: '' },
];

const catName = (id) => CATEGORIES.find((c) => c.id === Number(id))?.name ?? '其他';

const toDoc = (b) => ({
  bookId: b.bookId, title: b.title, authorName: b.author, categoryName: b.category,
  categoryId: b.categoryId, coverUrl: b.coverUrl, description: b.intro, status: b.status,
  clickCount: b.clickCount, favoriteCount: 0, hotScore: b.clickCount / 10, updateTime: '2026-09-25T20:00:00',
});

// ------------------------------ JWT（供前端解码 userId/role） ------------------------------
function b64url(obj) {
  return Buffer.from(JSON.stringify(obj)).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
function issueToken(userId, role) {
  const header = b64url({ alg: 'HS256', typ: 'JWT' });
  const payload = b64url({ sub: String(userId), userId, role, type: 'access', iat: Date.now() });
  return `${header}.${payload}.mock-signature`;
}

// ------------------------------ 路由 ------------------------------
function requireUser(req) {
  const uid = req.headers['x-user-id'];
  if (!uid) return null;
  return Number(Array.isArray(uid) ? uid[0] : uid);
}

function route(req, res, url) {
  const { pathname, searchParams } = url;
  const method = req.method.toUpperCase();
  const p = pathname.replace(/^\/api\/v1/, '').replace(/\/$/, '');
  const q = (k, d) => searchParams.get(k) ?? d;

  // ---- 鉴权 ----
  if (p === '/auth/login' && method === 'POST') {
    const body = req.body ?? {};
    if (!body.phone || !body.password) return fail(CODE.PARAM_ERROR, '手机号和密码不能为空');
    // mock：密码 moon 通过，其余报参数错误，便于前端验证失败分支
    if (body.password !== 'moon') return fail(CODE.PARAM_ERROR, '手机号或密码错误');
    return ok({ accessToken: issueToken(10001, 1), refreshToken: issueToken(10001, 1) });
  }
  if (p === '/users/me') {
    const uid = requireUser(req);
    if (!uid) return fail(CODE.UNAUTHORIZED, '未登录');
    // 对齐后端 UserInfoVO：id / phone / nickname / role / status
    return ok({ id: uid, nickname: '墨阅君', phone: '13800000000', role: 1, status: 1 });
  }

  // ---- 积分账户 ----
  if (p.match(/^\/points\/accounts\/\d+$/) && method === 'GET') {
    const uid = requireUser(req);
    if (!uid) return fail(CODE.UNAUTHORIZED, '未登录');
    return ok(getOrCreateAccount(uid));
  }
  if (p === '/points/products' && method === 'GET') {
    const list = PRODUCTS.filter((x) => x.status === 1);
    return ok({ total: list.length, page: 1, size: Number(q('size', 20)), records: list });
  }
  if (p === '/points/orders' && method === 'POST') {
    const uid = requireUser(req);
    if (!uid) return fail(CODE.UNAUTHORIZED, '未登录');
    const productId = Number(req.body?.productId);
    const prod = PRODUCTS.find((x) => x.id === productId);
    if (!prod) return fail(CODE.NOT_FOUND, '商品不存在');
    const acc = getOrCreateAccount(uid);
    if (acc.balance < prod.costPoints) return fail(CODE.PARAM_ERROR, '积分余额不足');
    acc.balance -= prod.costPoints;
    acc.totalSpent += prod.costPoints;
    const order = { id: orderSeq++, userId: uid, productId, productName: prod.name, costPoints: prod.costPoints, status: 1, createTime: new Date().toISOString() };
    ORDERS.push(order);
    FLOWS.push({ id: flowSeq++, userId: uid, bizType: 5, points: -prod.costPoints, remark: `兑换《${prod.name}》`, createTime: new Date().toISOString() });
    return ok(order);
  }
  if (p === '/points/orders' && method === 'GET') {
    const uid = Number(q('userId'));
    if (!uid) return fail(CODE.PARAM_ERROR, '缺少 userId');
    const list = ORDERS.filter((o) => o.userId === uid).sort((a, b) => b.id - a.id);
    return ok({ total: list.length, page: 1, size: Number(q('size', 20)), records: list });
  }
  if (p === '/points/check-in' && method === 'POST') {
    const uid = requireUser(req);
    if (!uid) return fail(CODE.UNAUTHORIZED, '未登录');
    const today = new Date().toISOString().slice(0, 10);
    if (LAST_CHECKIN.get(uid) === today) return fail(CODE.PARAM_ERROR, '今日已签到');
    LAST_CHECKIN.set(uid, today);
    const earned = 10;
    const acc = getOrCreateAccount(uid);
    acc.balance += earned;
    acc.totalEarned += earned;
    FLOWS.push({ id: flowSeq++, userId: uid, bizType: 1, points: earned, remark: '每日签到', createTime: new Date().toISOString() });
    return ok(earned);
  }
  if (p === '/points/flows' && method === 'GET') {
    const uid = Number(q('userId'));
    if (!uid) return fail(CODE.PARAM_ERROR, '缺少 userId');
    const list = FLOWS.filter((f) => f.userId === uid).sort((a, b) => b.id - a.id);
    return ok({ total: list.length, page: 1, size: Number(q('size', 20)), records: list });
  }

  // ---- 书城 ----
  if (p === '/books' && method === 'GET') {
    const categoryId = q('categoryId');
    const list = categoryId ? BOOKS.filter((b) => String(b.categoryId) === String(categoryId)) : BOOKS;
    return ok({ total: list.length, page: 1, size: Number(q('size', 20)), records: list });
  }
  if (p === '/books/mine' && method === 'GET') {
    const uid = requireUser(req);
    if (!uid) return fail(CODE.UNAUTHORIZED, '未登录');
    const list = MY_BOOKS.filter((b) => b.authorId === uid);
    return ok({ total: list.length, page: 1, size: 20, records: list });
  }
  if (p === '/books' && method === 'POST') {
    const uid = requireUser(req);
    if (!uid) return fail(CODE.UNAUTHORIZED, '未登录');
    const title = String(req.body?.title ?? '').trim();
    if (!title) return fail(CODE.PARAM_ERROR, '书名不能为空');
    const book = {
      bookId: bookSeq++,
      authorId: uid,
      title,
      author: '墨阅君',
      wordCount: 0,
      category: catName(req.body?.categoryId),
      categoryId: Number(req.body?.categoryId) || null,
      status: 1,
      coverUrl: req.body?.coverUrl ?? '',
      intro: req.body?.intro ?? '',
      clickCount: 0,
    };
    MY_BOOKS.push(book);
    CHAPTERS[book.bookId] = [];
    return ok(book);
  }
  const bookMatch = p.match(/^\/books\/(\d+)$/);
  if (bookMatch && method === 'GET') {
    const b = BOOKS.find((x) => x.bookId === Number(bookMatch[1]));
    return b ? ok(b) : fail(CODE.NOT_FOUND, '书籍不存在');
  }

  // ---- 章节 ----
  if (p === '/chapters' && method === 'GET') {
    const bookId = Number(q('bookId'));
    const list = CHAPTERS[bookId] ?? [];
    // 列表口径不含正文（对齐后端 toDtoLite）
    return ok({ total: list.length, page: 1, size: Number(q('size', 20)), records: list.map(({ content, ...rest }) => rest) });
  }
  const chapMatch = p.match(/^\/chapters\/(\d+)$/);
  if (chapMatch && method === 'GET') {
    const id = Number(chapMatch[1]);
    for (const list of Object.values(CHAPTERS)) {
      const c = list.find((x) => x.id === id);
      if (c) return ok(c);
    }
    return fail(CODE.NOT_FOUND, '章节不存在');
  }
  if (p === '/chapters' && method === 'POST') {
    const uid = requireUser(req);
    if (!uid) return fail(CODE.UNAUTHORIZED, '未登录');
    const bookId = Number(req.body?.bookId);
    if (!bookId) return fail(CODE.PARAM_ERROR, '缺少 bookId');
    if (!CHAPTERS[bookId]) CHAPTERS[bookId] = [];
    const title = String(req.body?.title ?? '').trim() || `第${(req.body?.chapterNo ?? CHAPTERS[bookId].length + 1)}章`;
    const content = String(req.body?.content ?? '');
    const status = Number(req.body?.status ?? 0);
    const chapter = {
      id: chapterSeq++,
      bookId,
      chapterNo: Number(req.body?.chapterNo ?? CHAPTERS[bookId].length + 1),
      title,
      wordCount: content.length,
      status,
      content,
    };
    CHAPTERS[bookId].push(chapter);
    const book = MY_BOOKS.find((b) => b.bookId === bookId);
    if (book) book.wordCount += content.length;
    return ok(chapter);
  }
  if (p === '/chapters/drafts' && method === 'GET') {
    const bookId = Number(q('bookId'));
    const list = (CHAPTERS[bookId] ?? []).filter((c) => c.status === 0);
    return ok({ total: list.length, page: 1, size: Number(q('size', 20)), records: list.map(({ content, ...rest }) => rest) });
  }
  const pubMatch = p.match(/^\/chapters\/(\d+)\/publish$/);
  if (pubMatch && method === 'POST') {
    const uid = requireUser(req);
    if (!uid) return fail(CODE.UNAUTHORIZED, '未登录');
    const id = Number(pubMatch[1]);
    let found;
    for (const list of Object.values(CHAPTERS)) {
      const c = list.find((x) => x.id === id);
      if (c) { found = c; break; }
    }
    if (!found) return fail(CODE.NOT_FOUND, '章节不存在');
    const publishTime = req.body?.publishTime;
    // 定时（未来时间）则置审核中(1)，否则立即发布(2)并落正文
    if (publishTime && new Date(publishTime).getTime() > Date.now()) {
      found.status = 1;
    } else {
      found.status = 2;
    }
    found.publishTime = publishTime ?? null;
    return ok(found);
  }

  // ---- 分类 ----
  if (p === '/categories' && method === 'GET') return ok(CATEGORIES);

  // ---- 检索 ----
  if (p === '/search/books' && method === 'GET') {
    const kw = String(q('keyword', '')).trim();
    const docs = kw ? BOOKS.filter((b) => (b.title + b.author + b.category + b.intro).includes(kw)).map(toDoc) : BOOKS.map(toDoc);
    return ok({ total: docs.length, page: 1, size: Number(q('size', 20)), records: docs });
  }
  if (p === '/search/corrected' && method === 'GET') {
    const kw = String(q('keyword', '')).trim();
    let docs = BOOKS.filter((b) => (b.title + b.author + b.category + b.intro).includes(kw)).map(toDoc);
    let corrected;
    if (docs.length === 0) {
      // 模拟 P1-5 纠错：换一个相近词二次召回
      corrected = '剑';
      docs = BOOKS.filter((b) => b.title.includes(corrected)).map(toDoc);
    }
    return ok({ records: docs, total: docs.length, page: 1, size: Number(q('size', 20)), correctedKeyword: corrected });
  }
  if (p === '/search/recommend' && method === 'GET') {
    const limit = Number(q('limit', 10));
    return ok(BOOKS.map(toDoc).sort((a, b) => b.clickCount - a.clickCount).slice(0, limit));
  }

  // ---- 书架（受保护） ----
  const shelfMatch = p.match(/^\/read\/bookshelf\/(\d+)$/);
  if (p === '/read/bookshelf' && method === 'POST') {
    const uid = requireUser(req);
    if (!uid) return fail(CODE.UNAUTHORIZED, '未登录');
    if (!SHELF.has(uid)) SHELF.set(uid, new Map());
    SHELF.get(uid).set(Number(req.body?.bookId), { lastChapterId: null });
    return ok();
  }
  if (shelfMatch && method === 'DELETE') {
    const uid = requireUser(req);
    if (!uid) return fail(CODE.UNAUTHORIZED, '未登录');
    SHELF.get(uid)?.delete(Number(shelfMatch[1]));
    return ok();
  }
  const progMatch = p.match(/^\/read\/bookshelf\/(\d+)\/progress$/);
  if (progMatch && method === 'PUT') {
    const uid = requireUser(req);
    if (!uid) return fail(CODE.UNAUTHORIZED, '未登录');
    if (!SHELF.has(uid)) SHELF.set(uid, new Map());
    SHELF.get(uid).set(Number(progMatch[1]), { lastChapterId: Number(req.body?.chapterId ?? null) });
    return ok();
  }
  if (shelfMatch && method === 'GET') {
    const uid = requireUser(req);
    if (!uid) return fail(CODE.UNAUTHORIZED, '未登录');
    const map = SHELF.get(uid) ?? new Map();
    return ok([...map.entries()].map(([bookId, v]) => ({ id: bookId, userId: uid, bookId, lastChapterId: v.lastChapterId })));
  }

  // ---- 评论 ----
  if (p === '/comments' && method === 'GET') {
    const bookId = Number(q('bookId'));
    const list = COMMENTS.filter((c) => c.bookId === bookId);
    return ok({ total: list.length, page: 1, size: Number(q('size', 20)), records: list });
  }
  if (p === '/comments' && method === 'POST') {
    const uid = requireUser(req);
    if (!uid) return fail(CODE.UNAUTHORIZED, '未登录');
    const content = String(req.body?.content ?? '').trim();
    if (!content) return fail(CODE.PARAM_ERROR, '评论内容不能为空');
    const c = { id: commentSeq++, userId: uid, bookId: Number(req.body?.bookId), content, status: 1, likeCount: 0, createTime: new Date().toISOString() };
    COMMENTS.push(c);
    return ok(c);
  }
  const likeMatch = p.match(/^\/comments\/(\d+)\/like$/);
  if (likeMatch && method === 'POST') {
    const uid = requireUser(req);
    if (!uid) return fail(CODE.UNAUTHORIZED, '未登录');
    const c = COMMENTS.find((x) => x.id === Number(likeMatch[1]));
    if (!c) return fail(CODE.NOT_FOUND, '评论不存在');
    c.likeCount += 1;
    return ok(c.likeCount);
  }
  const delComment = p.match(/^\/comments\/(\d+)$/);
  if (delComment && method === 'DELETE') {
    const uid = requireUser(req);
    if (!uid) return fail(CODE.UNAUTHORIZED, '未登录');
    const i = COMMENTS.findIndex((x) => x.id === Number(delComment[1]));
    if (i >= 0 && COMMENTS[i].userId === uid) COMMENTS.splice(i, 1);
    return ok();
  }

  // ---- AI 客服 ----
  if (p === '/ai/chat' && method === 'POST') {
    const uid = Number(req.body?.userId);
    if (!uid) return fail(CODE.UNAUTHORIZED, '未登录');
    let sid = req.body?.sessionId;
    if (!sid) {
      const s = { id: sessionSeq++, userId: uid, title: String(req.body?.content ?? '新会话').slice(0, 12), createTime: new Date().toISOString(), updateTime: new Date().toISOString() };
      SESSIONS.push(s);
      sid = s.id;
    }
    MESSAGES.push({ id: msgSeq++, sessionId: sid, role: 1, content: req.body?.content, createTime: new Date().toISOString() });
    const reply = { id: msgSeq++, sessionId: sid, role: 2, content: `（mock 助手）你问的是：${req.body?.content}`, createTime: new Date().toISOString() };
    MESSAGES.push(reply);
    return ok(reply);
  }
  if (p === '/ai/sessions' && method === 'GET') {
    const uid = Number(q('userId'));
    const list = SESSIONS.filter((s) => s.userId === uid);
    return ok({ total: list.length, page: 1, size: Number(q('size', 20)), records: list });
  }
  const sessMsg = p.match(/^\/ai\/sessions\/(\d+)\/messages$/);
  if (sessMsg && method === 'GET') {
    return ok(MESSAGES.filter((m) => m.sessionId === Number(sessMsg[1])));
  }
  const sessDel = p.match(/^\/ai\/sessions\/(\d+)$/);
  if (sessDel && method === 'DELETE') {
    const i = SESSIONS.findIndex((s) => s.id === Number(sessDel[1]));
    if (i >= 0) SESSIONS.splice(i, 1);
    return ok();
  }

  return fail(CODE.NOT_FOUND, `mock 未实现：${method} ${p}`);
}

// ------------------------------ 服务 ------------------------------
const server = http.createServer((req, res) => {
  const chunks = [];
  req.on('data', (c) => chunks.push(c));
  req.on('end', () => {
    const raw = Buffer.concat(chunks).toString('utf8');
    try {
      req.body = raw ? JSON.parse(raw) : {};
    } catch {
      req.body = {};
    }
    const url = new URL(req.url, `http://localhost:${PORT}`);
    let out;
    try {
      out = route(req, res, url);
    } catch (e) {
      out = fail(CODE.PARAM_ERROR, 'mock 处理异常：' + e.message);
    }
    const body = JSON.stringify(out);
    res.writeHead(200, {
      'Content-Type': 'application/json;charset=UTF-8',
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': '*',
      'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,OPTIONS',
    });
    res.end(body);
    console.log(`${req.method} ${req.url} -> code=${out.code}`);
  });
});

server.listen(PORT, () => {
  console.log(`[mock] 墨阅契约 mock 已启动：http://localhost:${PORT}/api/v1`);
  console.log('[mock] 登录：任意手机号 + 密码 moon（返回含 userId=10001 的 JWT）');
});
