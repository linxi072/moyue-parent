<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="filter">
        <el-form-item label="用户 ID">
          <el-input v-model="filter.userId" placeholder="按用户 ID 查" clearable @keyup.enter="load" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button type="success" @click="openReset">重置配额</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="userId" label="用户 ID" width="120" />
      <el-table-column prop="total" label="总额" width="110" />
      <el-table-column prop="used" label="已用" width="110" />
      <el-table-column label="剩余" width="120">
        <template #default="{ row }">
          <el-tag :type="(row as AiQuotaVO).remain === 0 ? 'danger' : 'success'">{{ row.remain }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="160" />
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

    <el-dialog v-model="resetDialog" title="重置配额" width="min(460px, 94vw)">
      <el-form :model="resetForm" label-width="90px">
        <el-form-item label="用户 ID"><el-input v-model="resetForm.userId" type="number" placeholder="用户 ID" /></el-form-item>
        <el-form-item label="总额"><el-input v-model="resetForm.total" type="number" placeholder="配额总额（token）" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialog = false">取消</el-button>
        <el-button type="primary" @click="submitReset">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pageQuotas, resetQuota, type AiQuotaVO } from '@/api/ai'

const rows = ref<AiQuotaVO[]>([])
const total = ref(0)
const loading = ref(false)
const resetDialog = ref(false)
const filter = reactive<{ userId?: number }>({})
const query = reactive({ page: 1, size: 10, userId: undefined as number | undefined })
const resetForm = reactive<{ userId?: number; total: number }>({ userId: undefined, total: 1000 })

async function load() {
  loading.value = true
  try {
    const r = await pageQuotas({ ...query, userId: filter.userId ? Number(filter.userId) : undefined })
    rows.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

function openReset() {
  Object.assign(resetForm, { userId: filter.userId ? Number(filter.userId) : undefined, total: 1000 })
  resetDialog.value = true
}
async function submitReset() {
  if (!resetForm.userId) return ElMessage.warning('请填写用户 ID')
  await resetQuota(Number(resetForm.userId), Number(resetForm.total))
  ElMessage.success('已重置')
  resetDialog.value = false
  load()
}

onMounted(load)
</script>
