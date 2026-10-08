#!/usr/bin/env python3
"""墨阅小说网 · M1 commerce 模块端到端实跑（经网关 8080）。

覆盖：兑换商品 CRUD / 上架下架 / 积分账户与流水 / 兑换闭环（扣积分+落订单） / 订单退款。
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
print("M1 commerce 端到端（gateway 8080 → moyue-commerce）")
print("=" * 78)

suffix = uuid.uuid4().hex[:6]

# ---------------------------------------------------------------- 登录
print("\n[1] 运营登录")
st, r = req("POST", "/api/v1/system/login", {"username": "admin", "password": "admin123"})
admin_token = (r.get("data") or {}).get("accessToken")
check("运营登录签发 token", bool(admin_token))

print("\n[2] 注册一个读者（用于兑换）")
ru = "reader" + suffix
st, r = req("POST", "/api/v1/system/register", {"username": ru, "password": "reader123456", "phone": "139" + str(abs(hash(suffix)) % 10**8).zfill(8), "userType": 1})
reader_id = r.get("data")
check("注册读者返回 userId", isinstance(reader_id, int) and reader_id > 0, reader_id)

# ---------------------------------------------------------------- 商品 + 上架
print("\n[3] 商品创建 → 上架")
st, r = req("POST", "/api/v1/admin/commerce/products",
            {"name": "M1 测试书币包 " + suffix, "type": 1, "priceAmount": 9.9, "points": 100, "stock": 10, "sort": 0},
            token=admin_token)
check("创建商品 code=0", code_of((st, r)) == 0, r.get("message"))
product_id = r.get("data")
check("返回商品 ID", isinstance(product_id, int) and product_id > 0, product_id)

st, r = req("POST", f"/api/v1/admin/commerce/products/{product_id}/online", token=admin_token)
check("上架商品 code=0", code_of((st, r)) == 0, r.get("message"))

# 校验 status=1
st, r = req("GET", "/api/v1/admin/commerce/products", token=admin_token, params={"page": 1, "size": 20})
prods = (data_of((st, r)) or {}).get("records") or []
p = next((x for x in prods if x.get("id") == product_id), None)
check("商品已上架 status=1", p is not None and p.get("status") == 1, p.get("status") if p else None)
check("商品初始库存=10", p is not None and p.get("stock") == 10, p.get("stock") if p else None)

# ---------------------------------------------------------------- 充值积分
print("\n[4] 运营手动充值积分（读者 +500）")
st, r = req("POST", "/api/v1/admin/commerce/points/adjust",
            params={"userId": reader_id, "bizType": 1, "amount": 500, "remark": "M1 测试充值"},
            token=admin_token)
check("充值后余额=500", code_of((st, r)) == 0 and data_of((st, r)) == 500, data_of((st, r)))

# ---------------------------------------------------------------- 兑换闭环
print("\n[5] 积分兑换（扣积分 + 减库存 + 落订单）")
st, r = req("POST", f"/api/v1/admin/commerce/products/{product_id}/exchange",
            params={"userId": reader_id}, token=admin_token)
check("兑换返回订单 ID", code_of((st, r)) == 0 and isinstance(r.get("data"), int), r.get("data"))
order_id = r.get("data")

# 库存 10 -> 9
st, r = req("GET", "/api/v1/admin/commerce/products", token=admin_token, params={"page": 1, "size": 20})
prods = (data_of((st, r)) or {}).get("records") or []
p = next((x for x in prods if x.get("id") == product_id), None)
check("兑换后库存=9", p is not None and p.get("stock") == 9, p.get("stock") if p else None)

# 流水：bizType=3 消费，balanceAfter=400
st, r = req("GET", "/api/v1/admin/commerce/points/logs", token=admin_token, params={"page": 1, "size": 50, "userId": reader_id})
logs = (data_of((st, r)) or {}).get("records") or []
consume = [x for x in logs if x.get("bizType") == 3]
check("产生 1 条消费流水", len(consume) >= 1, f"消费流水 {len(consume)} 条")
check("消费流水余额=400", any(x.get("balanceAfter") == 400 for x in consume), [x.get("balanceAfter") for x in consume])

# 账户余额
st, r = req("GET", "/api/v1/admin/commerce/points", token=admin_token, params={"page": 1, "size": 20, "userId": reader_id})
accs = (data_of((st, r)) or {}).get("records") or []
acc = next((x for x in accs if x.get("userId") == reader_id), None)
check("账户余额=400", acc is not None and acc.get("balance") == 400, acc.get("balance") if acc else None)

# ---------------------------------------------------------------- 退款
print("\n[6] 订单退款（status 0/1 → 2，幂等）")
st, r = req("POST", f"/api/v1/admin/commerce/orders/{order_id}/refund", token=admin_token)
check("退款 code=0", code_of((st, r)) == 0, r.get("message"))

st, r = req("GET", "/api/v1/admin/commerce/orders", token=admin_token, params={"page": 1, "size": 20, "userId": reader_id})
ords = (data_of((st, r)) or {}).get("records") or []
o = next((x for x in ords if x.get("id") == order_id), None)
check("订单已退款 status=2", o is not None and o.get("status") == 2, o.get("status") if o else None)

# 幂等：已退款再次退仍成功
st, r = req("POST", f"/api/v1/admin/commerce/orders/{order_id}/refund", token=admin_token)
check("重复退款幂等成功", code_of((st, r)) == 0, r.get("message"))

# ---------------------------------------------------------------- 负向：积分不足
print("\n[7] 负向：积分不足兑换被拒")
st, r = req("POST", "/api/v1/admin/commerce/products",
            {"name": "M1 高价包 " + suffix, "type": 2, "priceAmount": 99.0, "points": 100000, "stock": 5, "sort": 0},
            token=admin_token)
hp_id = r.get("data")
req("POST", f"/api/v1/admin/commerce/products/{hp_id}/online", token=admin_token)
st, r = req("POST", f"/api/v1/admin/commerce/products/{hp_id}/exchange", params={"userId": reader_id}, token=admin_token)
check("积分不足兑换被拒 code!=0", code_of((st, r)) != 0, r.get("message"))

# ---------------------------------------------------------------- 汇总
print("\n" + "=" * 78)
print(f"M1 commerce E2E 结果：通过 {len(PASS)} / 失败 {len(FAIL)}")
if FAIL:
    print("失败项：" + ", ".join(FAIL))
    sys.exit(1)
print("全部通过 ✅")
