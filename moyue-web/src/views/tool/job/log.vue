<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { usePage } from '@/composables/usePage'
import { pageJobLogs } from '@/api/job'
import type { JobLog } from '@/api/types'

/** ⑩-a 任务执行日志 —— 从「定时任务」页带 jobId 进入，数据来自 XXL-Job Admin */
const route = useRoute()
const jobId = ref<number>(Number(route.query.jobId) || 0)
const jobDesc = ref<string>((route.query.jobDesc as string) || '')

const { loading, total, records, query, search, reset, handleSizeChange, handleCurrentChange } =
  usePage<JobLog>((params) => pageJobLogs(jobId.value, params), {})

/** XXL-Job 用 200 表示成功 */
function codeTag(code?: number) {
  return code === 200 ? 'success' : 'danger'
}

function codeText(code?: number) {
  return code === 200 ? '成功' : '失败'
}

onMounted(() => reset())
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>任务日志{{ jobDesc ? ` · ${jobDesc}` : '' }}</span>
          <el-button link type="primary" @click="search">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="triggerTime" label="调度时间" width="180" />
        <el-table-column label="调度结果" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="codeTag(row.triggerCode)">{{ codeText(row.triggerCode) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="triggerMsg" label="调度备注" min-width="200" show-overflow-tooltip />
        <el-table-column prop="handleTime" label="执行时间" width="180" />
        <el-table-column label="执行结果" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="codeTag(row.handleCode)">{{ codeText(row.handleCode) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handleMsg" label="执行备注" min-width="220" show-overflow-tooltip />
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
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
