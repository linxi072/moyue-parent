#!/usr/bin/env python3
"""墨阅小说网 · M2 search 模块端到端实跑（经网关 8080）。

覆盖：热词 CRUD / 置顶（权重）/ 搜索联想（前缀匹配，MySQL 降级）/ 屏蔽词 CRUD / 启用停用。
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

suffix = uuid.uuid4().hex[:6]
print("=" * 78)
print("M2 search 端到端（gateway 8080 → moyue-search）")
print("=" * 78)

st, r = req("POST", "/api/v1/system/login", {"username": "admin", "password": "admin123"})
admin_token = (r.get("data") or {}).get("accessToken")
check("运营登录签发 token", bool(admin_token))

# ---------------------------------------------------------------- 热词 + 置顶
print("\n[1] 热词新增 → 置顶")
st, r = req("POST", "/api/v1/admin/search/hot-words", {"word": "都市强者" + suffix, "weight": 1, "enabled": 1}, token=admin_token)
check("创建热词A code=0", code_of((st, r)) == 0, r.get("message"))
a_id = r.get("data")
st, r = req("POST", "/api/v1/admin/search/hot-words", {"word": "仙侠世界" + suffix, "weight": 5, "enabled": 1}, token=admin_token)
check("创建热词B code=0", code_of((st, r)) == 0, r.get("message"))
b_id = r.get("data")

# 置顶 A
st, r = req("PUT", f"/api/v1/admin/search/hot-words/{a_id}", {"weight": 9999}, token=admin_token)
check("置顶热词A code=0", code_of((st, r)) == 0, r.get("message"))

st, r = req("GET", "/api/v1/admin/search/hot-words/top", params={"limit": 10}, token=admin_token)
top = data_of((st, r)) or []
check("热词榜返回数据", isinstance(top, list) and len(top) > 0, f"top={len(top)}")
# 断言用「权重严格最高」而非「绝对第一位」：历史多轮运行会遗留同为 9999 的热词，
# 并列时次序由命中次数/id 决定，脚本不应依赖数据累积状态。
a_top = next((x for x in top if x.get("id") == a_id), None)
check("置顶热词A 进入榜首", a_top is not None, a_id)
check("置顶热词A 权重为榜首最高",
      bool(top) and a_top is not None and (a_top.get("weight") or 0) >= max((x.get("weight") or 0) for x in top),
      f"A.weight={a_top.get('weight') if a_top else None} 榜首权重={[x.get('weight') for x in top[:3]]}")

# ---------------------------------------------------------------- 屏蔽词 + 停用
print("\n[2] 屏蔽词新增 → 停用")
st, r = req("POST", "/api/v1/admin/search/block-words", {"word": "暴力" + suffix, "level": 1, "enabled": 1}, token=admin_token)
check("创建屏蔽词 code=0", code_of((st, r)) == 0, r.get("message"))
bw_id = r.get("data")

st, r = req("POST", f"/api/v1/admin/search/block-words/{bw_id}/disable", token=admin_token)
check("停用屏蔽词 code=0", code_of((st, r)) == 0, r.get("message"))

st, r = req("GET", "/api/v1/admin/search/block-words", params={"page": 1, "size": 20}, token=admin_token)
bws = (data_of((st, r)) or {}).get("records") or []
bw = next((x for x in bws if x.get("id") == bw_id), None)
check("屏蔽词已停用 enabled=0", bw is not None and bw.get("enabled") == 0, bw.get("enabled") if bw else None)

# 重复词唯一校验
st, r = req("POST", "/api/v1/admin/search/block-words", {"word": "暴力" + suffix, "level": 1}, token=admin_token)
check("重复屏蔽词被拒 code!=0", code_of((st, r)) != 0, r.get("message"))

# ---------------------------------------------------------------- 联想（前缀）
print("\n[3] 搜索联想（前缀匹配）")
for w in ["仙侠世界" + suffix, "仙侠传说" + suffix, "都市强者" + suffix]:
    req("POST", "/api/v1/admin/search/hot-words", {"word": w, "weight": 3, "enabled": 1}, token=admin_token)

st, r = req("GET", "/api/v1/admin/search/hot-words/suggest", params={"keyword": "仙侠"}, token=admin_token)
sugs = data_of((st, r)) or []
check("联想返回 ≥2 条", len(sugs) >= 2, f"命中 {len(sugs)} 条")
check("联想结果均以「仙侠」开头", all((x.get("word") or "").startswith("仙侠") for x in sugs), [x.get("word") for x in sugs])

print("\n" + "=" * 78)
print(f"M2 search E2E 结果：通过 {len(PASS)} / 失败 {len(FAIL)}")
if FAIL:
    print("失败项：" + ", ".join(FAIL))
    sys.exit(1)
print("全部通过 ✅")
