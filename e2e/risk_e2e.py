#!/usr/bin/env python3
"""墨阅小说网 · M4 risk 模块端到端实跑（经网关 8080）。

覆盖：举报工单提交（待处理）→ 处理（通过/驳回状态流转）；敏感词 CRUD / 启用停用 / 文本命中检测（命中累加计数）。
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

print("=" * 78)
print("M4 risk 端到端（gateway 8080 → moyue-risk）")
print("=" * 78)

suffix = uuid.uuid4().hex[:6]

print("\n[1] 运营登录")
st, r = req("POST", "/api/v1/system/login", {"username": "admin", "password": "admin123"})
admin_token = (r.get("data") or {}).get("accessToken")
check("运营登录签发 token", bool(admin_token))

# ---------------------------------------------------------------- 举报工单
print("\n[2] 提交举报工单（待处理 status=0）")
st, r = req("POST", "/api/v1/admin/risk/reports",
            {"bizType": 1, "bizId": "book" + suffix, "reporterId": 1001, "reason": "涉嫌抄袭", "content": "该书大量复制他人内容"},
            token=admin_token)
check("提交举报 code=0", code_of((st, r)) == 0, r.get("message"))
report_id = r.get("data")
check("返回工单 ID", isinstance(report_id, int) and report_id > 0, report_id)

st, r = req("GET", "/api/v1/admin/risk/reports", token=admin_token, params={"page": 1, "size": 20})
recs = (data_of((st, r)) or {}).get("records") or []
rec = next((x for x in recs if x.get("id") == report_id), None)
check("工单初始状态=0（待处理）", rec is not None and rec.get("status") == 0, rec.get("status") if rec else None)

print("\n[3] 处理举报：通过（status 0→1）")
st, r = req("POST", f"/api/v1/admin/risk/reports/{report_id}/handle",
            params={"status": 1, "handler": "operator", "handleReason": "核实抄袭，已处理"}, token=admin_token)
check("处理通过 code=0", code_of((st, r)) == 0, r.get("message"))
st, r = req("GET", "/api/v1/admin/risk/reports", token=admin_token, params={"page": 1, "size": 20})
recs = (data_of((st, r)) or {}).get("records") or []
rec = next((x for x in recs if x.get("id") == report_id), None)
check("工单状态变为 1（已处理）", rec is not None and rec.get("status") == 1, rec.get("status") if rec else None)
check("处理人已记录", rec is not None and rec.get("handler") == "operator", rec.get("handler") if rec else None)

print("\n[4] 提交第二条举报并驳回（status 0→2）")
st, r = req("POST", "/api/v1/admin/risk/reports",
            {"bizType": 2, "bizId": "comment" + suffix, "reporterId": 1002, "reason": "辱骂"},
            token=admin_token)
report_id2 = r.get("data")
st, r = req("POST", f"/api/v1/admin/risk/reports/{report_id2}/handle",
            params={"status": 2, "handler": "operator", "handleReason": "举报不成立"}, token=admin_token)
check("驳回 code=0", code_of((st, r)) == 0, r.get("message"))
st, r = req("GET", "/api/v1/admin/risk/reports", token=admin_token, params={"page": 1, "size": 20})
recs = (data_of((st, r) or {}).get("records") or [])
rec = next((x for x in recs if x.get("id") == report_id2), None)
check("工单状态变为 2（驳回）", rec is not None and rec.get("status") == 2, rec.get("status") if rec else None)

print("\n[5] 负向：重复处理已处理工单被拒")
st, r = req("POST", f"/api/v1/admin/risk/reports/{report_id}/handle",
            params={"status": 1, "handler": "operator"}, token=admin_token)
check("已处理工单重复处理 code!=0", code_of((st, r)) != 0, r.get("message"))

# ---------------------------------------------------------------- 敏感词
print("\n[6] 敏感词创建 → 文本检测命中 → 停用后不命中")
st, r = req("POST", "/api/v1/admin/risk/sensitive-words",
            {"word": "违规词" + suffix, "level": 1, "enabled": 1}, token=admin_token)
check("创建敏感词 code=0", code_of((st, r)) == 0, r.get("message"))
sw_id = r.get("data")
check("返回敏感词 ID", isinstance(sw_id, int) and sw_id > 0, sw_id)

probe = "这是一段包含违规词" + suffix + "的文本"
st, r = req("GET", "/api/v1/admin/risk/sensitive-words/contains", params={"text": probe}, token=admin_token)
check("启用态文本命中=true", data_of((st, r)) is True, data_of((st, r)))
st, r = req("GET", "/api/v1/admin/risk/sensitive-words/match-level", params={"text": probe}, token=admin_token)
check("命中级别=1（拦截）", data_of((st, r)) == 1, data_of((st, r)))

# 命中计数 +1
st, r = req("GET", "/api/v1/admin/risk/sensitive-words", token=admin_token, params={"page": 1, "size": 20, "word": "违规词" + suffix})
sws = (data_of((st, r)) or {}).get("records") or []
sw = next((x for x in sws if x.get("id") == sw_id), None)
check("命中计数 >=1", sw is not None and sw.get("hitCount", 0) >= 1, sw.get("hitCount") if sw else None)

# 停用后不命中
st, r = req("POST", f"/api/v1/admin/risk/sensitive-words/{sw_id}/disable", token=admin_token)
check("停用 code=0", code_of((st, r)) == 0, r.get("message"))
st, r = req("GET", "/api/v1/admin/risk/sensitive-words/contains", params={"text": probe}, token=admin_token)
check("停用态文本不再命中=false", data_of((st, r)) is False, data_of((st, r)))

print("\n[7] 敏感词唯一性校验")
st, r = req("POST", "/api/v1/admin/risk/sensitive-words",
            {"word": "违规词" + suffix, "level": 2, "enabled": 1}, token=admin_token)
check("重复敏感词被拒 code!=0", code_of((st, r)) != 0, r.get("message"))

# ---------------------------------------------------------------- 汇总
print("\n" + "=" * 78)
print(f"M4 risk E2E 结果：通过 {len(PASS)} / 失败 {len(FAIL)}")
if FAIL:
    print("失败项：" + ", ".join(FAIL))
    sys.exit(1)
print("全部通过 ✅")
