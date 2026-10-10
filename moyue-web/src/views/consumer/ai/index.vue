<template>
  <div class="consumer-page">
    <div class="quota-card" v-if="quota">
      <div class="q-item"><span>{{ quota.total ?? 0 }}</span><label>总额度</label></div>
      <div class="q-item"><span>{{ quota.used ?? 0 }}</span><label>已用</label></div>
      <div class="q-item"><span class="remain">{{ quota.remain ?? 0 }}</span><label>剩余</label></div>
    </div>

    <div class="bar">
      <span class="title">我的创作</span>
      <el-select v-model="filter.status" size="small" placeholder="全部状态" clearable style="width: 120px" @change="onFilter">
        <el-option :value="0" label="待处理" />
        <el-option :value="1" label="成功" />
        <el-option :value="2" label="失败" />
      </el-select>
    </div>

    <div v-loading="loading" class="list">
      <div v-for="t in tasks" :key="t.id" class="task" @click="onOpen(t)">
        <div class="task-head">
          <span class="task-type">{{ taskTypeText(t.taskType) }}</span>
          <el-tag size="small" :type="statusTag(t.status)">{{ statusText(t.status) }}</el-tag>
        </div>
        <div class="task-prompt">{{ t.prompt || '（无提示词）' }}</div>
        <div class="task-time">{{ t.createTime }}</div>
      </div>
      <el-empty v-if="!loading && !tasks.length" description="暂无创作任务" />
    </div>

    <div class="pager">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20]"
        layout="total, prev, pager, next"
        @current-change="load"
        @size-change="load"
      />
    </div>

    <el-drawer v-model="drawer" title="任务详情" size="90%" direction="btt">
      <template v-if="current">
        <p><b>类型：</b>{{ taskTypeText(current.taskType) }}</p>
        <p><b>状态：</b>{{ statusText(current.status) }}</p>
        <p><b>模型：</b>{{ current.model || '—' }}</p>
        <p><b>消耗 Token：</b>{{ current.costTokens ?? '—' }}</p>
        <p><b>提示词：</b></p>
        <pre class="pre">{{ current.prompt }}</pre>
        <p><b>结果：</b></p>
        <pre class="pre">{{ current.result || '（暂无结果）' }}</pre>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pageMyTasks, getMyTask, myQuota, type AiTaskVO, type AiQuotaVO } from '@/api/ai'

// AI 创作：我的任务列表 + 配额。任务详情按归属校验，非本人任务后端返回「不存在」。
const quota = ref<AiQuotaVO | null>(null)
const tasks = ref<AiTaskVO[]>([])
const total = ref(0)
const loading = ref(false)
const drawer = ref(false)
const current = ref<AiTaskVO | null>(null)
const filter = reactive({ status: undefined as number | undefined })
const query = reactive({ page: 1, size: 10, status: undefined as number | undefined })

const taskTypeText = (t?: number) =>
  ({ 1: '续写', 2: '润色', 3: '摘要', 4: '大纲' }[t ?? -1] ?? '未知')
const statusText = (s?: number) =>
  ({ 0: '待处理', 1: '成功', 2: '失败' }[s ?? -1] ?? '未知')
const statusTag = (s?: number) => (s === 1 ? 'success' : s === 2 ? 'danger' : 'info')

function onFilter() {
  query.page = 1
  load()
}

async function load() {
  loading.value = true
  query.status = filter.status
  try {
    const [q, r] = await Promise.all([myQuota(), pageMyTasks(query)])
    quota.value = q ?? null
    tasks.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

async function onOpen(t: AiTaskVO) {
  try {
    current.value = await getMyTask(t.id)
    drawer.value = true
  } catch {
    ElMessage.error('任务不存在或无权查看')
  }
}

onMounted(load)
</script>

<style scoped lang="scss">
.quota-card {
  display: flex;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 16px;
  margin-bottom: 14px;
  .q-item {
    flex: 1;
    text-align: center;
    span { font-size: 20px; font-weight: 700; color: var(--color-text); }
    span.remain { color: var(--color-primary); }
    label { display: block; font-size: 12px; color: var(--color-text-muted); margin-top: 4px; }
  }
}
.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  .title { font-size: 16px; font-weight: 600; color: var(--color-text); }
}
.list { display: flex; flex-direction: column; gap: 10px; margin-top: 6px; }
.task {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 12px;
  cursor: pointer;
  .task-head { display: flex; align-items: center; justify-content: space-between; }
  .task-type { font-weight: 600; color: var(--color-text); }
  .task-prompt {
    margin: 6px 0;
    font-size: 13px;
    color: var(--color-text-secondary);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  .task-time { font-size: 12px; color: var(--color-text-muted); }
}
.pager { display: flex; justify-content: center; margin-top: 12px; }
.pre {
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 10px;
  font-family: inherit;
  font-size: 13px;
  margin: 4px 0 12px;
}
</style>
