#!/usr/bin/env python3
"""墨阅小说网 · M6 content 管理端深化端到端实跑（经网关 8080）。

覆盖：作品上架 status=1；章节发布（status=1）；书架全局查询返回数据。
"""
import json, sys, urllib.request, urllib.error, urllib.parse, uuid

GW = "http://127.0.0.1:8080"
PASS, FAIL = [], []

def req(method, path, body=None, token=None, params=None):
    url = GW + path
    if params:
        qs = "&".join(f"{k}={urllib.parse.quote(str(v))}" for k, v in params.items() if v is not None)
        url += ("&" if "?" in url else "?") + qs
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

def data_of(res):
    return res[1].get("data")

def find(records, _id):
    return next((x for x in (records or []) if x.get("id") == _id), None)

print("=" * 78)
print("M6 content 管理端深化 端到端（gateway 8080 → moyue-content）")
print("=" * 78)

suffix = uuid.uuid4().hex[:6]

print("\n[1] 运营登录")
st, r = req("POST", "/api/v1/system/login", {"username": "admin", "password": "admin123"})
admin_token = (r.get("data") or {}).get("accessToken")
check("运营登录签发 token", bool(admin_token))

print("\n[2] 新建作品并上架")
st, r = req("POST", "/api/v1/admin/content/books",
            {"title": "运营端测试作品" + suffix, "authorName": "测试作者"}, token=admin_token)
check("建作品 code=0", code_of((st, r)) == 0, r.get("message"))
book_id = r.get("data")
check("返回作品 ID", isinstance(book_id, int) and book_id > 0, book_id)

st, r = req("POST", f"/api/v1/admin/content/books/{book_id}/online", token=admin_token)
check("上架 code=0", code_of((st, r)) == 0, r.get("message"))

st, r = req("GET", f"/api/v1/books/{book_id}", token=admin_token)
check("消费者侧作品 status=1（已上架）", (data_of((st, r)) or {}).get("status") == 1, (data_of((st, r)) or {}).get("status"))

print("\n[3] 新建章节并发布")
st, r = req("POST", "/api/v1/chapters",
            {"bookId": book_id, "title": "第一章" + suffix, "content": "这是正文内容"}, token=admin_token)
check("建章节 code=0", code_of((st, r)) == 0, r.get("message"))
chapter_id = r.get("data")

st, r = req("POST", f"/api/v1/admin/content/chapters/{chapter_id}/publish", token=admin_token)
check("发布章节 code=0", code_of((st, r)) == 0, r.get("message"))
check("章节 status=1（已发布）", (data_of((st, r)) or {}).get("status") == 1, (data_of((st, r)) or {}).get("status"))

print("\n[4] 书架全局查询")
st, r = req("GET", "/api/v1/admin/content/bookshelf", token=admin_token, params={"page": 1, "size": 10})
check("书架查询 code=0", code_of((st, r)) == 0, r.get("message"))
check("书架返回分页结构", isinstance((data_of((st, r)) or {}).get("records"), list))

print("\n" + "=" * 78)
print(f"M6 content E2E 结果：通过 {len(PASS)} / 失败 {len(FAIL)}")
if FAIL:
    print("失败项：" + ", ".join(FAIL))
    sys.exit(1)
print("全部通过 ✅")
