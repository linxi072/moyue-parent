<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import { redisInfo, deleteRedisByPrefix, redisCommandStats } from '@/api/monitor'
import type { RedisInfoVO } from '@/api/types'

/**
 * ⑬ 缓存监控 —— Redis INFO / DBSIZE 聚合。
 * 清空库是高危动作，二次确认 + 明确文案，避免误操作打到生产。
 */
const loading = ref(false)
const info = ref<RedisInfoVO>({})
const commandStats = ref<Record<string, string>>({})
const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

async function load() {
  loading.value = true
  try {
    info.value = await redisInfo()
    commandStats.value = (await redisCommandStats()) || {}
    await nextTick()
    renderChart()
  } finally {
    loading.value = false
  }
}

function renderChart() {
  const entries = Object.entries(commandStats.value).slice(0, 10)
  if (!entries.length) return
  if (!chart && chartRef.value) {
    chart = echarts.init(chartRef.value)
  }
  chart?.setOption(
    {
      tooltip: { trigger: 'axis' },
      grid: { left: 60, right: 20, top: 30, bottom: 40 },
      xAxis: {
        type: 'category',
        data: entries.map(([k]) => k.replace('cmdstat_', '')),
        axisLabel: { rotate: 30 }
      },
      yAxis: { type: 'value', name: '调用次数' },
      series: [
        {
          type: 'bar',
          // INFO 的 commandstats 形如 "calls=123,usec=456"，这里取 calls
          data: entries.map(([, v]) => Number(/calls=(\d+)/.exec(v)?.[1] ?? 0)),
          itemStyle: { color: '#5b7fff' }
        }
      ]
    },
    true
  )
}

function resize() {
  chart?.resize()
}

async function handleClear() {
  const { value } = await ElMessageBox.prompt(
    '按前缀批量清理缓存（高危）。请输入前缀，如 moyue:book:',
    '高危操作',
    { inputValue: 'moyue:', inputPattern: /^\S+$/, inputErrorMessage: '前缀不能为空' }
  )
  if (!value) return
  const n = await deleteRedisByPrefix(value)
  ElMessage.success(`已清理 ${n} 个 key`)
  load()
}

const dbSizeList = ref<{ key: string; value: number }[]>([])

function buildDbSize() {
  const raw = info.value.dbSize || {}
  dbSizeList.value = Object.entries(raw).map(([key, value]) => ({ key, value }))
}

onMounted(async () => {
  await load()
  buildDbSize()
  window.addEventListener('resize', resize)
})

watch(
  () => info.value.dbSize,
  () => buildDbSize()
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="page-container">
    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="card-header">
          <span>Redis 概览</span>
          <div>
            <el-button link type="primary" @click="load">刷新</el-button>
            <el-button link type="danger" @click="handleClear">清空缓存</el-button>
          </div>
        </div>
      </template>

      <el-descriptions :column="4" border>
        <el-descriptions-item label="版本">{{ info.version || '-' }}</el-descriptions-item>
        <el-descriptions-item label="运行模式">{{ info.mode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="连接客户端数">{{ info.connectedClients ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="已用内存">{{ info.usedMemoryHuman || info.usedMemory || '-' }}</el-descriptions-item>
        <el-descriptions-item label="峰值内存">{{ info.peakMemoryHuman || '-' }}</el-descriptions-item>
        <el-descriptions-item label="运行天数">{{ info.uptimeInDays ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="QPS">{{ info.qps ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="Key 总数">{{ info.keyCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="命中率">{{ info.hitRate || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-row :gutter="16" class="mt-4">
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>命令统计 Top 10</template>
          <div ref="chartRef" style="height: 320px" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>各库 Key 数量</template>
          <el-table :data="dbSizeList" size="small" max-height="320">
            <el-table-column prop="key" label="库" />
            <el-table-column prop="value" label="Key 数" align="right" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="mt-4">
      <template #header>Redis INFO 原文</template>
      <el-input
        :model-value="info.info"
        type="textarea"
        :rows="10"
        readonly
        placeholder="后端未返回 info 原文"
      />
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
