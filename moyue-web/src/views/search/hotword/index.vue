<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="filter">
        <el-form-item label="热词">
          <el-input v-model="filter.word" placeholder="模糊匹配" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button type="success" @click="openCreate">新建热词</el-button>
          <el-button text type="primary" @click="loadTop">热词榜</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="word" label="热词" min-width="160" />
      <el-table-column prop="hitCount" label="命中" width="90" />
      <el-table-column prop="weight" label="权重" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="160" />
      <el-table-column label="操作" min-width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row as SearchHotWordVO)">编辑</el-button>
          <el-button link type="warning" @click="onTop(row as SearchHotWordVO)">置顶</el-button>
          <el-button link type="danger" @click="onDelete(row as SearchHotWordVO)">删除</el-button>
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

    <el-divider content-position="left">热词榜（启用中，按权重倒序）</el-divider>
    <div class="top-bar">
      <el-tag v-for="t in topList" :key="t.id" class="top-tag" type="warning">{{ t.word }}（{{ t.weight }}）</el-tag>
      <span v-if="!topList.length" class="toolbar-tip">暂无启用热词</span>
    </div>

    <el-divider content-position="left">搜索联想（前缀匹配，MySQL 降级）</el-divider>
    <div class="suggest-bar">
      <el-input v-model="kw" placeholder="输入前缀，如「仙侠」" style="width: 240px" @keyup.enter="loadSuggest" />
      <el-button type="primary" @click="loadSuggest">联想</el-button>
      <span v-for="s in suggestList" :key="s.id" class="suggest-tag">{{ s.word }}</span>
    </div>

    <el-dialog v-model="dialog" :title="form.id ? '编辑热词' : '新建热词'" width="min(480px, 94vw)">
      <el-form :model="form" label-width="80px">
        <el-form-item label="热词"><el-input v-model="form.word" placeholder="热词" /></el-form-item>
        <el-form-item label="权重"><el-input v-model="form.weight" type="number" /></el-form-item>
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
  pageHotWords,
  createHotWord,
  updateHotWord,
  deleteHotWord,
  topHotWords,
  suggestHotWords,
  type SearchHotWordVO
} from '@/api/search'

const rows = ref<SearchHotWordVO[]>([])
const total = ref(0)
const loading = ref(false)
const dialog = ref(false)
const topList = ref<SearchHotWordVO[]>([])
const suggestList = ref<SearchHotWordVO[]>([])
const kw = ref('')
const filter = reactive({ word: undefined as string | undefined })
const query = reactive({ page: 1, size: 10, word: undefined as string | undefined })
const form = reactive<Partial<SearchHotWordVO>>({})

async function load() {
  loading.value = true
  try {
    const r = await pageHotWords({ ...query, word: filter.word })
    rows.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

async function loadTop() {
  topList.value = await topHotWords(10)
}

async function loadSuggest() {
  if (!kw.value) return ElMessage.warning('请输入前缀')
  suggestList.value = await suggestHotWords(kw.value.trim())
}

function openCreate() {
  Object.assign(form, { id: undefined, word: '', weight: 0, enabled: 1 })
  dialog.value = true
}

function openEdit(row: SearchHotWordVO) {
  Object.assign(form, { ...row })
  dialog.value = true
}

async function submit() {
  if (!form.word) return ElMessage.warning('请填写热词')
  const payload = { word: form.word, weight: Number(form.weight) || 0, enabled: form.enabled ?? 1 }
  if (form.id) {
    await updateHotWord(form.id, payload)
    ElMessage.success('已保存')
  } else {
    await createHotWord(payload)
    ElMessage.success('已创建')
  }
  dialog.value = false
  load()
  loadTop()
}

async function onTop(row: SearchHotWordVO) {
  await updateHotWord(row.id, { weight: 9999 })
  ElMessage.success('已置顶')
  load()
  loadTop()
}

async function onDelete(row: SearchHotWordVO) {
  await ElMessageBox.confirm('确认删除该热词？', '提示', { type: 'warning' })
  await deleteHotWord(row.id)
  ElMessage.success('已删除')
  load()
  loadTop()
}

onMounted(() => {
  load()
  loadTop()
})
</script>

<style scoped>
.top-bar, .suggest-bar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.top-tag { margin: 0; }
.suggest-tag { padding: 2px 10px; background: var(--el-color-primary-light-9); color: var(--el-color-primary); border-radius: 12px; font-size: 13px; }
</style>
