<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="filter">
        <el-form-item label="屏蔽词">
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
          <el-button type="success" @click="openCreate">新建屏蔽词</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="word" label="屏蔽词" min-width="160" />
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
          <el-button link type="primary" @click="openEdit(row as BlockWordVO)">编辑</el-button>
          <el-button link type="success" v-if="(row as BlockWordVO).enabled !== 1" @click="onEnable(row as BlockWordVO)">启用</el-button>
          <el-button link type="warning" v-if="(row as BlockWordVO).enabled === 1" @click="onDisable(row as BlockWordVO)">停用</el-button>
          <el-button link type="danger" @click="onDelete(row as BlockWordVO)">删除</el-button>
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

    <el-dialog v-model="dialog" :title="form.id ? '编辑屏蔽词' : '新建屏蔽词'" width="min(480px, 94vw)">
      <el-form :model="form" label-width="80px">
        <el-form-item label="屏蔽词"><el-input v-model="form.word" placeholder="屏蔽词（唯一）" /></el-form-item>
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
  pageBlockWords,
  createBlockWord,
  updateBlockWord,
  deleteBlockWord,
  enableBlockWord,
  disableBlockWord,
  type BlockWordVO
} from '@/api/search'

const rows = ref<BlockWordVO[]>([])
const total = ref(0)
const loading = ref(false)
const dialog = ref(false)
const filter = reactive({ word: undefined as string | undefined, level: undefined as number | undefined })
const query = reactive({ page: 1, size: 10, word: undefined as string | undefined, level: undefined as number | undefined })
const form = reactive<Partial<BlockWordVO>>({})

async function load() {
  loading.value = true
  try {
    const r = await pageBlockWords({ ...query, word: filter.word, level: filter.level })
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

function openEdit(row: BlockWordVO) {
  Object.assign(form, { ...row })
  dialog.value = true
}

async function submit() {
  if (!form.word) return ElMessage.warning('请填写屏蔽词')
  const payload = { word: form.word, level: form.level ?? 1, enabled: form.enabled ?? 1 }
  if (form.id) {
    await updateBlockWord(form.id, payload)
    ElMessage.success('已保存')
  } else {
    const id = await createBlockWord(payload)
    if (typeof id === 'number') ElMessage.success('已创建')
  }
  dialog.value = false
  load()
}

async function onEnable(row: BlockWordVO) {
  await enableBlockWord(row.id)
  ElMessage.success('已启用')
  load()
}

async function onDisable(row: BlockWordVO) {
  await disableBlockWord(row.id)
  ElMessage.success('已停用')
  load()
}

async function onDelete(row: BlockWordVO) {
  await ElMessageBox.confirm('确认删除该屏蔽词？', '提示', { type: 'warning' })
  await deleteBlockWord(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
