#!/usr/bin/env node
// 墨阅小说网前端 · 联调自检脚本
//
// 模拟前端完整行为链：登录 → 解码 JWT 取 userId → 携带 X-User-Id 调受保护端点 → 断言 R<T> 契约。
// 目的：在后端基础设施（MySQL/Redis/ES）不可用时，仍能验证前端与 /api/v1 的接口契约、
// 参数名、身份头与 R<T> 解包逻辑是否一致。
//
// 用法：
//   1) npm run mock            （另开终端，默认 :8081）
//   2) node tools/verify-contract.mjs [baseUrl]
import http from 'node:http';

const BASE = process.argv[2] ?? 'http://localhost:8081/api/v1';

let pass = 0;
let fail = 0;

function check(name, cond, extra = '') {
  if (cond) {
    pass += 1;
    console.log(`  ✅ ${name}`);
  } else {
    fail += 1;
    console.log(`  ❌ ${name} ${extra}`);
  }
}

/** 极简 HTTP JSON 客户端（对齐前端 axios：baseURL + 身份头） */
function req(method, path, { body, headers = {} } = {}) {
  return new Promise((resolve, reject) => {
    const url = new URL(BASE + path);
    const data = body == null ? null : Buffer.from(JSON.stringify(body));
    const r = http.request(
      {
        hostname: url.hostname,
        port: url.port,
        path: url.pathname + url.search,
        method,
        headers: {
          ...(data ? { 'Content-Type': 'application/json', 'Content-Length': data.length } : {}),
          ...headers,
        },
      },
      (res) => {
        const chunks = [];
        res.on('data', (c) => chunks.push(c));
        res.on('end', () => {
          try {
            resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')));
          } catch (e) {
            reject(new Error('响应非 JSON：' + Buffer.concat(chunks).toString('utf8').slice(0, 120)));
          }
        });
      }
    );
    r.on('error', reject);
    if (data) r.write(data);
    r.end();
  });
}

/** 复刻前端 auth.ts 的 decodeJwt：解析 JWT payload 取 userId / role */
function decodeJwt(token) {
  const parts = token.split('.');
  if (parts.length < 2) throw new Error('非法 token（非三段 JWT）：' + token);
  const b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
  const bin = Buffer.from(b64, 'base64').toString('utf8');
  const json = JSON.parse(bin);
  return { userId: Number(json.userId), role: json.role == null ? null : Number(json.role) };
}

async function main() {
  console.log(`\n=== 墨阅前端联调自检 · base=${BASE} ===\n`);

  // ---- 1. 登录与 JWT 解析 ----
  console.log('[1] 登录与身份解析');
  const bad = await req('POST', '/auth/login', { body: { phone: '13800000000', password: 'wrong' } });
  check('错误密码返回业务码 10001（非 HTTP 异常）', bad.code === 10001, `实际 code=${bad.code}`);

  const login = await req('POST', '/auth/login', { body: { phone: '13800000000', password: 'moon' } });
  check('正确密码 code=0', login.code === 0, JSON.stringify(login));
  const token = login.data?.accessToken;
  check('返回 accessToken', typeof token === 'string' && token.length > 0);

  let userId;
  let role;
  try {
    const claims = decodeJwt(token);
    userId = claims.userId;
    role = claims.role;
    check('前端可解码 JWT 取到 userId', Number.isFinite(userId), `userId=${userId}`);
    check('前端可解码 JWT 取到 role', Number.isFinite(role), `role=${role}`);
  } catch (e) {
    check('前端可解码 JWT', false, e.message);
    console.log('\n结果：' + pass + ' 通过 / ' + fail + ' 失败');
    process.exit(1);
  }
  const auth = { 'X-User-Id': String(userId), 'X-User-Role': String(role ?? '') };

  // ---- 2. 书城 / 详情 / 章节 ----
  console.log('\n[2] 书城 / 详情 / 章节');
  const books = await req('GET', '/books?page=1&size=5');
  check('GET /books code=0', books.code === 0);
  check('书籍分页返回 records', Array.isArray(books.data?.records) && books.data.records.length > 0);
  const firstBook = books.data.records[0];

  const detail = await req('GET', `/books/${firstBook.bookId}`);
  check('GET /books/{id} 返回书名', detail.code === 0 && typeof detail.data?.title === 'string');

  const chapters = await req('GET', `/chapters?bookId=${firstBook.bookId}&page=1&size=20`);
  check('GET /chapters 返回目录', chapters.code === 0 && chapters.data.records.length > 0);
  check('目录口径不含正文 content', chapters.data.records.every((c) => c.content === undefined));
  const firstChapter = chapters.data.records[0];

  const chapter = await req('GET', `/chapters/${firstChapter.id}`);
  check('单章接口填充 content', chapter.code === 0 && typeof chapter.data?.content === 'string' && chapter.data.content.length > 0);

  // ---- 3. 检索与纠错 ----
  console.log('\n[3] 检索与纠错');
  const miss = await req('GET', '/search/corrected?keyword=zzzzzz&size=5');
  check('GET /search/corrected code=0', miss.code === 0);
  check('无命中时给出 correctedKeyword 纠错建议', typeof miss.data?.correctedKeyword === 'string');

  const hit = await req('GET', '/search/corrected?keyword=沧澜&size=5');
  check('命中检索返回 records', hit.code === 0 && Array.isArray(hit.data?.records));

  const rec = await req('GET', '/search/recommend?limit=3&sort=hot');
  check('推荐位返回数组', rec.code === 0 && Array.isArray(rec.data));

  // ---- 4. 鉴权：受保护端点必须带 X-User-Id ----
  console.log('\n[4] 鉴权（X-User-Id）');
  const noAuth = await req('GET', `/read/bookshelf/${userId}`);
  check('未带 X-User-Id → 10002 未登录', noAuth.code === 10002, `实际 code=${noAuth.code}`);

  const emptyShelf = await req('GET', `/read/bookshelf/${userId}`, { headers: auth });
  check('带 X-User-Id → code=0', emptyShelf.code === 0, JSON.stringify(emptyShelf));

  // ---- 5. 书架写链路 ----
  console.log('\n[5] 书架');
  const add = await req('POST', '/read/bookshelf', { body: { bookId: firstBook.bookId }, headers: auth });
  check('POST /read/bookshelf 加入书架 code=0', add.code === 0, JSON.stringify(add));

  const shelf = await req('GET', `/read/bookshelf/${userId}`, { headers: auth });
  check('书架含刚加入的书籍', (shelf.data ?? []).some((it) => it.bookId === firstBook.bookId));

  const prog = await req('PUT', `/read/bookshelf/${firstBook.bookId}/progress`, {
    body: { chapterId: firstChapter.id },
    headers: auth,
  });
  check('PUT 阅读进度上报 code=0', prog.code === 0);

  const afterProg = await req('GET', `/read/bookshelf/${userId}`, { headers: auth });
  check('进度已回写 lastChapterId', (afterProg.data ?? []).some((it) => it.lastChapterId === firstChapter.id));

  const del = await req('DELETE', `/read/bookshelf/${firstBook.bookId}`, { headers: auth });
  check('DELETE 移出书架 code=0', del.code === 0);

  // ---- 6. 评论 ----
  console.log('\n[6] 评论');
  const noAuthComment = await req('POST', '/comments', { body: { bookId: firstBook.bookId, content: 'x' } });
  check('未登录发表评论 → 10002', noAuthComment.code === 10002);

  const addComment = await req('POST', '/comments', {
    body: { bookId: firstBook.bookId, content: '联调测试评论' },
    headers: auth,
  });
  check('登录发表评论 code=0', addComment.code === 0, JSON.stringify(addComment));

  const list = await req('GET', `/comments?bookId=${firstBook.bookId}&page=1&size=20`);
  check('评论列表含刚发内容', (list.data?.records ?? []).some((c) => c.content === '联调测试评论'));

  const cid = addComment.data?.id;
  if (cid) {
    const like = await req('POST', `/comments/${cid}/like`, { headers: auth });
    check('点赞返回 likeCount 数字', like.code === 0 && typeof like.data === 'number');
  }

  // ---- 7. AI 客服 ----
  console.log('\n[7] AI 客服');
  const chat = await req('POST', '/ai/chat', { body: { userId, content: '这本书讲什么' } });
  check('POST /ai/chat code=0', chat.code === 0, JSON.stringify(chat));
  check('助手回复 role=2', chat.data?.role === 2);
  const sid = chat.data?.sessionId;
  check('首轮返回 sessionId', Number.isFinite(sid));

  const sessions = await req('GET', `/ai/sessions?userId=${userId}&page=1&size=20`);
  check('会话列表含新会话', (sessions.data?.records ?? []).some((s) => s.id === sid));

  const msgs = await req('GET', `/ai/sessions/${sid}/messages?userId=${userId}`);
  check('会话消息含用户提问与助手回复', (msgs.data ?? []).length >= 2);

  // ---- 8. 个人中心 / 积分 ----
  console.log('\n[8] 个人中心 / 积分');
  const me = await req('GET', '/users/me', { headers: auth });
  check('GET /users/me 返回资料 id', me.code === 0 && me.data?.id === userId, JSON.stringify(me.data));

  const acc = await req('GET', `/points/accounts/${userId}`, { headers: auth });
  check('GET /points/accounts 返回余额', acc.code === 0 && typeof acc.data?.balance === 'number', JSON.stringify(acc.data));
  const balanceBefore = acc.data?.balance ?? 0;

  const sign1 = await req('POST', '/points/check-in', { body: { userId }, headers: auth });
  check('POST /points/check-in 返回本次获得积分', sign1.code === 0 && typeof sign1.data === 'number' && sign1.data > 0, JSON.stringify(sign1));
  const sign2 = await req('POST', '/points/check-in', { body: { userId }, headers: auth });
  check('重复签到 → 10001 今日已签到', sign2.code === 10001, `实际 code=${sign2.code}`);

  const flows = await req('GET', `/points/flows?userId=${userId}&page=1&size=20`, { headers: auth });
  check('GET /points/flows 含签到流水', flows.code === 0 && (flows.data?.records ?? []).some((f) => f.bizType === 1));

  const products = await req('GET', '/points/products?page=1&size=50', { headers: auth });
  check('GET /points/products 返回上架商品', products.code === 0 && (products.data?.records ?? []).length > 0);

  const order = await req('POST', '/points/orders', { body: { userId, productId: 1 }, headers: auth });
  check('POST /points/orders 兑换返回订单', order.code === 0 && typeof order.data?.productName === 'string', JSON.stringify(order.data));
  const accAfter = await req('GET', `/points/accounts/${userId}`, { headers: auth });
  check('兑换后余额已扣减', accAfter.code === 0 && (accAfter.data?.balance ?? 0) === balanceBefore + (sign1.data ?? 0) - (order.data?.costPoints ?? 0), `before=${balanceBefore} after=${accAfter.data?.balance}`);

  const orders = await req('GET', `/points/orders?userId=${userId}&page=1&size=20`, { headers: auth });
  check('GET /points/orders 含刚兑换订单', orders.code === 0 && (orders.data?.records ?? []).some((o) => o.id === order.data?.id));

  // ---- 9. 作者工作台（建书 / 写章 / 发布） ----
  console.log('\n[9] 作者工作台');
  const mine = await req('GET', `/books/mine?page=1&size=50`, { headers: auth });
  check('GET /books/mine 返回我的作品', mine.code === 0 && Array.isArray(mine.data?.records), JSON.stringify(mine.data));
  const seedBook = mine.data?.records?.[0];
  const bookId = seedBook?.bookId ?? firstBook.bookId; // 用预置或书城首本兜底

  const newBook = await req('POST', '/books', { body: { title: '联调测试作', categoryId: 1, intro: 'verify' }, headers: auth });
  check('POST /books 创建作品 code=0', newBook.code === 0 && typeof newBook.data?.bookId === 'number', JSON.stringify(newBook.data));
  const createdBookId = newBook.data?.bookId ?? bookId;

  const newChapter = await req('POST', '/chapters', { body: { bookId: createdBookId, title: '联调测试章', content: '正文内容', chapterNo: 99, status: 0 }, headers: auth });
  check('POST /chapters 写章 code=0', newChapter.code === 0 && typeof newChapter.data?.id === 'number', JSON.stringify(newChapter.data));
  const chapterId = newChapter.data?.id;

  const drafts = await req('GET', `/chapters/drafts?bookId=${createdBookId}&page=1&size=20`, { headers: auth });
  check('GET /chapters/drafts 含草稿', drafts.code === 0 && (drafts.data?.records ?? []).some((c) => c.id === chapterId), JSON.stringify(drafts.data));

  const pub = await req('POST', `/chapters/${chapterId}/publish`, { body: {}, headers: auth });
  check('POST /chapters/{id}/publish 立即发布 status=2', pub.code === 0 && pub.data?.status === 2, JSON.stringify(pub.data));

  const chapterList = await req('GET', `/chapters?bookId=${createdBookId}&page=1&size=100`, { headers: auth });
  check('发布后目录含该章（status=2）', chapterList.code === 0 && (chapterList.data?.records ?? []).some((c) => c.id === chapterId && c.status === 2));

  console.log(`\n=== 结果：${pass} 通过 / ${fail} 失败 ===\n`);
  process.exit(fail === 0 ? 0 : 1);
}

main().catch((e) => {
  console.error('自检脚本异常：', e.message);
  process.exit(1);
});
