<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="filter">
        <el-form-item label="类型">
          <el-select v-model="filter.taskType" clearable placeholder="全部" style="width: 130px">
            <el-option :value="1" label="续写" />
            <el-option :value="2" label="润色" />
            <el-option :value="3" label="摘要" />
            <el-option :value="4" label="大纲" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filter.status" clearable placeholder="全部" style="width: 120px">
            <el-option :value="0" label="待处理" />
            <el-option :value="1" label="成功" />
            <el-option :value="2" label="失败" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button type="success" @click="openCreate">新建任务</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }"><el-tag>{{ typeText(row.taskType) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="prompt" label="提示词" min-width="200" show-overflow-tooltip />
      <el-table-column prop="model" label="模型" width="120" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'info'">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="costTokens" label="消耗" width="80" />
      <el-table-column prop="createTime" label="创建时间" min-width="160" />
      <el-table-column label="操作" min-width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" v-if="(row as AiTaskVO).status === 0" @click="onRun(row as AiTaskVO)">运行</el-button>
          <el-button link type="info" v-if="(row as AiTaskVO).status === 1" @click="onView(row as AiTaskVO)">查看结果</el-button>
          <el-button link type="danger" @click="onDelete(row as AiTaskVO)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="load"
      />
    </div>

    <el-dialog v-model="createDialog" title="新建 AI 任务" width="min(560px, 94vw)">
      <el-form :model="form" label-width="90px">
        <el-form-item label="任务类型">
          <el-select v-model="form.taskType" style="width: 160px">
            <el-option :value="1" label="续写" />
            <el-option :value="2" label="润色" />
            <el-option :value="3" label="摘要" />
            <el-option :value="4" label="大纲" />
          </el-select>
        </el-form-item>
        <el-form-item label="提示词"><el-input v-model="form.prompt" type="textarea" :rows="3" placeholder="提示词" /></el-form-item>
        <el-form-item label="模型"><el-input v-model="form.model" placeholder="如 qwen-max" /></el-form-item>
        <el-form-item label="用户 ID"><el-input v-model="form.userId" type="number" placeholder="发起用户 ID" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" @click="submit">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resultDialog" title="生成结果" width="min(560px, 94vw)">
      <pre class="result-box">{{ currentResult }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageTasks, createTask, deleteTask, runTask, type AiTaskVO } from '@/api/ai'

const rows = ref<AiTaskVO[]>([])
const total = ref(0)
const loading = ref(false)
const createDialog = ref(false)
const resultDialog = ref(false)
const currentResult = ref('')
const filter = reactive<{ taskType?: number; status?: number }>({})
const query = reactive({ page: 1, size: 10, taskType: undefined as number | undefined, status: undefined as number | undefined })
const form = reactive<Partial<AiTaskVO>>({ taskType: 1, prompt: '', model: 'qwen-max', userId: undefined })

const typeText = (t?: number) => ({ 1: '续写', 2: '润色', 3: '摘要', 4: '大纲' }[t ?? -1] ?? '通用')
const statusText = (s?: number) => ({ 0: '待处理', 1: '成功', 2: '失败' }[s ?? -1] ?? '未知')

async function load() {
  loading.value = true
  try {
    const r = await pageTasks({ ...query, taskType: filter.taskType, status: filter.status })
    rows.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: undefined, taskType: 1, prompt: '', model: 'qwen-max', userId: undefined })
  createDialog.value = true
}
async function submit() {
  if (!form.prompt) return ElMessage.warning('请填写提示词')
  await createTask({ ...form, userId: form.userId ? Number(form.userId) : 0 })
  ElMessage.success('已创建')
  createDialog.value = false
  load()
}
async function onRun(row: AiTaskVO) {
  try {
    await runTask(row.id)
    ElMessage.success('运行完成（已扣配额）')
  } catch (e: any) {
    ElMessage.error('运行失败：' + (e?.message || '配额不足或已执行'))
  }
  load()
}
function onView(row: AiTaskVO) {
  currentResult.value = row.result || '（无结果）'
  resultDialog.value = true
}
async function onDelete(row: AiTaskVO) {
  await ElMessageBox.confirm('确认删除该任务？', '提示', { type: 'warning' })
  await deleteTask(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.result-box { white-space: pre-wrap; word-break: break-all; max-height: 320px; overflow: auto; background: var(--el-fill-color-light); padding: 10px; border-radius: 6px; }
</style>
