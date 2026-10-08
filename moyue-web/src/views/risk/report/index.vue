<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="filter">
        <el-form-item label="业务类型">
          <el-select v-model="filter.bizType" clearable placeholder="全部" style="width: 130px">
            <el-option :value="1" label="作品" />
            <el-option :value="2" label="评论" />
            <el-option :value="3" label="用户" />
            <el-option :value="4" label="帖子" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filter.status" clearable placeholder="全部" style="width: 120px">
            <el-option :value="0" label="待处理" />
            <el-option :value="1" label="已处理" />
            <el-option :value="2" label="驳回" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button type="success" @click="openCreate">提交举报</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column label="类型" width="80">
        <template #default="{ row }"><el-tag>{{ bizText(row.bizType) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="bizId" label="对象 ID" min-width="120" />
      <el-table-column prop="reporterId" label="举报人" width="100" />
      <el-table-column prop="reason" label="举报原因" min-width="140" show-overflow-tooltip />
      <el-table-column prop="content" label="内容摘要" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'warning'">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="handler" label="处理人" width="100" />
      <el-table-column prop="handleReason" label="处理说明" min-width="140" show-overflow-tooltip />
      <el-table-column prop="createTime" label="创建时间" min-width="160" />
      <el-table-column label="操作" min-width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" v-if="(row as ReportTicketVO).status === 0" @click="openHandle(row as ReportTicketVO)">处理</el-button>
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

    <el-dialog v-model="createDialog" title="提交举报" width="min(520px, 94vw)">
      <el-form :model="form" label-width="90px">
        <el-form-item label="业务类型">
          <el-select v-model="form.bizType" style="width: 160px">
            <el-option :value="1" label="作品" />
            <el-option :value="2" label="评论" />
            <el-option :value="3" label="用户" />
            <el-option :value="4" label="帖子" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象 ID"><el-input v-model="form.bizId" placeholder="被举报对象 ID" /></el-form-item>
        <el-form-item label="举报人"><el-input v-model="form.reporterId" type="number" placeholder="用户 ID" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="form.reason" placeholder="举报原因" /></el-form-item>
        <el-form-item label="内容摘要"><el-input v-model="form.content" type="textarea" :rows="2" placeholder="被举报内容摘要（可选）" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" @click="submit">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="handleDialog" title="处理举报工单" width="min(520px, 94vw)">
      <el-form :model="handleForm" label-width="90px">
        <el-form-item label="处理结果">
          <el-select v-model="handleForm.status" style="width: 160px">
            <el-option :value="1" label="已处理" />
            <el-option :value="2" label="驳回" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理人"><el-input v-model="handleForm.handler" placeholder="处理人" /></el-form-item>
        <el-form-item label="处理说明"><el-input v-model="handleForm.handleReason" type="textarea" :rows="2" placeholder="处理说明" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleDialog = false">取消</el-button>
        <el-button type="primary" @click="doHandle">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pageReports, submitReport, handleReport, type ReportTicketVO } from '@/api/risk'

const rows = ref<ReportTicketVO[]>([])
const total = ref(0)
const loading = ref(false)
const createDialog = ref(false)
const handleDialog = ref(false)
const filter = reactive<{ bizType?: number; status?: number }>({})
const query = reactive({ page: 1, size: 10, bizType: undefined as number | undefined, status: undefined as number | undefined })
const form = reactive<Partial<ReportTicketVO>>({ bizType: 1, bizId: '', reporterId: undefined, reason: '', content: '' })
const handleForm = reactive<{ id: number; status: number; handler?: string; handleReason?: string }>({ id: 0, status: 1, handler: 'operator', handleReason: '' })
const current = ref<ReportTicketVO | null>(null)

const bizText = (t?: number) => ({ 1: '作品', 2: '评论', 3: '用户', 4: '帖子' }[t ?? -1] ?? '其他')
const statusText = (s?: number) => ({ 0: '待处理', 1: '已处理', 2: '驳回' }[s ?? -1] ?? '未知')

async function load() {
  loading.value = true
  try {
    const r = await pageReports({ ...query, bizType: filter.bizType, status: filter.status })
    rows.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: undefined, bizType: 1, bizId: '', reporterId: undefined, reason: '', content: '' })
  createDialog.value = true
}
async function submit() {
  if (!form.bizId) return ElMessage.warning('请填写对象 ID')
  await submitReport({ ...form, reporterId: form.reporterId ? Number(form.reporterId) : 0 })
  ElMessage.success('已提交，待处理')
  createDialog.value = false
  load()
}

function openHandle(row: ReportTicketVO) {
  current.value = row
  Object.assign(handleForm, { id: row.id, status: 1, handler: 'operator', handleReason: '' })
  handleDialog.value = true
}
async function doHandle() {
  await handleReport(handleForm.id, handleForm.status, handleForm.handler, handleForm.handleReason)
  ElMessage.success(handleForm.status === 1 ? '已处理' : '已驳回')
  handleDialog.value = false
  load()
}

onMounted(load)
</script>
