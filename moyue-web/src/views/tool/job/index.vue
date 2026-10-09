<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePage } from '@/composables/usePage'
import { pageJobs, changeJobStatus, triggerJob, jobHealth, jobGroups } from '@/api/job'
import type { JobInfo, JobGroup } from '@/api/types'

/**
 * ⑩ 定时任务 —— 复用 XXL-Job Admin（架构 ADR-13），后端是 OpenAPI 代理。
 *
 * <p><strong>只读边界</strong>：说明书明确规定「不提供新增 / 编辑 / 删除任务」——
 * 任务由开发在代码中声明 {@code @XxlJob} 后由 Admin 注册，避免前端配置的 Cron 与代码不同步。
 * 因此本页只有列表 / 启停 / 触发 / 日志。
 */
const router = useRouter()
const { loading, total, records, query, search, reset, handleSizeChange, handleCurrentChange } =
  usePage<JobInfo>(pageJobs, { jobDesc: '', jobGroup: undefined })

const health = ref<{ available: boolean; address: string }>({ available: false, address: '' })
const groups = ref<JobGroup[]>([])

async function loadHealth() {
  try {
    health.value = await jobHealth()
  } catch {
    health.value = { available: false, address: '' }
  }
}

/** 执行器分组（任务页筛选源），Admin 不可达时静默降级为空 */
async function loadGroups() {
  try {
    groups.value = await jobGroups()
  } catch {
    groups.value = []
  }
}

async function toggleTrigger(row: JobInfo) {
  const next: 0 | 1 = row.triggerStatus === 1 ? 0 : 1
  await changeJobStatus(row.id!, next)
  ElMessage.success(next === 1 ? '已启动' : '已停止')
  search()
}

async function handleTrigger(row: JobInfo) {
  const { value } = await ElMessageBox.prompt(
    '执行参数（可留空）',
    `执行一次 - ${row.jobDesc}`,
    { inputValue: row.executorParam || '' }
  )
  await triggerJob(row.id!, value || '')
  ElMessage.success('已触发，请到任务日志查看执行结果')
}

function goLog(row: JobInfo) {
  router.push({ path: '/tool/job/log', query: { jobId: row.id, jobDesc: row.jobDesc } })
}

onMounted(() => {
  reset()
  loadHealth()
  loadGroups()
})
</script>

<template>
  <div class="page-container">
    <el-alert
      v-if="!health.available"
      type="warning"
      show-icon
      :closable="false"
      title="调度中心不可达，任务模块已降级为只读"
      :description="`无法连接 XXL-Job Admin${health.address ? '（' + health.address + '）' : ''}，请联系运维检查部署状态`"
      class="mb-4"
    />

    <el-card shadow="never" class="search-bar">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="任务描述">
          <el-input v-model="query.jobDesc" clearable style="width: 200px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="执行器分组">
          <el-select
            v-model="query.jobGroup"
            clearable
            filterable
            placeholder="全部分组"
            style="width: 200px"
          >
            <el-option
              v-for="g in groups"
              :key="g.id"
              :label="`${g.title || g.appname}（${g.appname}）`"
              :value="String(g.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset()">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">定时任务</span>
          <el-text type="info" size="small">
            任务由开发声明 @XxlJob 后由 Admin 注册，后台不提供增删改
          </el-text>
        </div>
      </template>

      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="id" label="任务 ID" width="90" />
        <el-table-column prop="jobDesc" label="任务描述" min-width="180" />
        <el-table-column prop="scheduleType" label="调度类型" width="110" />
        <el-table-column prop="scheduleConf" label="调度配置" width="160" />
        <el-table-column prop="executorHandler" label="JobHandler" min-width="180" show-overflow-tooltip />
        <el-table-column prop="author" label="负责人" width="110" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.triggerStatus === 1 ? 'success' : 'info'">
              {{ row.triggerStatus === 1 ? '运行中' : '已停止' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              :type="row.triggerStatus === 1 ? 'warning' : 'success'"
              :disabled="!health.available"
              @click="toggleTrigger(row)"
            >
              {{ row.triggerStatus === 1 ? '停止' : '启动' }}
            </el-button>
            <el-button link type="primary" :disabled="!health.available" @click="handleTrigger(row)">
              执行一次
            </el-button>
            <el-button link type="info" @click="goLog(row)">日志</el-button>
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
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title {
  font-weight: 600;
  font-size: 15px;
}

.mb-4 {
  margin-bottom: 16px;
}
</style>
