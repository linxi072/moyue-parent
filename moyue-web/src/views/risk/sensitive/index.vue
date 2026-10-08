<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="filter">
        <el-form-item label="敏感词">
          <el-input v-model="filter.word" placeholder="模糊匹配" clearable />
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="filter.level" clearable placeholder="全部" style="width: 120px">
            <el-option :value="1" label="拦截" />
            <el-option :value="2" label="告警" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button type="success" @click="openCreate">新建敏感词</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="word" label="敏感词" min-width="160" />
      <el-table-column label="级别" width="100">
        <template #default="{ row }">
          <el-tag :type="row.level === 1 ? 'danger' : 'warning'">{{ row.level === 1 ? '拦截' : '告警' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="hitCount" label="命中" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="160" />
      <el-table-column label="操作" min-width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row as SensitiveWordVO)">编辑</el-button>
          <el-button link type="success" v-if="(row as SensitiveWordVO).enabled !== 1" @click="onEnable(row as SensitiveWordVO)">启用</el-button>
          <el-button link type="warning" v-if="(row as SensitiveWordVO).enabled === 1" @click="onDisable(row as SensitiveWordVO)">停用</el-button>
          <el-button link type="danger" @click="onDelete(row as SensitiveWordVO)">删除</el-button>
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

    <el-card class="detect-card" shadow="never">
      <template #header>文本检测（命中累加计数）</template>
      <el-input v-model="detectText" placeholder="输入待检测文本" style="max-width: 420px" @keyup.enter="onDetect">
        <template #append>
          <el-button type="primary" @click="onDetect">检测</el-button>
        </template>
      </el-input>
      <span v-if="detectResult !== null" class="detect-result" :class="detectResult ? 'hit' : 'clean'">
        {{ detectResult ? '命中敏感词' : '未命中' }}
        <template v-if="detectLevel">（级别：{{ detectLevel === 1 ? '拦截' : '告警' }}）</template>
      </span>
    </el-card>

    <el-dialog v-model="dialog" :title="form.id ? '编辑敏感词' : '新建敏感词'" width="min(480px, 94vw)">
      <el-form :model="form" label-width="80px">
        <el-form-item label="敏感词"><el-input v-model="form.word" placeholder="敏感词（唯一）" /></el-form-item>
        <el-form-item label="级别">
          <el-select v-model="form.level" style="width: 140px">
            <el-option :value="1" label="拦截" />
            <el-option :value="2" label="告警" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.enabled" style="width: 140px">
            <el-option :value="1" label="启用" />
            <el-option :value="0" label="停用" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageSensitiveWords,
  createSensitiveWord,
  updateSensitiveWord,
  deleteSensitiveWord,
  enableSensitiveWord,
  disableSensitiveWord,
  sensitiveContains,
  sensitiveMatchLevel,
  type SensitiveWordVO
} from '@/api/risk'

const rows = ref<SensitiveWordVO[]>([])
const total = ref(0)
const loading = ref(false)
const dialog = ref(false)
const filter = reactive<{ word?: string; level?: number }>({})
const query = reactive({ page: 1, size: 10, word: undefined as string | undefined, level: undefined as number | undefined })
const form = reactive<Partial<SensitiveWordVO>>({})
const detectText = ref('')
const detectResult = ref<boolean | null>(null)
const detectLevel = ref<number | null>(null)

async function load() {
  loading.value = true
  try {
    const r = await pageSensitiveWords({ ...query, word: filter.word || undefined, level: filter.level })
    rows.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: undefined, word: '', level: 1, enabled: 1 })
  dialog.value = true
}
function openEdit(row: SensitiveWordVO) {
  Object.assign(form, { ...row })
  dialog.value = true
}
async function submit() {
  if (!form.word) return ElMessage.warning('请填写敏感词')
  const payload = { word: form.word, level: form.level ?? 1, enabled: form.enabled ?? 1 }
  if (form.id) {
    await updateSensitiveWord(form.id, payload)
    ElMessage.success('已保存')
  } else {
    const id = await createSensitiveWord(payload)
    if (typeof id === 'number') ElMessage.success('已创建')
  }
  dialog.value = false
  load()
}
async function onEnable(row: SensitiveWordVO) {
  await enableSensitiveWord(row.id)
  ElMessage.success('已启用')
  load()
}
async function onDisable(row: SensitiveWordVO) {
  await disableSensitiveWord(row.id)
  ElMessage.success('已停用')
  load()
}
async function onDelete(row: SensitiveWordVO) {
  await ElMessageBox.confirm('确认删除该敏感词？', '提示', { type: 'warning' })
  await deleteSensitiveWord(row.id)
  ElMessage.success('已删除')
  load()
}
async function onDetect() {
  if (!detectText.value) return ElMessage.warning('请输入待检测文本')
  detectResult.value = await sensitiveContains(detectText.value)
  detectLevel.value = await sensitiveMatchLevel(detectText.value)
}

onMounted(load)
</script>

<style scoped>
.detect-card { margin-top: 16px; }
.detect-result { margin-left: 12px; font-weight: 600; }
.detect-result.hit { color: var(--el-color-danger); }
.detect-result.clean { color: var(--el-color-success); }
</style>
