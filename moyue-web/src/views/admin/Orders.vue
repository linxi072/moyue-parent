<template>
  <div class="admin-page">
    <el-alert
      class="role-tip"
      type="info"
      show-icon
      :closable="false"
      title="管理后台需要管理员角色（role=3），非管理员访问会被后端拒绝并返回业务码 10003。"
    />

    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel-header">
          <span class="panel-title">订单对账</span>
          <el-button type="primary" size="small" :loading="loading" @click="load">刷新</el-button>
        </div>
      </template>

      <el-alert
        v-if="errorTip"
        class="error-tip"
        type="error"
        show-icon
        :closable="false"
        :title="errorTip"
      />

      <div class="summary">
        <span class="summary-label">当前页合计金额</span>
        <span class="summary-value">¥ {{ pageAmount }}</span>
        <span class="summary-sub">共 {{ orders.length }} 笔（仅统计当前页订单）</span>
      </div>

      <el-table v-loading="loading" :data="orders" border stripe style="width: 100%">
        <el-table-column prop="orderNo" label="订单号" min-width="200" show-overflow-tooltip />
        <el-table-column label="书籍" min-width="120">
          <template #default="{ row }">
            {{ row.bookId ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="章节" min-width="120">
          <template #default="{ row }">
            {{ row.chapterId ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="金额" min-width="110">
          <template #default="{ row }">
            ¥ {{ formatAmount(row.amount) }}
          </template>
        </el-table-column>
        <el-table-column label="支付渠道" width="120">
          <template #default="{ row }">
            {{ payChannelText(row.payChannel) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="支付时间" min-width="180">
          <template #default="{ row }">
            {{ formatTime(row.payTime) }}
          </template>
        </el-table-column>
      </el-table>

      <EmptyState v-if="!loading && !errorTip && orders.length === 0" mark="单" title="暂无打赏订单" />

      <div v-if="total > size" class="pager">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="handlePage"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { formatTime } from '@/api/types';
import { listAdminOrders } from '@/api/admin';
import type { RewardOrderEntity } from '@/api/admin';

import EmptyState from '@/components/EmptyState.vue';
const orders = ref<RewardOrderEntity[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const errorTip = ref('');

/** 当前页合计金额（前端求和，后端未提供聚合接口） */
const pageAmount = computed(() => {
  const sum = orders.value.reduce((acc, item) => acc + Number(item.amount || 0), 0);
  return formatAmount(sum);
});

async function load() {
  loading.value = true;
  errorTip.value = '';
  try {
    const res = await listAdminOrders(page.value, size.value);
    orders.value = (res && res.records) || [];
    total.value = (res && res.total) || 0;
  } catch (err) {
    orders.value = [];
    total.value = 0;
    const detail = err instanceof Error && err.message ? `（${err.message}）` : '';
    errorTip.value = `订单列表加载失败${detail}。请确认当前账号为管理员（role=3），否则后端会拒绝该请求。`;
  } finally {
    loading.value = false;
  }
}

function handlePage(p: number) {
  page.value = p;
  load();
}

function formatAmount(n: number | null | undefined) {
  return Number(n || 0).toFixed(2);
}

function payChannelText(channel: number) {
  if (channel === 1) return '微信';
  if (channel === 2) return '支付宝';
  if (channel === 3) return '余额';
  return '-';
}

function statusText(s: number) {
  if (s === 0) return '待支付';
  if (s === 1) return '已支付';
  if (s === 2) return '已关闭';
  return `未知（${s}）`;
}

function statusTagType(s: number): 'warning' | 'success' | 'info' {
  if (s === 0) return 'warning';
  if (s === 1) return 'success';
  return 'info';
}

onMounted(load);
</script>

<style scoped>
.admin-page {
  padding: 16px;
}
.role-tip,
.error-tip {
  margin-bottom: 16px;
}
.panel {
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
.summary {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px 16px;
  border-left: 3px solid var(--moyue-gold);
  border-radius: var(--moyue-radius);
  background: var(--moyue-paper);
}
.summary-label {
  font-size: 13px;
  color: var(--moyue-ink);
  opacity: 0.7;
}
.summary-value {
  font-size: 22px;
  font-weight: 600;
  color: var(--moyue-crimson);
}
.summary-sub {
  font-size: 12px;
  color: var(--moyue-ink);
  opacity: 0.55;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
