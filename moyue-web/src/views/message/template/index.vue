<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="filter">
        <el-form-item label="关键词"><el-input v-model="filter.keyword" placeholder="编码 / 标题" clearable /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button type="success" @click="openCreate">新建模板</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="code" label="编码" min-width="140" />
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">{{ row.type === 1 ? '系统' : row.type === 2 ? '活动' : '私信' }}</template>
      </el-table-column>
      <el-table-column prop="content" label="正文" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row as MessageTemplateVO)">编辑</el-button>
          <el-button link type="success" v-if="(row as MessageTemplateVO).enabled !== 1" @click="onEnable(row as MessageTemplateVO)">启用</el-button>
          <el-button link type="warning" v-if="(row as MessageTemplateVO).enabled === 1" @click="onDisable(row as MessageTemplateVO)">停用</el-button>
          <el-button link type="danger" @click="onDelete(row as MessageTemplateVO)">删除</el-button>
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

    <el-dialog v-model="dialog" :title="form.id ? '编辑模板' : '新建模板'" width="min(560px, 94vw)">
      <el-form :model="form" label-width="80px">
        <el-form-item label="编码"><el-input v-model="form.code" placeholder="唯一编码，如 NOTICE" /></el-form-item>
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width: 140px">
            <el-option :value="1" label="系统" />
            <el-option :value="2" label="活动" />
            <el-option :value="3" label="私信" />
          </el-select>
        </el-form-item>
        <el-form-item label="正文">
          <el-input v-model="form.content" type="textarea" :rows="5" placeholder="支持 ${name} 占位，群发时替换为收件人" />
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
  pageTemplates,
  createTemplate,
  updateTemplate,
  deleteTemplate,
  enableTemplate,
  disableTemplate,
  type MessageTemplateVO
} from '@/api/message'

const rows = ref<MessageTemplateVO[]>([])
const total = ref(0)
const loading = ref(false)
const dialog = ref(false)
const filter = reactive({ keyword: undefined as string | undefined })
const query = reactive({ page: 1, size: 10, keyword: undefined as string | undefined })
const form = reactive<Partial<MessageTemplateVO>>({})

async function load() {
  loading.value = true
  try {
    const r = await pageTemplates({ ...query, keyword: filter.keyword })
    rows.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: undefined, code: '', title: '', type: 1, content: '' })
  dialog.value = true
}

function openEdit(row: MessageTemplateVO) {
  Object.assign(form, { ...row })
  dialog.value = true
}

async function submit() {
  if (!form.code) return ElMessage.warning('请填写编码')
  if (!form.title) return ElMessage.warning('请填写标题')
  const payload = { code: form.code, title: form.title, type: form.type ?? 1, content: form.content }
  if (form.id) {
    await updateTemplate(form.id, payload)
    ElMessage.success('已保存')
  } else {
    await createTemplate(payload)
    ElMessage.success('已创建')
  }
  dialog.value = false
  load()
}

async function onEnable(row: MessageTemplateVO) {
  await enableTemplate(row.id)
  ElMessage.success('已启用')
  load()
}

async function onDisable(row: MessageTemplateVO) {
  await disableTemplate(row.id)
  ElMessage.success('已停用')
  load()
}

async function onDelete(row: MessageTemplateVO) {
  await ElMessageBox.confirm('确认删除该模板？', '提示', { type: 'warning' })
  await deleteTemplate(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
