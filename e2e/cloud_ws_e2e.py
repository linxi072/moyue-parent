#!/usr/bin/env python3
"""IM WebSocket 经网关端到端广播验证。"""
import json, ssl, threading, time, urllib.request, urllib.error, uuid
import websocket

GW = "http://127.0.0.1:8080"
WS = "ws://127.0.0.1:8080/ws/im"
PASS, FAIL = [], []
received = []
ws_open = threading.Event()

def check(name, cond, detail=""):
    (PASS if cond else FAIL).append(name)
    print(("  [OK]   " if cond else "  [FAIL] ") + name + ((" — " + str(detail)) if detail else ""))

def req(method, path, body=None, token=None):
    d = json.dumps(body).encode() if body is not None else None
    h = {"Content-Type": "application/json"}
    if token:
        h["Authorization"] = "Bearer " + token
    r = urllib.request.Request(GW + path, data=d, headers=h, method=method)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        txt = e.read().decode()
        try:
            return e.code, json.loads(txt)
        except Exception:
            return e.code, {"raw": txt[:200]}

print("=" * 74)
print("IM WebSocket 经网关端到端验证")
print("=" * 74)

# 运营登录 + 读者注册
_, r = req("POST", "/api/v1/system/login", {"username": "admin", "password": "admin123"})
admin_token = r["data"]["accessToken"]
admin_id = r["data"]["userId"]

suffix = uuid.uuid4().hex[:6]
ru = "wsreader" + suffix
_, r = req("POST", "/api/v1/system/register", {"username": ru, "password": "reader123456",
                                               "phone": "138" + str(abs(hash(suffix)) % 10**8).zfill(8),
                                               "userType": 1})
_, r = req("POST", "/api/v1/system/login", {"username": ru, "password": "reader123456"})
reader_token = r["data"]["accessToken"]
reader_id = r["data"]["userId"]
print(f"\n[准备] admin={admin_id} reader={reader_id}")

# 读者创建会话，成员含 admin
_, r = req("POST", "/api/v1/im/conversations", {"type": 1, "memberIds": [admin_id], "title": "WS E2E"}, token=reader_token)
conv_id = r.get("data")
check("创建会话（成员含 admin）", isinstance(conv_id, int), conv_id)

def on_message(ws, msg):
    received.append(msg)
    print("    ← 收到推送:", msg[:160])

def on_open(ws):
    ws_open.set()
    print("    WS 已连接")

def on_error(ws, err):
    print("    WS 错误:", err)

# admin 经网关建立 WS（query 携带 access_token）
print("\n[1] 经网关建立 WebSocket（?access_token=）")
ws = websocket.WebSocketApp(f"{WS}?access_token={admin_token}",
                            on_open=on_open, on_message=on_message, on_error=on_error)
t = threading.Thread(target=ws.run_forever, daemon=True)
t.start()
check("WS 握手成功（未被 401 拦截）", ws_open.wait(15))

if ws_open.is_set():
    time.sleep(1)
    print("\n[2] 读者经 REST 发消息，验证 admin 收到广播")
    payload = {"content": "WS-BROADCAST-" + suffix, "type": 1}
    st, rr = req("POST", f"/api/v1/im/conversations/{conv_id}/messages", payload, token=reader_token)
    check("REST 发消息成功", st == 200 and rr.get("code") == 0, rr.get("message"))
    for _ in range(30):
        if received:
            break
        time.sleep(0.3)
    check("WS 收到广播推送", len(received) > 0, f"共 {len(received)} 条")
    if received:
        hit = any(("WS-BROADCAST-" + suffix) in m for m in received)
        check("推送内容含发送消息", hit, received[-1][:160])
    ws.close()

    print("\n[3] 无令牌连接应被拒绝")
    open2 = threading.Event()
    def on_open2(w):
        open2.set()
    ws2 = websocket.WebSocketApp(WS, on_open=on_open2, on_error=lambda w, e: None)
    t2 = threading.Thread(target=ws2.run_forever, daemon=True)
    t2.start()
    check("无令牌 WS 握手被拒", not open2.wait(8))
    ws2.close()

print("\n" + "=" * 74)
print(f"结果：PASS {len(PASS)} / FAIL {len(FAIL)}")
if FAIL:
    print("失败项：" + "; ".join(FAIL))
print("=" * 74)
