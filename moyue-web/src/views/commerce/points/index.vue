<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="filter">
        <el-form-item label="用户 ID">
          <el-input v-model="filter.userId" placeholder="按用户 ID 查" clearable @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button type="success" @click="openAdjust">调整积分</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="积分账户" name="account">
        <el-table :data="accs" v-loading="loadingAcc" border stripe>
          <el-table-column prop="userId" label="用户 ID" width="120" />
          <el-table-column prop="balance" label="可用" width="120" />
          <el-table-column prop="totalIncome" label="累计获得" width="120" />
          <el-table-column prop="totalConsume" label="累计消费" width="120" />
          <el-table-column prop="frozen" label="冻结" width="100" />
          <el-table-column prop="createTime" label="创建时间" min-width="160" />
        </el-table>
        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="accQuery.page"
            v-model:page-size="accQuery.size"
            :total="accTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="loadAccounts"
            @size-change="loadAccounts"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="积分流水" name="log">
        <el-table :data="logs" v-loading="loadingLog" border stripe>
          <el-table-column prop="userId" label="用户 ID" width="120" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">
              <el-tag :type="bizType(row.bizType)">{{ bizText(row.bizType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="changeAmount" label="变动" width="110" />
          <el-table-column prop="balanceAfter" label="余额" width="110" />
          <el-table-column prop="refId" label="关联单号" min-width="160" />
          <el-table-column prop="remark" label="备注" min-width="140" />
          <el-table-column prop="createTime" label="时间" min-width="160" />
        </el-table>
        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="logQuery.page"
            v-model:page-size="logQuery.size"
            :total="logTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="loadLogs"
            @size-change="loadLogs"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dialog" title="调整积分" width="min(480px, 94vw)">
      <el-form :model="adj" label-width="90px">
        <el-form-item label="用户 ID"><el-input v-model="adj.userId" type="number" placeholder="用户 ID" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="adj.bizType" style="width: 160px">
            <el-option :value="1" label="充值" />
            <el-option :value="2" label="打赏" />
            <el-option :value="3" label="消费" />
            <el-option :value="4" label="退款" />
          </el-select>
        </el-form-item>
        <el-form-item label="积分"><el-input v-model="adj.amount" type="number" placeholder="正数增 / 负数减" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="adj.remark" placeholder="备注" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submitAdjust">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  pagePointsAccounts,
  pagePointsLogs,
  adjustPoints,
  type PointsAccountVO,
  type PointsLogVO
} from '@/api/commerce'

const tab = ref('account')
const accs = ref<PointsAccountVO[]>([])
const accTotal = ref(0)
const loadingAcc = ref(false)
const logs = ref<PointsLogVO[]>([])
const logTotal = ref(0)
const loadingLog = ref(false)
const dialog = ref(false)
const filter = reactive<{ userId?: number }>({})
const accQuery = reactive({ page: 1, size: 10, userId: undefined as number | undefined })
const logQuery = reactive({ page: 1, size: 10, userId: undefined as number | undefined, bizType: undefined as number | undefined })
const adj = reactive<{ userId?: number; bizType: number; amount?: number; remark?: string }>({ bizType: 1 })

const bizText = (t?: number) => ({ 1: '充值', 2: '打赏', 3: '消费', 4: '退款' }[t ?? -1] ?? '其他')
const bizType = (t?: number) => (t === 3 ? 'danger' : t === 4 ? 'warning' : 'success')

function onSearch() {
  const v = filter.userId ? Number(filter.userId) : undefined
  accQuery.userId = v
  logQuery.userId = v
  loadAccounts()
  loadLogs()
}

async function loadAccounts() {
  loadingAcc.value = true
  try {
    const r = await pagePointsAccounts({ ...accQuery, userId: filter.userId ? Number(filter.userId) : undefined })
    accs.value = r.records
    accTotal.value = r.total
  } finally {
    loadingAcc.value = false
  }
}

async function loadLogs() {
  loadingLog.value = true
  try {
    const r = await pagePointsLogs({ ...logQuery, userId: filter.userId ? Number(filter.userId) : undefined })
    logs.value = r.records
    logTotal.value = r.total
  } finally {
    loadingLog.value = false
  }
}

function openAdjust() {
  Object.assign(adj, { userId: filter.userId ? Number(filter.userId) : undefined, bizType: 1, amount: undefined, remark: '' })
  dialog.value = true
}

async function submitAdjust() {
  if (!adj.userId) return ElMessage.warning('请填写用户 ID')
  if (adj.amount == null || adj.amount === 0) return ElMessage.warning('请填写非零积分')
  await adjustPoints({ userId: Number(adj.userId), bizType: adj.bizType, amount: Number(adj.amount), remark: adj.remark })
  ElMessage.success('已调整')
  dialog.value = false
  loadAccounts()
  loadLogs()
}

onMounted(() => {
  loadAccounts()
  loadLogs()
})
</script>
