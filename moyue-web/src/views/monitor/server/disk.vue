<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { diskInfo } from '@/api/monitor'

/** ⑫-b 磁盘状态 */
const loading = ref(false)
const disks = ref<any[]>([])

async function load() {
  loading.value = true
  try {
    disks.value = (await diskInfo()) || []
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
          <span>磁盘状态</span>
          <el-button link type="primary" @click="load">刷新</el-button>
        </div>
      </template>

      <el-table :data="disks" border stripe>
        <el-table-column prop="name" label="盘符路径" min-width="140" />
        <el-table-column prop="total" label="总大小" width="120" />
        <el-table-column prop="used" label="已用" width="120" />
        <el-table-column prop="free" label="可用" width="120" />
        <el-table-column label="使用率" min-width="200">
          <template #default="{ row }">
            <el-progress
              :percentage="Math.round((row.usage ?? 0) * 100) / 100"
              :status="(row.usage ?? 0) > 90 ? 'exception' : 'success'"
            />
          </template>
        </el-table-column>
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
