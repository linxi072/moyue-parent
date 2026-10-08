<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { redisKeyList, redisKeyValue, deleteRedisKey } from '@/api/monitor'

/** ⑬-a 缓存键浏览 —— 生产环境建议限制 limit，避免 SCAN 大库 */
const loading = ref(false)
const keys = ref<string[]>([])
const prefix = ref('moyue:')
const limit = ref(100)

const valueVisible = ref(false)
const valueInfo = reactive({ key: '', type: '', value: '', ttlSeconds: -1 })
const total = ref(0)

async function search() {
  loading.value = true
  try {
    const res = await redisKeyList(prefix.value, limit.value)
    keys.value = res?.keys || []
    total.value = res?.total ?? keys.value.length
  } finally {
    loading.value = false
  }
}

async function view(key: string) {
  const res = await redisKeyValue(key)
  Object.assign(valueInfo, res)
  valueVisible.value = true
}

async function remove(key: string) {
  await ElMessageBox.confirm(`确认删除缓存键「${key}」？`, '提示', { type: 'warning' })
  await deleteRedisKey(key)
  ElMessage.success('已删除')
  search()
}

onMounted(search)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="search-bar">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="Key 前缀">
          <el-input v-model="prefix" placeholder="如 moyue:book:" style="width: 240px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="最大条数">
          <el-input-number v-model="limit" :min="10" :max="2000" :step="10" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-loading="loading" shadow="never">
      <template #header>
        缓存键列表（命中 {{ total }} 个，当前展示 {{ keys.length }} 个）
      </template>
      <el-table :data="keys.map((k) => ({ key: k }))" border stripe max-height="520">
        <el-table-column prop="key" label="Key" min-width="360" show-overflow-tooltip />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="view(row.key)">查看</el-button>
            <el-button link type="danger" @click="remove(row.key)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="valueVisible" title="缓存详情" width="640px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="Key">{{ valueInfo.key }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ valueInfo.type }}</el-descriptions-item>
        <el-descriptions-item label="TTL(秒)">{{ valueInfo.ttlSeconds }}</el-descriptions-item>
      </el-descriptions>
      <el-input
        :model-value="valueInfo.value"
        type="textarea"
        :rows="8"
        readonly
        class="mt-2"
      />
    </el-dialog>
  </div>
</template>
