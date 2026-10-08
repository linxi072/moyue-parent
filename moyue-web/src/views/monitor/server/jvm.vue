<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { jvmInfo } from '@/api/monitor'

/** ⑫-a JVM 详情 */
const loading = ref(false)
const jvm = ref<any>({})

async function load() {
  loading.value = true
  try {
    jvm.value = await jvmInfo()
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
          <span>JVM 信息</span>
          <el-button link type="primary" @click="load">刷新</el-button>
        </div>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="JVM 名称">{{ jvm.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Java 版本">{{ jvm.version || '-' }}</el-descriptions-item>
        <el-descriptions-item label="安装路径" :span="2">{{ jvm.home || '-' }}</el-descriptions-item>
        <el-descriptions-item label="总内存">{{ jvm.total ?? '-' }} MB</el-descriptions-item>
        <el-descriptions-item label="已用内存">{{ jvm.used ?? '-' }} MB</el-descriptions-item>
        <el-descriptions-item label="剩余内存">{{ jvm.free ?? '-' }} MB</el-descriptions-item>
        <el-descriptions-item label="内存使用率">
          <el-progress
            :percentage="Math.round((jvm.usage ?? 0) * 100) / 100"
            :status="(jvm.usage ?? 0) > 85 ? 'exception' : 'success'"
            style="width: 180px"
          />
        </el-descriptions-item>
        <el-descriptions-item label="启动时间">{{ jvm.startTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="运行时长">{{ jvm.runTime || '-' }}</el-descriptions-item>
      </el-descriptions>
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
