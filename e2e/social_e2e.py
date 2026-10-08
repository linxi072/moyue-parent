#!/usr/bin/env python3
"""墨阅小说网 · M7 social 管理端深化端到端实跑（经网关 8080）。

覆盖：评论列表 → 删除（逻辑删除）→ 列表查不到；会话列表 → 查看消息 → 禁用会话。
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
print("M7 social 管理端深化 端到端（gateway 8080 → moyue-social）")
print("=" * 78)

suffix = uuid.uuid4().hex[:6]

print("\n[1] 运营登录")
st, r = req("POST", "/api/v1/system/login", {"username": "admin", "password": "admin123"})
admin_token = (r.get("data") or {}).get("accessToken")
check("运营登录签发 token", bool(admin_token))

print("\n[2] 建测试作品（评论夹具）")
st, r = req("POST", "/api/v1/admin/content/books",
            {"title": "社交测试作品" + suffix, "authorName": "测试作者"}, token=admin_token)
book_id = r.get("data")
check("建作品 code=0", code_of((st, r)) == 0, r.get("message"))

print("\n[3] 发评论 → 管理端列表 → 删除 → 查不到")
st, r = req("POST", "/api/v1/comments",
            {"bookId": book_id, "content": "运营端测试评论" + suffix}, token=admin_token)
check("发评论 code=0", code_of((st, r)) == 0, r.get("message"))
comment_id = r.get("data")

st, r = req("GET", "/api/v1/admin/social/comments", token=admin_token, params={"page": 1, "size": 20})
rec = find((data_of((st, r)) or {}).get("records"), comment_id)
check("评论出现在管理端列表", rec is not None, comment_id)

st, r = req("DELETE", f"/api/v1/admin/social/comments/{comment_id}", token=admin_token)
check("删除评论 code=0", code_of((st, r)) == 0, r.get("message"))

st, r = req("GET", "/api/v1/admin/social/comments", token=admin_token, params={"page": 1, "size": 20})
rec = find((data_of((st, r)) or {}).get("records"), comment_id)
check("删除后管理端列表查不到（逻辑删除）", rec is None)

print("\n[4] 建会话 → 管理端列表 → 查看消息 → 禁用")
st, r = req("POST", "/api/v1/im/conversations",
            {"type": 1, "memberIds": [1, 2], "title": "测试会话" + suffix}, token=admin_token)
check("建会话 code=0", code_of((st, r)) == 0, r.get("message"))
conv_id = r.get("data")

st, r = req("POST", f"/api/v1/im/conversations/{conv_id}/messages",
            {"content": "会话内第一条消息" + suffix}, token=admin_token)
check("会话内发消息 code=0", code_of((st, r)) == 0, r.get("message"))

st, r = req("GET", "/api/v1/admin/social/im/conversations", token=admin_token, params={"page": 1, "size": 20})
conv = find((data_of((st, r)) or {}).get("records"), conv_id)
check("会话出现在管理端列表", conv is not None, conv_id)

st, r = req("GET", f"/api/v1/admin/social/im/conversations/{conv_id}/messages",
            token=admin_token, params={"size": 20})
check("运营查看消息 code=0", code_of((st, r)) == 0, r.get("message"))
check("消息列表含刚发送内容", any((m.get("content") or "").find(suffix) >= 0 for m in (data_of((st, r)) or [])))

st, r = req("POST", f"/api/v1/admin/social/im/conversations/{conv_id}/disable",
            params={"disabled": 1}, token=admin_token)
check("禁用会话 code=0", code_of((st, r)) == 0, r.get("message"))

st, r = req("GET", "/api/v1/admin/social/im/conversations", token=admin_token, params={"page": 1, "size": 20})
conv = find((data_of((st, r)) or {}).get("records"), conv_id)
check("会话状态变为已禁用 disabled=1", conv is not None and conv.get("disabled") == 1, conv.get("disabled") if conv else None)

print("\n" + "=" * 78)
print(f"M7 social E2E 结果：通过 {len(PASS)} / 失败 {len(FAIL)}")
if FAIL:
    print("失败项：" + ", ".join(FAIL))
    sys.exit(1)
print("全部通过 ✅")
