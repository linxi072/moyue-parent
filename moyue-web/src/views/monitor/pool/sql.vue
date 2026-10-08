<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { sqlList, slowSqlList, urlStats, resetSqlStats } from '@/api/monitor'

/** ⑭-a SQL / URL 监控 —— 定位慢查询与热点接口 */
const router = useRouter()
const activeTab = ref('sql')
const loading = ref(false)
const sqls = ref<any[]>([])
const slowSqls = ref<any[]>([])
const urls = ref<any[]>([])

async function load() {
  loading.value = true
  try {
    if (activeTab.value === 'sql') {
      sqls.value = (await sqlList()) || []
    } else if (activeTab.value === 'slow') {
      slowSqls.value = (await slowSqlList()) || []
    } else {
      urls.value = (await urlStats()) || []
    }
  } finally {
    loading.value = false
  }
}

async function handleReset() {
  await ElMessageBox.confirm('确认重置全部 SQL 统计？历史数据将清零', '提示', { type: 'warning' })
  await resetSqlStats()
  ElMessage.success('已重置')
  load()
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="card-header">
          <el-tabs v-model="activeTab" class="flex-1" @tab-change="load">
            <el-tab-pane label="SQL 监控" name="sql" />
            <el-tab-pane label="慢 SQL" name="slow" />
            <el-tab-pane label="URL 统计" name="url" />
          </el-tabs>
          <div>
            <el-button link type="primary" @click="load">刷新</el-button>
            <el-button link type="danger" @click="handleReset">重置统计</el-button>
          </div>
        </div>
      </template>

      <el-table v-if="activeTab === 'sql'" :data="sqls" border stripe max-height="560">
        <el-table-column prop="sql" label="SQL 语句" min-width="320" show-overflow-tooltip />
        <el-table-column prop="executeCount" label="执行次数" width="100" align="center" />
        <el-table-column prop="totalTime" label="总耗时(ms)" width="110" align="center" />
        <el-table-column prop="maxTimespan" label="最大耗时(ms)" width="120" align="center" />
        <el-table-column prop="effectedRowCount" label="影响行数" width="100" align="center" />
      </el-table>

      <el-table v-else-if="activeTab === 'slow'" :data="slowSqls" border stripe max-height="560">
        <el-table-column prop="sql" label="慢 SQL" min-width="320" show-overflow-tooltip />
        <el-table-column prop="executeCount" label="执行次数" width="100" align="center" />
        <el-table-column prop="maxTimespan" label="最大耗时(ms)" width="120" align="center" />
        <el-table-column prop="lastSlowTime" label="最近发生" width="170" />
      </el-table>

      <el-table v-else :data="urls" border stripe max-height="560">
        <el-table-column prop="url" label="请求地址" min-width="280" show-overflow-tooltip />
        <el-table-column prop="requestCount" label="请求次数" width="100" align="center" />
        <el-table-column prop="totalTime" label="总耗时(ms)" width="110" align="center" />
        <el-table-column prop="maxTimespan" label="最大耗时(ms)" width="120" align="center" />
      </el-table>

      <div class="mt-4">
        <el-text type="info" size="small">
          连接池总体状态请查看
          <el-link type="primary" @click="router.push('/monitor/pool')">连接池监视</el-link>
        </el-text>
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

.flex-1 {
  flex: 1;
}
</style>
