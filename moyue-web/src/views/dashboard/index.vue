<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { onlineCount } from '@/api/online'
import { operLogStats } from '@/api/operlog'
import type { OperLogStats } from '@/api/operlog'
import { serverInfo } from '@/api/monitor'
import { useAppStore } from '@/stores/app'

const online = ref(0)
const stats = ref<OperLogStats>({ total: 0, failTotal: 0, byBusinessType: {} })
const server = ref<any>({})
const appStore = useAppStore()
// 服务概览描述列表：移动端单列，桌面双列
const descColumn = computed(() => (appStore.isMobile ? 1 : 2))

onMounted(async () => {
  try {
    online.value = await onlineCount()
  } catch (e) {
    online.value = 0
  }
  try {
    stats.value = await operLogStats()
  } catch (e) {
    /* 后端未就绪时保持 0 */
  }
  try {
    server.value = await serverInfo()
  } catch (e) {
    /* 同上 */
  }
})
</script>

<template>
  <div class="page-container">
    <el-row :gutter="16">
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="never">
          <div class="stat">
            <div class="label">在线用户</div>
            <div class="value">{{ online }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="never">
          <div class="stat">
            <div class="label">日志总量</div>
            <div class="value">{{ stats.total ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="never">
          <div class="stat">
            <div class="label">失败操作</div>
            <div class="value danger">{{ stats.failTotal ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
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

    <el-card shadow="never" class="mt-4">
      <template #header>服务概览</template>
      <el-descriptions :column="descColumn" border>
        <el-descriptions-item label="服务器名称">{{ server.sys?.computerName }}</el-descriptions-item>
        <el-descriptions-item label="操作系统">{{ server.sys?.osName }}</el-descriptions-item>
        <el-descriptions-item label="服务器 IP">{{ server.sys?.computerIp }}</el-descriptions-item>
        <el-descriptions-item label="CPU 核数">{{ server.cpu?.cpuNum }}</el-descriptions-item>
        <el-descriptions-item label="内存已用">{{ server.mem?.used }} GB</el-descriptions-item>
        <el-descriptions-item label="内存总计">{{ server.mem?.total }} GB</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.stat {
  .label {
    color: #909399;
    font-size: 13px;
  }

  .value {
    font-size: 26px;
    font-weight: 600;
    margin-top: 6px;

    &.danger {
      color: #f56c6c;
    }
  }
}
</style>
