#!/usr/bin/env python3
"""墨阅小说网 · M3 message 模块端到端实跑（经网关 8080）。

覆盖：单发站内信（未读）/ 批量群发 3 人（各 1 条未读）/ 模板渲染 ${name} / 全部已读（read_flag 全 1）。
"""
import json, sys, urllib.request, urllib.error, urllib.parse, uuid

GW = "http://127.0.0.1:8080"
PASS, FAIL = [], []

def req(method, path, body=None, token=None, params=None):
    url = GW + path
    if params:
        parts = []
        for k, v in params.items():
            if v is None:
                continue
            if isinstance(v, list):
                for x in v:
                    parts.append(f"{k}={urllib.parse.quote(str(x))}")
            else:
                parts.append(f"{k}={urllib.parse.quote(str(v))}")
        if parts:
            url += ("&" if "?" in url else "?") + "&".join(parts)
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

suffix = uuid.uuid4().hex[:6]
print("=" * 78)
print("M3 message 端到端（gateway 8080 → moyue-message）")
print("=" * 78)

st, r = req("POST", "/api/v1/system/login", {"username": "admin", "password": "admin123"})
admin_token = (r.get("data") or {}).get("accessToken")
check("运营登录签发 token", bool(admin_token))

# 注册 3 个读者用于群发 + 1 个独立读者用于单发
readers = []
for i in range(3):
    ru = "msgreader" + suffix + str(i)
    st, r = req("POST", "/api/v1/system/register",
                {"username": ru, "password": "reader123456", "phone": "138" + str(abs(hash(ru)) % 10**8).zfill(8), "userType": 1})
    readers.append(r.get("data"))
ru = "msgsingle" + suffix
st, r = req("POST", "/api/v1/system/register",
            {"username": ru, "password": "reader123456", "phone": "137" + str(abs(hash(ru)) % 10**8).zfill(8), "userType": 1})
single_reader = r.get("data")
check("注册 3 个群发读者 + 1 个单发读者", len(readers) == 3 and all(isinstance(x, int) for x in readers) and isinstance(single_reader, int), readers + [single_reader])

# ---------------------------------------------------------------- 模板（可选渲染）
print("\n[1] 消息模板创建 + 渲染")
st, r = req("POST", "/api/v1/admin/message/templates",
            {"code": "WELCOME" + suffix, "title": "欢迎", "content": "你好 ${name}，欢迎来到墨阅", "type": 1, "enabled": 1},
            token=admin_token)
check("创建模板 code=0", code_of((st, r)) == 0, r.get("message"))
tmpl_id = r.get("data")

# ---------------------------------------------------------------- 单发
print("\n[2] 单发站内信（默认未读，发往独立读者）")
st, r = req("POST", "/api/v1/admin/message/messages/send",
            params={"toUser": single_reader, "title": "单发测试" + suffix, "content": "hello", "type": 1, "templateCode": "WELCOME" + suffix, "name": "小明"},
            token=admin_token)
check("单发返回消息 ID", code_of((st, r)) == 0 and isinstance(r.get("data"), int), r.get("data"))
single_id = r.get("data")

# 校验内容被模板渲染（${name} → 小明）
st, r = req("GET", "/api/v1/admin/message/messages", params={"page": 1, "size": 20, "toUser": single_reader}, token=admin_token)
msgs = (data_of((st, r)) or {}).get("records") or []
m = next((x for x in msgs if x.get("id") == single_id), None)
check("单发内容经模板渲染（含『小明』）", m is not None and "小明" in (m.get("content") or ""), m.get("content") if m else None)
check("单发默认未读 readFlag=0", m is not None and m.get("readFlag") == 0, m.get("readFlag") if m else None)

# ---------------------------------------------------------------- 批量群发
print("\n[3] 批量群发 3 人（各 1 条未读）")
st, r = req("POST", "/api/v1/admin/message/messages/send-batch",
            params={"toUserIds": readers, "title": "群发测试" + suffix, "content": "批量内容", "type": 1},
            token=admin_token)
check("批量群发返回 3 个 ID", code_of((st, r)) == 0 and isinstance(r.get("data"), list) and len(r.get("data")) == 3, r.get("data"))

# 每人 1 条未读
for uid in readers + [single_reader]:
    st, r = req("GET", "/api/v1/admin/message/messages/unread-count", params={"toUser": uid}, token=admin_token)
    check(f"读者 {uid} 未读数=1", code_of((st, r)) == 0 and data_of((st, r)) == 1, data_of((st, r)))

# ---------------------------------------------------------------- 全部已读
print("\n[4] 全部已读（read_flag 全置 1）")
batch_ids = r0_ids = []
# 收集这 3 条群发消息 ID
st, r = req("GET", "/api/v1/admin/message/messages", params={"page": 1, "size": 50, "title": "群发测试" + suffix}, token=admin_token)
group_msgs = (data_of((st, r)) or {}).get("records") or []
group_ids = [x.get("id") for x in group_msgs]
check("查到 3 条群发消息", len(group_ids) == 3, len(group_ids))

st, r = req("POST", "/api/v1/admin/message/messages/read-all", body=group_ids + [single_id], token=admin_token)
check("全部已读 code=0", code_of((st, r)) == 0, r.get("message"))

for uid in readers + [single_reader]:
    st, r = req("GET", "/api/v1/admin/message/messages/unread-count", params={"toUser": uid}, token=admin_token)
    check(f"读者 {uid} 已读后未读数=0", code_of((st, r)) == 0 and data_of((st, r)) == 0, data_of((st, r)))

print("\n" + "=" * 78)
print(f"M3 message E2E 结果：通过 {len(PASS)} / 失败 {len(FAIL)}")
if FAIL:
    print("失败项：" + ", ".join(FAIL))
    sys.exit(1)
print("全部通过 ✅")
