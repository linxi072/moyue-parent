<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="filter">
        <el-form-item label="业务类型">
          <el-select v-model="filter.bizType" clearable placeholder="全部" style="width: 130px">
            <el-option :value="1" label="作品" />
            <el-option :value="2" label="评论" />
            <el-option :value="3" label="封面" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filter.status" clearable placeholder="全部" style="width: 120px">
            <el-option :value="0" label="待审" />
            <el-option :value="1" label="通过" />
            <el-option :value="2" label="驳回" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务 ID">
          <el-input v-model="filter.bizId" placeholder="作品 / 评论 ID" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag>{{ bizText(row.bizType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="bizId" label="业务 ID" min-width="130" />
      <el-table-column prop="content" label="待审内容" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'info'">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="auditor" label="审核人" width="110" />
      <el-table-column prop="reason" label="驳回原因" min-width="140" show-overflow-tooltip />
      <el-table-column prop="createTime" label="创建时间" min-width="160" />
      <el-table-column label="操作" min-width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="success" v-if="(row as AuditRecordVO).status === 0" @click="onApprove(row as AuditRecordVO)">通过</el-button>
          <el-button link type="danger" v-if="(row as AuditRecordVO).status === 0" @click="onReject(row as AuditRecordVO)">驳回</el-button>
          <el-button link type="danger" @click="onDelete(row as AuditRecordVO)">删除</el-button>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageAudits, approveAudit, rejectAudit, deleteAudit, type AuditRecordVO } from '@/api/risk'

const rows = ref<AuditRecordVO[]>([])
const total = ref(0)
const loading = ref(false)
const filter = reactive<{ bizType?: number; status?: number; bizId?: string }>({})
const query = reactive({ page: 1, size: 10, bizType: undefined as number | undefined, status: undefined as number | undefined, bizId: undefined as string | undefined })

const bizText = (t?: number) => ({ 1: '作品', 2: '评论', 3: '封面' }[t ?? -1] ?? '其他')
const statusText = (s?: number) => ({ 0: '待审', 1: '通过', 2: '驳回' }[s ?? -1] ?? '未知')

async function load() {
  loading.value = true
  try {
    const r = await pageAudits({ ...query, bizType: filter.bizType, status: filter.status, bizId: filter.bizId || undefined })
    rows.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

async function onApprove(row: AuditRecordVO) {
  await approveAudit(row.id, 'operator')
  ElMessage.success('已通过')
  load()
}
async function onReject(row: AuditRecordVO) {
  const { value } = await ElMessageBox.prompt('驳回原因', '驳回', { inputType: 'textarea' })
  await rejectAudit(row.id, 'operator', value)
  ElMessage.success('已驳回')
  load()
}
async function onDelete(row: AuditRecordVO) {
  await ElMessageBox.confirm('确认删除该审核工单？', '提示', { type: 'warning' })
  await deleteAudit(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
