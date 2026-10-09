<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { poolList, poolDatasources } from '@/api/monitor'
import type { DruidPoolVO } from '@/api/types'

/** ⑭ 连接池监视 —— Druid 数据源运行时指标 */
const loading = ref(false)
const pools = ref<DruidPoolVO[]>([])

const dsLoading = ref(false)
const datasources = ref<DruidPoolVO[]>([])

async function load() {
  loading.value = true
  try {
    pools.value = (await poolList()) || []
  } finally {
    loading.value = false
  }
}

async function loadDatasources() {
  dsLoading.value = true
  try {
    datasources.value = (await poolDatasources()) || []
  } catch {
    datasources.value = []
  } finally {
    dsLoading.value = false
  }
}

onMounted(() => {
  load()
  loadDatasources()
})
</script>

<template>
  <div class="page-container">
    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="card-header">
          <span>连接池状态</span>
          <el-button link type="primary" @click="load">刷新</el-button>
        </div>
      </template>

      <el-table :data="pools" border stripe>
        <el-table-column prop="name" label="数据源" min-width="180" />
        <el-table-column prop="activeCount" label="活跃连接" width="100" align="center" />
        <el-table-column prop="poolingCount" label="空闲连接" width="100" align="center" />
        <el-table-column prop="maxActive" label="最大连接" width="100" align="center" />
        <el-table-column label="使用率" min-width="200">
          <template #default="{ row }">
            <el-progress
              :percentage="row.maxActive ? Math.round((row.activeCount / row.maxActive) * 10000) / 100 : 0"
              :status="
                row.maxActive && row.activeCount / row.maxActive > 0.85 ? 'exception' : 'success'
              "
            />
          </template>
        </el-table-column>
        <el-table-column prop="connectCount" label="建立次数" width="100" align="center" />
        <el-table-column prop="closeCount" label="关闭次数" width="100" align="center" />
        <el-table-column prop="waitThreadCount" label="等待线程" width="100" align="center" />
        <el-table-column prop="logicConnectErrorCount" label="连接错误" width="100" align="center" />
      </el-table>
    </el-card>

    <el-card v-loading="dsLoading" shadow="never" class="mt-4">
      <template #header>
        <div class="card-header">
          <span>数据源清单</span>
          <el-button link type="primary" @click="loadDatasources">刷新</el-button>
        </div>
      </template>

      <el-table :data="datasources" border stripe>
        <el-table-column prop="name" label="数据源" min-width="160" />
        <el-table-column prop="url" label="JDBC URL" min-width="300" show-overflow-tooltip />
        <el-table-column prop="driverClassName" label="驱动类" min-width="200" show-overflow-tooltip />
        <el-table-column prop="activeCount" label="活跃连接" width="100" align="center" />
        <el-table-column prop="poolingCount" label="空闲连接" width="100" align="center" />
        <el-table-column prop="maxActive" label="最大连接" width="100" align="center" />
        <el-table-column prop="waitThreadCount" label="等待线程" width="100" align="center" />
      </el-table>
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
