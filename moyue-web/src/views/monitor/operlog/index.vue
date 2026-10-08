<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePage } from '@/composables/usePage'
import { pageOperLogs, deleteOperLog, clearOperLogs, operLogStats } from '@/api/operlog'
import type { OperLogStats } from '@/api/operlog'
import type { SysOperLog } from '@/api/types'
import { downloadFile } from '@/utils/download'
import { BUSINESS_TYPE_OPTIONS, STATUS_OPTIONS, labelOf, tagOf } from '@/utils/enums'

/**
 * ⑦ 操作日志域 —— @Log 切面异步落库，只读 + 清理，不提供编辑。
 * 注意：stats 为列表页概览端点，后端未就绪时静默降级为 0（同 dashboard 处理）。
 */
const { loading, total, records, query, search, reset, handleSizeChange, handleCurrentChange } =
  usePage<SysOperLog>(pageOperLogs, { title: '', operName: '', businessType: undefined, status: undefined })

const stats = ref<OperLogStats>({ total: 0, failTotal: 0, byBusinessType: {} })
const detailVisible = ref(false)
const detail = ref<SysOperLog>({})

async function loadStats() {
  try {
    stats.value = await operLogStats()
  } catch {
    /* 端点未就绪时保持 0，不阻断列表 */
  }
}

function openDetail(row: SysOperLog) {
  detail.value = row
  detailVisible.value = true
}

async function handleDelete(row: SysOperLog) {
  await ElMessageBox.confirm('确认删除该条操作日志？', '提示', { type: 'warning' })
  await deleteOperLog(row.id!)
  ElMessage.success('已删除')
  search()
}

async function handleClear() {
  await ElMessageBox.confirm('确认清空全部操作日志？该操作不可恢复', '警告', { type: 'warning' })
  await clearOperLogs()
  ElMessage.success('已清空')
  search()
  loadStats()
}

function handleExport() {
  downloadFile('/admin/system/logs/oper/export', 'oper-log.csv')
}

onMounted(() => {
  reset()
  loadStats()
})
</script>

<template>
  <div class="page-container">
    <el-row :gutter="16" class="mb-4">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat"><div class="label">日志总量</div><div class="value">{{ stats.total ?? 0 }}</div></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat">
            <div class="label">业务类型数</div>
            <div class="value">{{ Object.keys(stats.byBusinessType || {}).length }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat danger">
            <div class="label">失败总数</div>
            <div class="value">{{ stats.failTotal ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat">
            <div class="label">失败率</div>
            <div class="value">
              {{ stats.total ? Math.round(((stats.failTotal ?? 0) / stats.total) * 100) : 0 }}%
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="search-bar">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="操作模块">
          <el-input v-model="query.title" clearable style="width: 160px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="操作人员">
          <el-input v-model="query.operName" clearable style="width: 140px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="query.businessType" clearable placeholder="全部" style="width: 130px">
            <el-option
              v-for="o in BUSINESS_TYPE_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 110px">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset()">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div class="table-toolbar">
        <span class="title">操作日志</span>
        <div>
          <el-button @click="handleExport">导出 CSV</el-button>
          <el-button type="danger" @click="handleClear">清空</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="title" label="操作模块" min-width="130" />
        <el-table-column label="业务类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(BUSINESS_TYPE_OPTIONS, row.businessType)">
              {{ labelOf(BUSINESS_TYPE_OPTIONS, row.businessType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operName" label="操作人员" width="120" />
        <el-table-column prop="operIp" label="操作 IP" width="130" />
        <el-table-column prop="operUrl" label="请求地址" min-width="180" show-overflow-tooltip />
        <el-table-column prop="costTime" label="耗时(ms)" width="100" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(STATUS_OPTIONS, row.status)">
              {{ labelOf(STATUS_OPTIONS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operTime" label="操作时间" width="170" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="操作详情" width="720px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="操作模块">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="操作人员">{{ detail.operName }}</el-descriptions-item>
        <el-descriptions-item label="请求地址" :span="2">{{ detail.operUrl }}</el-descriptions-item>
        <el-descriptions-item label="请求方法" :span="2">{{ detail.method }}</el-descriptions-item>
        <el-descriptions-item label="操作 IP">{{ detail.operIp }}</el-descriptions-item>
        <el-descriptions-item label="耗时(ms)">{{ detail.costTime }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="tagOf(STATUS_OPTIONS, detail.status)">
            {{ labelOf(STATUS_OPTIONS, detail.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ detail.operTime }}</el-descriptions-item>
        <el-descriptions-item label="错误信息" :span="2">
          {{ detail.errorMsg || '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<style scoped>
.title {
  font-weight: 600;
  font-size: 15px;
}

.mb-4 {
  margin-bottom: 16px;
}

.stat .label {
  color: #909399;
  font-size: 13px;
}

.stat .value {
  font-size: 24px;
  font-weight: 600;
  margin-top: 6px;
}

.stat.danger .value {
  color: #f56c6c;
}
</style>
