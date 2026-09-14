#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 墨阅小说网 · 全链路冒烟测试（全部经网关 :8080）
# 覆盖：登录取 token -> IM 会话/消息 -> 积分商城（含 2 个失败场景）-> 博客（含点赞幂等）
# 前置：
#   1) 原生启动 MySQL / Redis / Nacos（项目硬性约束：禁用 Docker，见 docs/不可忽视条件.md）
#   2) ./scripts/build.sh
#   3) ./scripts/start-all.sh，并在 Nacos 确认服务已注册
# 用法（Git Bash / macOS / Linux）：
#   chmod +x scripts/*.sh
#   ./scripts/smoke-test.sh
# 可选环境变量：BASE / PHONE / PASSWORD / USER_ID
# ---------------------------------------------------------------------------

# 不使用 set -e：单条用例失败需要继续执行后续用例并汇总

BASE="${BASE:-http://localhost:8080}"
PHONE="${PHONE:-13800000000}"
PASSWORD="${PASSWORD:-123456}"
USER_ID="${USER_ID:-1}"

CURL_OPTS=(-s --max-time 20)
PASS_COUNT=0
FAIL_COUNT=0

pass() {
    PASS_COUNT=$((PASS_COUNT + 1))
    echo "  [PASS] $1"
}

fail() {
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo "  [FAIL] $1"
}

# 断言响应体中的 code 等于期望值
assert_code() {
    local name="$1"
    local body="$2"
    local expected="$3"
    if echo "$body" | grep -q "\"code\":${expected}"; then
        pass "$name（code=$expected）"
    else
        fail "$name（期望 code=$expected，实际响应：$body）"
    fi
}

# 提取字符串字段值：extract_str "$body" "accessToken"
extract_str() {
    echo "$1" | sed -n "s/.*\"$2\":\"\([^\"]*\)\".*/\1/p" | head -n 1
}

# 提取数字字段值：extract_num "$body" "id"
extract_num() {
    echo "$1" | sed -n "s/.*\"$2\":\([0-9-]*\).*/\1/p" | head -n 1
}

summary() {
    echo "============================================================"
    echo "[smoke] 结果汇总：PASS=$PASS_COUNT，FAIL=$FAIL_COUNT"
    if [ "$FAIL_COUNT" -eq 0 ]; then
        echo "[smoke] 结论：全链路冒烟通过"
    else
        echo "[smoke] 结论：存在失败用例，请查看上方 [FAIL] 明细与 logs/ 日志"
    fi
    echo "============================================================"
}

if ! command -v curl > /dev/null 2>&1; then
    echo "[smoke][ERROR] 未检测到 curl，无法执行冒烟测试"
    exit 1
fi

echo "[smoke] 网关地址：$BASE"
echo "[smoke] 演示账号：$PHONE / $PASSWORD（userId=$USER_ID）"

# ------------------------------------------------------------------
# 1. 登录：白名单免鉴权，取 accessToken 供后续用例使用
# ------------------------------------------------------------------
echo ""
echo "== 1. 认证：登录获取 accessToken =="
LOGIN_BODY=$(curl "${CURL_OPTS[@]}" -X POST "$BASE/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"phone\":\"$PHONE\",\"password\":\"$PASSWORD\"}")
assert_code "登录 /auth/login" "$LOGIN_BODY" 0

TOKEN=$(extract_str "$LOGIN_BODY" "accessToken")
if [ -z "$TOKEN" ]; then
    fail "提取 accessToken（响应：$LOGIN_BODY）"
    echo "[smoke] 后续用例均依赖鉴权，测试中止"
    summary
    exit 1
fi
pass "提取 accessToken 成功"
AUTH=(-H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json')

# ------------------------------------------------------------------
# 2. 即时通讯 IM：建会话 -> 列表 -> 发消息（含 WebSocket 广播）-> 消息列表
# ------------------------------------------------------------------
echo ""
echo "== 2. 即时通讯 IM =="
CONV_BODY=$(curl "${CURL_OPTS[@]}" -X POST "$BASE/api/v1/im/conversations" "${AUTH[@]}" \
    -d "{\"type\":1,\"ownerId\":$USER_ID,\"memberIds\":[$USER_ID,2]}")
assert_code "创建单聊会话" "$CONV_BODY" 0

CONV_ID=$(extract_num "$CONV_BODY" "id")
if [ -z "$CONV_ID" ]; then
    fail "提取 conversationId（响应：$CONV_BODY），跳过后续 IM 用例"
else
    pass "提取 conversationId 成功：$CONV_ID"

    CONV_LIST=$(curl "${CURL_OPTS[@]}" "$BASE/api/v1/im/conversations?userId=$USER_ID&page=1&size=20" "${AUTH[@]}")
    assert_code "查询我的会话列表" "$CONV_LIST" 0

    SEND_BODY=$(curl "${CURL_OPTS[@]}" -X POST "$BASE/api/v1/im/conversations/$CONV_ID/messages" "${AUTH[@]}" \
        -d "{\"senderId\":$USER_ID,\"content\":\"冒烟测试消息\",\"type\":1}")
    assert_code "发送消息（落库后会向在线成员 WebSocket 广播）" "$SEND_BODY" 0

    MSG_LIST=$(curl "${CURL_OPTS[@]}" "$BASE/api/v1/im/conversations/$CONV_ID/messages?page=1&size=20" "${AUTH[@]}")
    assert_code "查询会话消息列表" "$MSG_LIST" 0
fi

# ------------------------------------------------------------------
# 3. 积分商城：账户 / 商品 / 正常兑换 + 2 个失败场景（均期望 code=10001）
# 说明：为让脚本可重复执行，正常兑换与两个失败场景都使用「当场创建的商品」，
#       不依赖种子商品的余额与库存状态。
# ------------------------------------------------------------------
echo ""
echo "== 3. 积分商城 =="
ACC_BODY=$(curl "${CURL_OPTS[@]}" "$BASE/api/v1/points/accounts/$USER_ID" "${AUTH[@]}")
assert_code "查询积分账户" "$ACC_BODY" 0

PROD_BODY=$(curl "${CURL_OPTS[@]}" "$BASE/api/v1/points/products?page=1&size=20" "${AUTH[@]}")
assert_code "查询上架商品列表" "$PROD_BODY" 0

# 3.1 正常兑换：新建低价商品（10 积分 / 库存 100）后兑换，期望成功
CHEAP_BODY=$(curl "${CURL_OPTS[@]}" -X POST "$BASE/api/v1/admin/points/products" "${AUTH[@]}" \
    -d '{"name":"冒烟-低价商品","description":"cost=10 stock=100","imageUrl":"","costPoints":10,"stock":100,"status":1}')
assert_code "后台新建低价商品（10 积分）" "$CHEAP_BODY" 0
CHEAP_ID=$(extract_num "$CHEAP_BODY" "id")
if [ -z "$CHEAP_ID" ]; then
    fail "提取低价商品 id（响应：$CHEAP_BODY），跳过正常兑换用例"
else
    ORDER_BODY=$(curl "${CURL_OPTS[@]}" -X POST "$BASE/api/v1/points/orders" "${AUTH[@]}" \
        -d "{\"userId\":$USER_ID,\"productId\":$CHEAP_ID}")
    assert_code "兑换低价商品（余额充足、库存充足）" "$ORDER_BODY" 0
fi

# 3.2 失败场景一：积分不足（天价商品 999999 积分）
RICH_BODY=$(curl "${CURL_OPTS[@]}" -X POST "$BASE/api/v1/admin/points/products" "${AUTH[@]}" \
    -d '{"name":"冒烟-天价商品","description":"cost=999999","imageUrl":"","costPoints":999999,"stock":10,"status":1}')
assert_code "后台新建天价商品（999999 积分）" "$RICH_BODY" 0
RICH_ID=$(extract_num "$RICH_BODY" "id")
if [ -z "$RICH_ID" ]; then
    fail "提取天价商品 id（响应：$RICH_BODY），跳过「积分不足」用例"
else
    LOW_BODY=$(curl "${CURL_OPTS[@]}" -X POST "$BASE/api/v1/points/orders" "${AUTH[@]}" \
        -d "{\"userId\":$USER_ID,\"productId\":$RICH_ID}")
    assert_code "兑换天价商品被拒（积分不足）" "$LOW_BODY" 10001
fi

# 3.3 失败场景二：库存不足（零库存商品）
ZERO_BODY=$(curl "${CURL_OPTS[@]}" -X POST "$BASE/api/v1/admin/points/products" "${AUTH[@]}" \
    -d '{"name":"冒烟-零库存商品","description":"stock=0","imageUrl":"","costPoints":10,"stock":0,"status":1}')
assert_code "后台新建零库存商品（stock=0）" "$ZERO_BODY" 0
ZERO_ID=$(extract_num "$ZERO_BODY" "id")
if [ -z "$ZERO_ID" ]; then
    fail "提取零库存商品 id（响应：$ZERO_BODY），跳过「库存不足」用例"
else
    OUT_BODY=$(curl "${CURL_OPTS[@]}" -X POST "$BASE/api/v1/points/orders" "${AUTH[@]}" \
        -d "{\"userId\":$USER_ID,\"productId\":$ZERO_ID}")
    assert_code "兑换零库存商品被拒（库存不足）" "$OUT_BODY" 10001
fi

ORDER_LIST=$(curl "${CURL_OPTS[@]}" "$BASE/api/v1/points/orders?userId=$USER_ID&page=1&size=20" "${AUTH[@]}")
assert_code "查询我的兑换订单" "$ORDER_LIST" 0

# ------------------------------------------------------------------
# 4. 博客空间：发布 -> 详情 -> 评论 -> 评论列表 -> 点赞幂等
# 点赞为「切换」语义：已赞则取消并 -1，未赞则新增并 +1，
# 故连续三次调用应得到 1 -> 0 -> 1，like_count 永远不会累加到 2。
# ------------------------------------------------------------------
echo ""
echo "== 4. 博客空间 =="
POST_BODY=$(curl "${CURL_OPTS[@]}" -X POST "$BASE/api/v1/blog/posts" "${AUTH[@]}" \
    -d "{\"authorId\":$USER_ID,\"title\":\"冒烟测试文章\",\"coverUrl\":\"\",\"summary\":\"冒烟摘要\",\"content\":\"冒烟正文\",\"status\":1}")
assert_code "发布文章" "$POST_BODY" 0

POST_ID=$(extract_num "$POST_BODY" "id")
if [ -z "$POST_ID" ]; then
    fail "提取文章 id（响应：$POST_BODY），跳过后续博客用例"
else
    pass "提取文章 id 成功：$POST_ID"

    DETAIL_BODY=$(curl "${CURL_OPTS[@]}" "$BASE/api/v1/blog/posts/$POST_ID" "${AUTH[@]}")
    assert_code "查询文章详情（view_count 自增）" "$DETAIL_BODY" 0

    CMT_BODY=$(curl "${CURL_OPTS[@]}" -X POST "$BASE/api/v1/blog/posts/$POST_ID/comments" "${AUTH[@]}" \
        -d "{\"userId\":$USER_ID,\"content\":\"冒烟测试评论\"}")
    assert_code "发表评论" "$CMT_BODY" 0

    CMT_LIST=$(curl "${CURL_OPTS[@]}" "$BASE/api/v1/blog/posts/$POST_ID/comments?page=1&size=20" "${AUTH[@]}")
    assert_code "查询评论列表" "$CMT_LIST" 0

    LIKE_1=$(curl "${CURL_OPTS[@]}" -X POST "$BASE/api/v1/blog/posts/$POST_ID/like" "${AUTH[@]}" \
        -d "{\"userId\":$USER_ID}")
    assert_code "第 1 次点赞" "$LIKE_1" 0
    COUNT_1=$(extract_num "$LIKE_1" "data")

    LIKE_2=$(curl "${CURL_OPTS[@]}" -X POST "$BASE/api/v1/blog/posts/$POST_ID/like" "${AUTH[@]}" \
        -d "{\"userId\":$USER_ID}")
    assert_code "第 2 次点赞（取消赞）" "$LIKE_2" 0
    COUNT_2=$(extract_num "$LIKE_2" "data")

    LIKE_3=$(curl "${CURL_OPTS[@]}" -X POST "$BASE/api/v1/blog/posts/$POST_ID/like" "${AUTH[@]}" \
        -d "{\"userId\":$USER_ID}")
    assert_code "第 3 次点赞（重新点赞）" "$LIKE_3" 0
    COUNT_3=$(extract_num "$LIKE_3" "data")

    if [ "$COUNT_1" = "1" ] && [ "$COUNT_2" = "0" ] && [ "$COUNT_3" = "1" ]; then
        pass "点赞幂等：like_count 序列 1 -> 0 -> 1，未重复计数"
    else
        fail "点赞幂等异常：like_count 序列为 $COUNT_1 -> $COUNT_2 -> $COUNT_3（期望 1 -> 0 -> 1）"
    fi
fi

# ------------------------------------------------------------------
# 汇总
# ------------------------------------------------------------------
echo ""
summary
if [ "$FAIL_COUNT" -ne 0 ]; then
    exit 1
fi
exit 0
