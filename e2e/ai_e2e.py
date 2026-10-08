#!/usr/bin/env python3
"""墨阅小说网 · M5 ai 模块端到端实跑（经网关 8080）。

覆盖：建任务 → 运行（status 0→1、result 非空、配额扣减 used+）→ 配额不足拒绝（PAY_FAILED）。
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
print("M5 ai 端到端（gateway 8080 → moyue-ai）")
print("=" * 78)

suffix = uuid.uuid4().hex[:6]

print("\n[1] 运营登录")
st, r = req("POST", "/api/v1/system/login", {"username": "admin", "password": "admin123"})
admin_token = (r.get("data") or {}).get("accessToken")
check("运营登录签发 token", bool(admin_token))

# 幂等化：第 5 步会把配额重置为 5 以验证「不足拒绝」，不还原会导致脚本二次运行必然失败。
# 故在开头先把配额恢复到基线 1000。
print("\n[1.5] 重置基线配额（保证脚本可重复执行）")
st, r = req("POST", "/api/v1/admin/ai/quota/reset",
            params={"userId": 1001, "total": 1000}, token=admin_token)
check("基线配额重置 code=0", code_of((st, r)) == 0, r.get("message"))

print("\n[2] 建任务（user=1001）")
st, r = req("POST", "/api/v1/admin/ai/tasks",
            {"prompt": "为开篇写一段简介" + suffix, "taskType": 3, "userId": 1001}, token=admin_token)
check("建任务 code=0", code_of((st, r)) == 0, r.get("message"))
task_id = r.get("data")
check("返回任务 ID", isinstance(task_id, int) and task_id > 0, task_id)

print("\n[3] 运行任务（扣配额 + 状态流转）")
st, r = req("POST", f"/api/v1/admin/ai/tasks/{task_id}/run", token=admin_token)
check("运行 code=0", code_of((st, r)) == 0, r.get("message"))

st, r = req("GET", "/api/v1/admin/ai/tasks", token=admin_token, params={"page": 1, "size": 20})
rec = find((data_of((st, r)) or {}).get("records"), task_id)
check("任务状态变为 1（完成）", rec is not None and rec.get("status") == 1, rec.get("status") if rec else None)
check("任务结果非空", rec is not None and bool(rec.get("result")), (rec or {}).get("result"))

print("\n[4] 配额扣减校验")
st, r = req("GET", "/api/v1/admin/ai/quota", token=admin_token, params={"userId": 1001})
qr = (data_of((st, r)) or {}).get("records") or []
q = next((x for x in qr if x.get("userId") == 1001), None)
check("配额 used >= 10", q is not None and (q.get("used") or 0) >= 10, q.get("used") if q else None)
check("配额 remain < total", q is not None and q.get("remain") < q.get("total"), (q.get("remain"), q.get("total")) if q else None)

print("\n[5] 配额不足拒绝（重置为 5 后运行扣 10）")
st, r = req("POST", "/api/v1/admin/ai/quota/reset", params={"userId": 1001, "total": 5}, token=admin_token)
check("重置配额 code=0", code_of((st, r)) == 0, r.get("message"))

st, r = req("POST", "/api/v1/admin/ai/tasks",
            {"prompt": "第二次生成" + suffix, "taskType": 3, "userId": 1001}, token=admin_token)
task_id2 = r.get("data")
st, r = req("POST", f"/api/v1/admin/ai/tasks/{task_id2}/run", token=admin_token)
check("配额不足运行被拒 code!=0", code_of((st, r)) != 0, r.get("message"))

st, r = req("GET", "/api/v1/admin/ai/tasks", token=admin_token, params={"page": 1, "size": 20})
rec2 = find((data_of((st, r)) or {}).get("records"), task_id2)
check("被拒任务保持待处理 status=0", rec2 is not None and rec2.get("status") == 0, rec2.get("status") if rec2 else None)

print("\n" + "=" * 78)
print(f"M5 ai E2E 结果：通过 {len(PASS)} / 失败 {len(FAIL)}")
if FAIL:
    print("失败项：" + ", ".join(FAIL))
    sys.exit(1)
print("全部通过 ✅")
