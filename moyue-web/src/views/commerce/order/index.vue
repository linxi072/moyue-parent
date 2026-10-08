<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="filter">
        <el-form-item label="订单号">
          <el-input v-model="filter.orderNo" placeholder="模糊匹配" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filter.status" clearable placeholder="全部" style="width: 120px">
            <el-option v-for="o in statusOpts" :key="o.v" :label="o.l" :value="o.v" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-toolbar">
      <span class="toolbar-title">付费订单</span>
      <el-button text type="primary" @click="loadSummary">刷新概览</el-button>
      <span class="toolbar-tip" v-if="summary">
        共 {{ summary.totalOrders }} 单 / 已付 {{ summary.paidOrders }} / 退款 {{ summary.refundOrders }} / 已付 ¥{{ summary.totalPaidAmount }}
      </span>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="orderNo" label="订单号" min-width="180" />
      <el-table-column prop="productName" label="商品" min-width="140" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">{{ typeText(row.productType) }}</template>
      </el-table-column>
      <el-table-column prop="amount" label="金额" width="100" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="160" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="warning" @click="onRefund(row as PayOrderVO)" v-if="(row as PayOrderVO).status === 1">退款</el-button>
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
import { pageOrders, summaryOrders, refundOrder, type PayOrderVO } from '@/api/commerce'

const rows = ref<PayOrderVO[]>([])
const total = ref(0)
const loading = ref(false)
const filter = reactive({ orderNo: undefined as string | undefined, status: undefined as number | undefined })
const query = reactive({ page: 1, size: 10, orderNo: undefined as string | undefined, status: undefined as number | undefined })
const summary = ref<Record<string, any> | null>(null)

const statusOpts = [
  { v: 0, l: '待付' },
  { v: 1, l: '已付' },
  { v: 2, l: '退款' },
  { v: 3, l: '关闭' }
]
const statusText = (s?: number) => ({ 0: '待付', 1: '已付', 2: '退款', 3: '关闭' }[s ?? -1] ?? '未知')
const statusType = (s?: number) => (s === 1 ? 'success' : s === 2 ? 'danger' : s === 3 ? 'info' : 'warning')
const typeText = (t?: number) => ({ 1: '书币', 2: '会员', 3: '打赏' }[t ?? -1] ?? '其他')

async function load() {
  loading.value = true
  try {
    const r = await pageOrders({ ...query, orderNo: filter.orderNo, status: filter.status })
    rows.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  summary.value = await summaryOrders()
}

async function onRefund(row: PayOrderVO) {
  await ElMessageBox.confirm(`确认对订单 ${row.orderNo} 退款？`, '提示', { type: 'warning' })
  await refundOrder(row.id)
  ElMessage.success('已退款')
  load()
  loadSummary()
}

onMounted(() => {
  load()
  loadSummary()
})
</script>
