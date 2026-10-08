<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="filter">
        <el-form-item label="标题"><el-input v-model="filter.title" placeholder="标题模糊" clearable /></el-form-item>
        <el-form-item label="已读">
          <el-select v-model="filter.readFlag" clearable placeholder="全部" style="width: 110px">
            <el-option :value="0" label="未读" />
            <el-option :value="1" label="已读" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button type="success" @click="openSend">单发</el-button>
          <el-button type="warning" @click="openBatch">批量群发</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column prop="toUser" label="接收人" width="120" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">{{ row.type === 1 ? '系统' : row.type === 2 ? '活动' : '私信' }}</template>
      </el-table-column>
      <el-table-column label="已读" width="90">
        <template #default="{ row }">
          <el-tag :type="row.readFlag === 1 ? 'success' : 'info'">{{ row.readFlag === 1 ? '已读' : '未读' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="160" />
      <el-table-column label="操作" min-width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" v-if="(row as MessageVO).readFlag !== 1" @click="onRead([row.id])">标记已读</el-button>
          <el-button link type="danger" @click="onDelete(row as MessageVO)">删除</el-button>
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

    <el-dialog v-model="dialog" :title="batchMode ? '批量群发' : '单发站内信'" width="min(520px, 94vw)">
      <el-form :model="form" label-width="80px">
        <el-form-item v-if="!batchMode" label="接收人">
          <el-input v-model="form.toUser" type="number" placeholder="用户 ID" />
        </el-form-item>
        <el-form-item v-else label="接收人">
          <el-input v-model="form.toUserIds" placeholder="逗号分隔的多个用户 ID" />
        </el-form-item>
        <el-form-item label="模板">
          <el-select v-model="form.templateCode" clearable placeholder="可选，引用模板" filterable style="width: 220px">
            <el-option v-for="t in templates" :key="t.id" :label="t.code" :value="(t.code as string)" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="正文"><el-input v-model="form.content" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submitSend">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageMessages,
  deleteMessage,
  readAll,
  sendMessage,
  sendBatch,
  pageTemplates,
  type MessageVO,
  type MessageTemplateVO
} from '@/api/message'

const rows = ref<MessageVO[]>([])
const total = ref(0)
const loading = ref(false)
const dialog = ref(false)
const batchMode = ref(false)
const templates = ref<MessageTemplateVO[]>([])
const filter = reactive({ title: undefined as string | undefined, readFlag: undefined as number | undefined })
const query = reactive({ page: 1, size: 10, title: undefined as string | undefined, readFlag: undefined as number | undefined })
const form = reactive<{ toUser?: number; toUserIds: string; templateCode?: string; title: string; content: string }>({
  toUser: undefined,
  toUserIds: '',
  templateCode: undefined,
  title: '',
  content: ''
})

async function load() {
  loading.value = true
  try {
    const r = await pageMessages({ ...query, title: filter.title, readFlag: filter.readFlag })
    rows.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

async function loadTemplates() {
  const r = await pageTemplates({ page: 1, size: 100, enabled: 1 })
  templates.value = r.records
}

function openSend() {
  batchMode.value = false
  Object.assign(form, { toUser: undefined, toUserIds: '', templateCode: undefined, title: '', content: '' })
  dialog.value = true
}

function openBatch() {
  batchMode.value = true
  Object.assign(form, { toUser: undefined, toUserIds: '', templateCode: undefined, title: '', content: '' })
  dialog.value = true
}

async function submitSend() {
  if (batchMode.value) {
    const ids = form.toUserIds.split(/[,\s]+/).map((s) => Number(s.trim())).filter((n) => n > 0)
    if (!ids.length) return ElMessage.warning('请填写接收人 ID')
    await sendBatch(ids, form.title, form.content, 1, form.templateCode, '用户')
    ElMessage.success(`已群发 ${ids.length} 人`)
  } else {
    if (!form.toUser) return ElMessage.warning('请填写接收人 ID')
    await sendMessage(Number(form.toUser), form.title, form.content, 1, form.templateCode, '用户')
    ElMessage.success('已发送')
  }
  dialog.value = false
  load()
}

async function onRead(ids: number[]) {
  await readAll(ids)
  ElMessage.success('已标记已读')
  load()
}

async function onDelete(row: MessageVO) {
  await ElMessageBox.confirm('确认删除该消息？', '提示', { type: 'warning' })
  await deleteMessage(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(() => {
  load()
  loadTemplates()
})
</script>
