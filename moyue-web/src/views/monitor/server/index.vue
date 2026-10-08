<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { serverInfo } from '@/api/monitor'
import type { ServerVO } from '@/api/types'

/** ⑫ 服务监控 —— 数据来自 Actuator + oshi（后端聚合），只读 */
const router = useRouter()
const loading = ref(false)
const info = ref<ServerVO>({})

async function load() {
  loading.value = true
  try {
    info.value = await serverInfo()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="card-header">
          <span>服务监控概览</span>
          <el-button type="primary" link @click="load">刷新</el-button>
        </div>
      </template>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-card shadow="never">
            <template #header>CPU</template>
            <div class="metric">
              <span>核心数</span><span>{{ info.cpu?.cpuNum ?? '-' }}</span>
            </div>
            <div class="metric">
              <span>系统使用率</span>
              <el-progress :percentage="Math.round((info.cpu?.sys ?? 0) * 100) / 100" />
            </div>
            <div class="metric">
              <span>用户使用率</span>
              <el-progress :percentage="Math.round((info.cpu?.used ?? 0) * 100) / 100" status="success" />
            </div>
            <div class="metric">
              <span>空闲率</span>
              <el-progress :percentage="Math.round((info.cpu?.free ?? 0) * 100) / 100" />
            </div>
          </el-card>
        </el-col>

        <el-col :span="12">
          <el-card shadow="never">
            <template #header>内存</template>
            <div class="metric">
              <span>总内存</span><span>{{ info.mem?.total ?? '-' }} GB</span>
            </div>
            <div class="metric">
              <span>已用</span><span>{{ info.mem?.used ?? '-' }} GB</span>
            </div>
            <div class="metric">
              <span>剩余</span><span>{{ info.mem?.free ?? '-' }} GB</span>
            </div>
            <div class="metric">
              <span>使用率</span>
              <el-progress
                :percentage="Math.round((info.mem?.usage ?? 0) * 100) / 100"
                :status="(info.mem?.usage ?? 0) > 85 ? 'exception' : 'success'"
              />
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="mt-4">
        <el-col :span="12">
          <el-card shadow="never">
            <template #header>
              <div class="card-header">
                <span>JVM</span>
                <el-button link type="primary" @click="router.push('/monitor/server/jvm')">
                  详情
                </el-button>
              </div>
            </template>
            <div class="metric"><span>名称</span><span>{{ info.jvm?.name ?? '-' }}</span></div>
            <div class="metric"><span>版本</span><span>{{ info.jvm?.version ?? '-' }}</span></div>
            <div class="metric"><span>已用内存</span><span>{{ info.jvm?.used ?? '-' }} MB</span></div>
            <div class="metric">
              <span>使用率</span>
              <el-progress :percentage="Math.round((info.jvm?.usage ?? 0) * 100) / 100" />
            </div>
            <div class="metric"><span>运行时长</span><span>{{ info.jvm?.runTime ?? '-' }}</span></div>
          </el-card>
        </el-col>

        <el-col :span="12">
          <el-card shadow="never">
            <template #header>
              <div class="card-header">
                <span>服务器信息</span>
                <el-button link type="primary" @click="router.push('/monitor/server/disk')">
                  磁盘状态
                </el-button>
              </div>
            </template>
            <div class="metric"><span>主机名</span><span>{{ info.sys?.computerName ?? '-' }}</span></div>
            <div class="metric"><span>操作系统</span><span>{{ info.sys?.osName ?? '-' }}</span></div>
            <div class="metric"><span>系统架构</span><span>{{ info.sys?.osArch ?? '-' }}</span></div>
            <div class="metric"><span>服务器 IP</span><span>{{ info.sys?.computerIp ?? '-' }}</span></div>
            <div class="metric"><span>工作目录</span><span>{{ info.sys?.userDir ?? '-' }}</span></div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.metric {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px dashed #ebeef5;
  font-size: 14px;
}

.metric > span:first-child {
  color: #606266;
  min-width: 90px;
}
</style>
