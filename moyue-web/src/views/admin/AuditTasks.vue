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
          <span class="panel-title">内容审核</span>
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

      <el-tabs v-model="tab" @tab-change="handleTabChange">
        <el-tab-pane label="审核任务" name="tasks">
          <div class="filter-bar">
            <span class="filter-label">状态筛选</span>
            <el-select v-model="status" style="width: 160px" @change="load">
              <el-option
                v-for="opt in statusOptions"
                :key="String(opt.value)"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </div>
        </el-tab-pane>
        <el-tab-pane label="待审评论" name="comments">
          <div class="filter-bar">
            <span class="filter-hint">待审评论即状态为「待投递」的评论类审核任务（业务类型 2 评论）。</span>
          </div>
        </el-tab-pane>
      </el-tabs>

      <el-table v-loading="loading" :data="tasks" border stripe style="width: 100%">
        <el-table-column prop="id" label="任务 ID" min-width="180" />
        <el-table-column label="业务类型" width="120">
          <template #default="{ row }">
            {{ bizTypeText(row.bizType) }}
          </template>
        </el-table-column>
        <el-table-column prop="bizId" label="业务 ID" min-width="160" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="180">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status !== 2">
              <el-button type="success" link @click="handleDecide(row, true)">通过</el-button>
              <el-button type="danger" link @click="handleDecide(row, false)">驳回</el-button>
            </template>
            <span v-else class="op-done">已完成，不可操作</span>
          </template>
        </el-table-column>
      </el-table>

      <EmptyState v-if="!loading && !errorTip && tasks.length === 0" mark="审" :title="tab === 'tasks' ? '当前筛选条件下暂无审核任务' : '暂无待审评论'" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { AUDIT_STATUS_TEXT, formatTime } from '@/api/types';
import { approveTask, listAuditTasks, listPendingComments, rejectTask } from '@/api/admin';
import type { AuditTaskEntity } from '@/api/admin';

import EmptyState from '@/components/EmptyState.vue';
type TabName = 'tasks' | 'comments';

const tasks = ref<AuditTaskEntity[]>([]);
const loading = ref(false);
const errorTip = ref('');
const tab = ref<TabName>('tasks');
const status = ref<number | ''>('');

const statusOptions: { label: string; value: number | '' }[] = [
  { label: '全部', value: '' },
  { label: '待审（0）', value: 0 },
  { label: '审核中（1）', value: 1 },
  { label: '已完成（2）', value: 2 },
];

async function load() {
  loading.value = true;
  errorTip.value = '';
  try {
    if (tab.value === 'comments') {
      tasks.value = (await listPendingComments()) || [];
    } else {
      tasks.value = (await listAuditTasks(status.value === '' ? undefined : status.value)) || [];
    }
  } catch (err) {
    tasks.value = [];
    const detail = err instanceof Error && err.message ? `（${err.message}）` : '';
    errorTip.value = `审核任务加载失败${detail}。请确认当前账号为管理员（role=3），否则后端会拒绝该请求。`;
  } finally {
    loading.value = false;
  }
}

function handleTabChange() {
  load();
}

function bizTypeText(bizType: number) {
  if (bizType === 1) return '章节';
  if (bizType === 2) return '评论';
  return `未知（${bizType}）`;
}

/** 复用共享常量；后端另有 3 死信状态，此处补充兜底 */
function statusText(s: number) {
  return AUDIT_STATUS_TEXT[s] || (s === 3 ? '死信' : `状态 ${s}`);
}

function statusTagType(s: number): 'info' | 'warning' | 'success' | 'danger' {
  if (s === 0) return 'warning';
  if (s === 1) return 'info';
  if (s === 2) return 'success';
  return 'danger';
}

async function handleDecide(row: AuditTaskEntity, passed: boolean) {
  const action = passed ? '通过' : '驳回';
  try {
    await ElMessageBox.confirm(
      `确认${action}任务 ${row.id}（${bizTypeText(row.bizType)} / 业务 ID ${row.bizId}）？审核后不可重复裁决。`,
      `审核${action}`,
      { type: 'warning', confirmButtonText: `确认${action}`, cancelButtonText: '取消' }
    );
  } catch {
    return;
  }
  try {
    if (passed) {
      await approveTask(row.id);
    } else {
      await rejectTask(row.id);
    }
    ElMessage.success(`已${action}`);
    await load();
  } catch (err) {
    ElMessage.error(`审核${action}失败：${err instanceof Error ? err.message : '未知错误'}`);
  }
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
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.filter-label {
  font-size: 14px;
  color: var(--moyue-ink);
}
.filter-hint {
  font-size: 13px;
  color: var(--moyue-ink);
  opacity: 0.6;
}
.op-done {
  font-size: 12px;
  color: var(--moyue-ink);
  opacity: 0.5;
}
</style>
