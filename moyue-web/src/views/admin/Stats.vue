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
          <span class="panel-title">数据概览</span>
          <el-button type="primary" size="small" :loading="loading" @click="load">刷新</el-button>
        </div>
      </template>

      <div v-loading="loading" class="stats-body">
        <el-alert
          v-if="errorTip"
          class="error-tip"
          type="error"
          show-icon
          :closable="false"
          :title="errorTip"
        />

        <el-row v-if="items.length > 0" :gutter="16">
          <el-col v-for="item in items" :key="item.key" :xs="12" :sm="8" :md="6">
            <div class="stat-box">
              <p class="stat-key" :title="item.key">{{ item.key }}</p>
              <p class="stat-value">{{ formatNumber(item.value) }}</p>
            </div>
          </el-col>
        </el-row>

        <EmptyState v-if="!loading && !errorTip && items.length === 0" mark="数" title="暂无统计数据" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { statsOverview } from '@/api/admin';

import EmptyState from '@/components/EmptyState.vue';
interface StatItem {
  key: string;
  value: number;
}

const items = ref<StatItem[]>([]);
const loading = ref(false);
const errorTip = ref('');

/** 后端返回的 Map 键名不固定，未知键名直接展示原始 key */
async function load() {
  loading.value = true;
  errorTip.value = '';
  try {
    const data = await statsOverview();
    const map = data || {};
    items.value = Object.keys(map).map((key) => ({
      key,
      value: Number(map[key] || 0),
    }));
  } catch (err) {
    items.value = [];
    const detail = err instanceof Error && err.message ? `（${err.message}）` : '';
    errorTip.value = `统计数据加载失败${detail}。请确认当前账号为管理员（role=3），否则后端会拒绝该请求。`;
  } finally {
    loading.value = false;
  }
}

function formatNumber(n: number) {
  return Number(n || 0).toLocaleString('zh-CN');
}

onMounted(load);
</script>

<style scoped>
.admin-page {
  padding: 16px;
}
.role-tip {
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
.stats-body {
  min-height: 120px;
}
.error-tip {
  margin-bottom: 12px;
}
.stat-box {
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid rgba(201, 56, 46, 0.18);
  border-left: 3px solid var(--moyue-crimson);
  border-radius: var(--moyue-radius);
  background: var(--moyue-paper);
}
.stat-key {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--moyue-ink);
  opacity: 0.65;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.stat-value {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--moyue-crimson);
  word-break: break-all;
}
</style>
