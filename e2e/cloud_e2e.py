#!/usr/bin/env python3
"""墨阅小说网 Cloud 端到端实跑脚本（经网关 8080）。"""
import json, sys, urllib.request, urllib.error, uuid

GW = "http://127.0.0.1:8080"
PASS, FAIL = [], []

def req(method, path, body=None, token=None, raw=False):
    url = GW + path
    data = json.dumps(body).encode() if body is not None else None
    h = {"Content-Type": "application/json"}
    if token:
        h["Authorization"] = "Bearer " + token
    r = urllib.request.Request(url, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        txt = e.read().decode()
        try:
            return e.code, json.loads(txt)
        except Exception:
            return e.code, {"raw": txt[:200]}

def check(name, cond, detail=""):
    (PASS if cond else FAIL).append(name)
    print(("  [OK]   " if cond else "  [FAIL] ") + name + ((" — " + str(detail)) if detail else ""))

def code_of(res):
    return res[1].get("code")

print("=" * 78)
print("墨阅小说网 Cloud E2E（gateway 8080 → Nacos → auth/system/content/social）")
print("=" * 78)

# ---------------------------------------------------------------- 1. 运营登录
print("\n[1] 运营登录（gateway → moyue-auth）")
st, r = req("POST", "/api/v1/system/login", {"username": "admin", "password": "admin123"})
check("登录 200", st == 200, st)
admin_token = (r.get("data") or {}).get("accessToken")
check("签发 accessToken", bool(admin_token))
check("userType=3（运营）", (r.get("data") or {}).get("userType") == 3, (r.get("data") or {}).get("userType"))

# ---------------------------------------------------------------- 2. C 端注册
print("\n[2] C 端读者注册（gateway → moyue-auth）")
suffix = uuid.uuid4().hex[:6]
ru, rp, rphone = "reader" + suffix, "reader123456", "139" + str(abs(hash(suffix)) % 10**8).zfill(8)
st, r = req("POST", "/api/v1/system/register", {"username": ru, "password": rp, "phone": rphone, "userType": 1})
check("注册成功（code=0）", code_of((st, r)) == 0, r.get("message") or r)
reader_id = r.get("data")
check("返回读者 userId", isinstance(reader_id, int) and reader_id > 0, reader_id)

st, r = req("POST", "/api/v1/system/login", {"username": ru, "password": rp})
check("读者登录成功", code_of((st, r)) == 0, r.get("message"))
reader_token = (r.get("data") or {}).get("accessToken")
check("读者 userType=1", (r.get("data") or {}).get("userType") == 1, (r.get("data") or {}).get("userType"))

# ---------------------------------------------------------------- 3. 管理端（Feign/路由 + 权限）
print("\n[3] 管理端接口（gateway → moyue-system / content / social）")
for name, path in [("system 用户列表", "/api/v1/admin/system/users?pageNum=1&pageSize=5"),
                   ("content 作品列表", "/api/v1/admin/content/books?pageNum=1&pageSize=5"),
                   ("social 帖子列表", "/api/v1/admin/social/posts?pageNum=1&pageSize=5")]:
    st, r = req("GET", path, token=admin_token)
    check(name + " 路由可达", st == 200 and code_of((st, r)) == 0, f"http={st} code={code_of((st, r))}")

print("\n[3b] 鉴权边界")
st, r = req("GET", "/api/v1/admin/system/users?pageNum=1&pageSize=5")
check("未带令牌访问后台 → 401", st == 401, st)
st, r = req("GET", "/api/v1/admin/system/users?pageNum=1&pageSize=5", token=reader_token)
check("读者访问后台 → 403", st == 403, st)
st, r = req("GET", "/api/v1/admin/system/users?pageNum=1&pageSize=5", token="bad.token.here")
check("非法令牌 → 401", st == 401, st)

# ---------------------------------------------------------------- 4. 内容 C 端
print("\n[4] 内容 C 端（gateway → moyue-content）")
# 先通过管理端真实创建一部已发布作品，保证 C 端有数据
st, r = req("POST", "/api/v1/admin/content/books",
            {"title": "Cloud E2E 作品 " + suffix, "authorName": "E2E 作者",
             "status": 1, "categoryId": 1, "intro": "端到端测试作品", "wordCount": 0},
            token=admin_token)
check("管理端创建已发布作品", st == 200 and code_of((st, r)) == 0, r.get("message") or r)
book_id = r.get("data")

st, r = req("GET", "/api/v1/books?pageNum=1&pageSize=5", token=reader_token)
check("C 端书城列表可达", st == 200 and code_of((st, r)) == 0, f"http={st} code={code_of((st, r))}")
books = ((r.get("data") or {}).get("records") or []) if isinstance(r.get("data"), dict) else (r.get("data") or {}).get("list", [])
if books:
    book_id = books[0].get("id") or books[0].get("bookId")
check("C 端书城返回数据", book_id is not None, f"bookId={book_id}")

if book_id:
    st, r = req("GET", f"/api/v1/books/{book_id}", token=reader_token)
    check("C 端书籍详情可达", st == 200 and code_of((st, r)) == 0, f"http={st} code={code_of((st, r))}")

    # 书架幂等
    st, r = req("POST", "/api/v1/read/bookshelf", {"bookId": book_id}, token=reader_token)
    check("加入书架（第 1 次）", code_of((st, r)) == 0, r.get("message") or r)
    st, r = req("POST", "/api/v1/read/bookshelf", {"bookId": book_id}, token=reader_token)
    check("加入书架（第 2 次，幂等）", code_of((st, r)) == 0, r.get("message") or r)
    st, r = req("GET", "/api/v1/read/bookshelf?pageNum=1&pageSize=10", token=reader_token)
    recs = ((r.get("data") or {}).get("records") or []) if isinstance(r.get("data"), dict) else []
    same = [x for x in recs if (x.get("bookId") or x.get("id")) == book_id]
    check("书架列表仅 1 条（幂等生效）", len(same) == 1, f"命中 {len(same)} 条 / 共 {len(recs)} 条")
    check("书架列表可达", st == 200 and code_of((st, r)) == 0, f"http={st}")

    # 阅读进度 upsert
    st, r = req("PUT", f"/api/v1/read/bookshelf/{book_id}/progress", {"chapterNo": 1, "position": 10}, token=reader_token)
    check("上报进度 chapterNo=1", code_of((st, r)) == 0, r.get("message") or r)
    st, r = req("PUT", f"/api/v1/read/bookshelf/{book_id}/progress", {"chapterNo": 3, "position": 88}, token=reader_token)
    check("上报进度 chapterNo=3（覆盖）", code_of((st, r)) == 0, r.get("message") or r)
    st, r = req("GET", f"/api/v1/read/bookshelf/{book_id}/progress", token=reader_token)
    d = r.get("data") or {}
    check("进度读取为最新值 3/88", d.get("chapterNo") == 3 and d.get("position") == 88, d)

# ---------------------------------------------------------------- 5. 评论与点赞
print("\n[5] 评论与点赞（gateway → moyue-social）")
st, r = req("POST", "/api/v1/comments", {"bookId": book_id, "content": "Cloud E2E 评论 " + suffix}, token=reader_token)
check("发表评论", code_of((st, r)) == 0, r.get("message") or r)
comment_id = r.get("data")
check("返回 commentId", isinstance(comment_id, int) and comment_id > 0, comment_id)

if comment_id:
    seq = []
    for i in range(5):
        st, r = req("POST", f"/api/v1/comments/{comment_id}/like", token=reader_token)
        seq.append(r.get("data"))
    check("点赞开关 5 次序列 1,0,1,0,1", seq == [1, 0, 1, 0, 1], seq)
    st, r = req("GET", f"/api/v1/comments?bookId={book_id}&pageNum=1&pageSize=10", token=reader_token)
    recs = ((r.get("data") or {}).get("records") or []) if isinstance(r.get("data"), dict) else []
    hit = [x for x in recs if x.get("id") == comment_id]
    check("评论列表可查", st == 200 and code_of((st, r)) == 0, f"http={st}")
    check("点赞计数与开关一致（1）", (hit[0].get("likeCount") == 1) if hit else False, (hit[0].get("likeCount") if hit else "未命中"))
    st, r = req("DELETE", f"/api/v1/comments/{comment_id}", token=reader_token)
    check("删除自己的评论", code_of((st, r)) == 0, r.get("message") or r)

# ---------------------------------------------------------------- 6. IM
print("\n[6] IM 会话与消息（gateway → moyue-social）")
st, r = req("POST", "/api/v1/im/conversations", {"type": 1, "memberIds": [1], "title": "Cloud E2E"}, token=reader_token)
check("创建会话", code_of((st, r)) == 0, r.get("message") or r)
conv_id = r.get("data")
check("返回 conversationId", isinstance(conv_id, int) and conv_id > 0, conv_id)

if conv_id:
    st, r = req("GET", "/api/v1/im/conversations", token=reader_token)
    check("会话列表可达", st == 200 and code_of((st, r)) == 0, f"http={st}")
    msg_ids = []
    for i in range(3):
        st, r = req("POST", f"/api/v1/im/conversations/{conv_id}/messages", {"content": f"cloud-e2e-{i}", "type": 1}, token=reader_token)
        if code_of((st, r)) == 0 and isinstance(r.get("data"), dict):
            msg_ids.append((r["data"]).get("id"))
    check("连续发送 3 条消息", len(msg_ids) == 3, msg_ids)
    st, r = req("GET", f"/api/v1/im/conversations/{conv_id}/messages?limit=20", token=reader_token)
    msgs = r.get("data") or []
    check("消息游标分页可查", st == 200 and len(msgs) >= 3, f"条数={len(msgs)}")
    if msg_ids:
        st, r = req("POST", f"/api/v1/im/conversations/{conv_id}/messages/{msg_ids[-1]}/recall", token=reader_token)
        check("撤回消息", code_of((st, r)) == 0, r.get("message") or r)
        st, r = req("GET", f"/api/v1/im/conversations/{conv_id}/messages?limit=20", token=reader_token)
        recalled = [m for m in (r.get("data") or []) if m.get("id") == msg_ids[-1]]
        check("撤回后状态 status=2", recalled and recalled[0].get("status") == 2, (recalled[0].get("status") if recalled else "未命中"))

# ---------------------------------------------------------------- 7. 未登录 C 端
print("\n[7] C 端未登录访问")
st, r = req("GET", "/api/v1/read/bookshelf?pageNum=1&pageSize=5")
check("未登录访问书架 → 401", st == 401, st)

# ---------------------------------------------------------------- 8. 参数校验（评论缺 bookId 应 400 而非 500）
print("\n[8] 参数校验边界")
st, r = req("POST", "/api/v1/comments", {"content": "无 bookId 的评论"}, token=reader_token)
check("评论缺 bookId → 业务参数错误（非 500）", st == 200 and code_of((st, r)) != 0 and "bookId" in (r.get("message") or ""),
      f"http={st} msg={r.get('message')}")

print("\n" + "=" * 78)
print(f"结果：PASS {len(PASS)} / FAIL {len(FAIL)}")
if FAIL:
    print("失败项：" + "; ".join(FAIL))
print("=" * 78)
sys.exit(1 if FAIL else 0)
