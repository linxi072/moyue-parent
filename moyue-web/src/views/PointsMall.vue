<template>
  <div class="points-mall">
    <!-- 积分账户概览 -->
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel-header">
          <span class="panel-title">我的积分账户</span>
          <el-button
            type="primary"
            size="small"
            :loading="accountLoading"
            :disabled="!userId"
            @click="loadAccount"
          >
            刷新
          </el-button>
        </div>
      </template>

      <el-alert
        v-if="!userId"
        class="tip"
        type="warning"
        show-icon
        :closable="false"
        title="未能获取当前登录用户 ID，请重新登录后重试。"
      />

      <div v-else v-loading="accountLoading" class="stat-row">
        <div class="stat-box">
          <p class="stat-label">可用余额</p>
          <p class="stat-value">{{ formatNumber(account?.balance) }}</p>
        </div>
        <div class="stat-box">
          <p class="stat-label">累计获得</p>
          <p class="stat-value">{{ formatNumber(account?.totalEarned) }}</p>
        </div>
        <div class="stat-box">
          <p class="stat-label">累计消费</p>
          <p class="stat-value">{{ formatNumber(account?.totalSpent) }}</p>
        </div>
      </div>
    </el-card>

    <!-- 商品列表 -->
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel-header">
          <span class="panel-title">积分好物</span>
          <span class="panel-sub">可用余额不足或库存为 0 时无法兑换</span>
        </div>
      </template>

      <el-alert
        v-if="productError"
        class="tip"
        type="error"
        show-icon
        :closable="false"
        :title="productError"
      />

      <div v-loading="productLoading">
        <EmptyState
          v-if="!productLoading && !productError && products.length === 0"
          mark="礼"
          title="暂无上架商品"
          description="积分商城还在备货，先去逛逛书城吧。"
        />

        <el-row v-else :gutter="16">
          <el-col v-for="item in products" :key="item.id" :xs="24" :sm="12" :md="8" :lg="6">
            <el-card class="product-card" shadow="hover">
              <CoverImage :src="item.imageUrl" :title="item.name" ratio="4 / 3" height="130px" />
              <h3 class="product-name" :title="item.name">{{ item.name }}</h3>
              <p class="product-desc" :title="item.description">{{ item.description || '暂无描述' }}</p>
              <p class="product-cost">{{ formatNumber(item.costPoints) }} 积分</p>
              <p class="product-stock">库存 {{ formatNumber(item.stock) }}</p>
              <el-button
                class="exchange-btn"
                type="primary"
                size="small"
                :disabled="!canExchange(item)"
                :loading="exchangingId === item.id"
                @click="exchange(item)"
              >
                {{ exchangeText(item) }}
              </el-button>
            </el-card>
          </el-col>
        </el-row>

        <div v-if="productTotal > size" class="pager">
          <el-pagination
            layout="prev, pager, next"
            :total="productTotal"
            :page-size="size"
            :current-page="page"
            @current-change="handleProductPage"
          />
        </div>
      </div>
    </el-card>

    <!-- 我的兑换订单 -->
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel-header">
          <span class="panel-title">我的兑换订单</span>
          <el-button
            size="small"
            :loading="orderLoading"
            :disabled="!userId"
            @click="handleOrderPage(1)"
          >
            刷新
          </el-button>
        </div>
      </template>

      <el-alert
        v-if="orderError"
        class="tip"
        type="error"
        show-icon
        :closable="false"
        :title="orderError"
      />

      <div v-loading="orderLoading">
        <EmptyState v-if="!orderLoading && !orderError && orders.length === 0" mark="单" title="还没有兑换记录，去上面挑一件吧" />

        <template v-else>
          <el-table :data="orders" border stripe size="small">
            <el-table-column prop="productName" label="商品" min-width="160" show-overflow-tooltip />
            <el-table-column label="消耗积分" width="110" align="right">
              <template #default="scope">{{ formatNumber(scope.row.costPoints) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="scope">
                <el-tag :type="orderTagType(scope.row.status)" size="small">
                  {{ orderStatusText(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="兑换时间" width="180">
              <template #default="scope">{{ formatTime(scope.row.createTime) }}</template>
            </el-table-column>
          </el-table>

          <div v-if="orderTotal > orderSize" class="pager">
            <el-pagination
              layout="prev, pager, next"
              :total="orderTotal"
              :page-size="orderSize"
              :current-page="orderPage"
              @current-change="handleOrderPage"
            />
          </div>
        </template>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { createOrder, getAccount, listOrders, listProducts } from '@/api/points';
import type { PointsAccount, PointsOrder, PointsProduct } from '@/api/points';
import { formatTime } from '@/api/types';
import CoverImage from '@/components/CoverImage.vue';
import EmptyState from '@/components/EmptyState.vue';

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger';

const userStore = useUserStore();

/** 当前登录用户 ID，取不到时降级为 0（页面各处据此给出提示） */
const userId = computed<number>(() => {
  const n = Number(userStore.userInfo?.id);
  return Number.isFinite(n) ? n : 0;
});

const account = ref<PointsAccount | null>(null);
const accountLoading = ref(false);

const products = ref<PointsProduct[]>([]);
const productLoading = ref(false);
const productError = ref('');
const page = ref(1);
const size = ref(12);
const productTotal = ref(0);

const orders = ref<PointsOrder[]>([]);
const orderLoading = ref(false);
const orderError = ref('');
const orderPage = ref(1);
const orderSize = ref(10);
const orderTotal = ref(0);

const exchangingId = ref<number>(0);

async function loadAccount() {
  if (!userId.value) {
    return;
  }
  accountLoading.value = true;
  try {
    account.value = await getAccount(userId.value);
  } catch {
    // request.ts 已统一提示业务错误，这里仅兜底防止未处理异常
    account.value = null;
  } finally {
    accountLoading.value = false;
  }
}

async function loadProducts() {
  productLoading.value = true;
  productError.value = '';
  try {
    const res = await listProducts(page.value, size.value);
    products.value = (res && res.records) || [];
    productTotal.value = Number(res?.total || 0);
  } catch {
    // request.ts 已统一提示业务错误，这里仅兜底防止未处理异常
    products.value = [];
    productTotal.value = 0;
    productError.value = '商品列表加载失败，请稍后重试。';
  } finally {
    productLoading.value = false;
  }
}

async function loadOrders(nextPage = orderPage.value) {
  if (!userId.value) {
    orders.value = [];
    return;
  }
  orderLoading.value = true;
  orderError.value = '';
  try {
    const res = await listOrders(userId.value, nextPage, orderSize.value);
    orders.value = (res && res.records) || [];
    orderTotal.value = Number(res?.total || 0);
    orderPage.value = nextPage;
  } catch {
    // request.ts 已统一提示业务错误，这里仅兜底防止未处理异常
    orders.value = [];
    orderTotal.value = 0;
    orderError.value = '兑换订单加载失败，请稍后重试。';
  } finally {
    orderLoading.value = false;
  }
}

function handleProductPage(p: number) {
  page.value = p;
  loadProducts();
}

function handleOrderPage(p: number) {
  orderPage.value = p;
  loadOrders(p);
}

function canExchange(product: PointsProduct): boolean {
  if (!userId.value) {
    return false;
  }
  if (Number(product.stock || 0) <= 0) {
    return false;
  }
  return Number(account.value?.balance || 0) >= Number(product.costPoints || 0);
}

function exchangeText(product: PointsProduct): string {
  if (Number(product.stock || 0) <= 0) {
    return '已售罄';
  }
  if (Number(account.value?.balance || 0) < Number(product.costPoints || 0)) {
    return '积分不足';
  }
  return '立即兑换';
}

async function exchange(product: PointsProduct) {
  const cost = Number(product.costPoints || 0);
  const balance = Number(account.value?.balance || 0);
  if (Number(product.stock || 0) <= 0) {
    ElMessage.warning('该商品已售罄');
    return;
  }
  if (balance < cost) {
    ElMessage.warning('积分不足，无法兑换该商品');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `兑换「${product.name}」将消耗 ${cost} 积分，兑换后余额为 ${balance - cost} 积分。确定继续吗？`,
      '兑换确认',
      { type: 'warning', confirmButtonText: '确认兑换', cancelButtonText: '再想想' }
    );
  } catch {
    // 用户取消
    return;
  }

  exchangingId.value = product.id;
  try {
    await createOrder(userId.value, product.id);
    ElMessage.success('兑换成功');
    await Promise.all([loadAccount(), loadProducts(), loadOrders(1)]);
  } catch {
    // request.ts 已统一提示业务错误
  } finally {
    exchangingId.value = 0;
  }
}

function orderStatusText(status: number): string {
  if (status === 1) return '已兑换';
  if (status === 2) return '已取消';
  return '待兑换';
}

function orderTagType(status: number): TagType {
  if (status === 1) return 'success';
  if (status === 2) return 'info';
  return 'warning';
}

function formatNumber(n: number | undefined | null): string {
  return Number(n || 0).toLocaleString('zh-CN');
}

onMounted(() => {
  loadProducts();
  loadAccount();
  loadOrders(1);
});
</script>

<style scoped>
.points-mall {
  padding: 16px;
}
.panel {
  margin-bottom: 16px;
  border-radius: var(--moyue-radius);
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--moyue-ink);
}
.panel-sub {
  font-size: 12px;
  color: var(--moyue-ink);
  opacity: 0.55;
}
.tip {
  margin-bottom: 12px;
}
.stat-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  min-height: 96px;
}
.stat-box {
  flex: 1 1 200px;
  padding: 16px;
  border: 1px solid rgba(201, 56, 46, 0.18);
  border-left: 3px solid var(--moyue-crimson);
  border-radius: var(--moyue-radius);
  background: var(--moyue-paper);
}
.stat-label {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--moyue-ink);
  opacity: 0.65;
}
.stat-value {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--moyue-crimson);
  word-break: break-all;
}
.product-card {
  margin-bottom: 16px;
  border-radius: var(--moyue-radius);
}
.product-cover {
  height: 130px;
  border-radius: 6px;
  background-color: var(--moyue-crimson-deep);
  background-size: cover;
  background-position: center;
}
.product-name {
  margin: 10px 0 4px;
  font-size: 15px;
  color: var(--moyue-ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.product-desc {
  margin: 0 0 8px;
  height: 32px;
  font-size: 12px;
  color: var(--moyue-ink);
  opacity: 0.65;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.product-cost {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--moyue-gold);
}
.product-stock {
  margin: 4px 0 10px;
  font-size: 12px;
  color: var(--moyue-ink);
  opacity: 0.6;
}
.exchange-btn {
  width: 100%;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 12px;
}
</style>
