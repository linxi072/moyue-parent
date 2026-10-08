<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { previewGenCode } from '@/api/generator'

/** ⑮-b 代码预览 —— 后端返回 { 文件名: 代码文本 }，按文件分 Tab 展示 */
const route = useRoute()
const tableId = ref<number>(Number(route.query.tableId) || 0)
const tableName = ref<string>((route.query.tableName as string) || '')

const loading = ref(false)
const filesMap = ref<Record<string, string>>({})
const activeFile = ref('')
const fileNames = computed(() => Object.keys(filesMap.value))

async function load() {
  if (!tableId.value) return
  loading.value = true
  try {
    filesMap.value = (await previewGenCode(tableId.value)) || {}
    activeFile.value = fileNames.value[0] || ''
  } finally {
    loading.value = false
  }
}

function copyCode() {
  const text = filesMap.value[activeFile.value] || ''
  navigator.clipboard?.writeText(text)
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="card-header">
          <span>代码预览{{ tableName ? ` · ${tableName}` : '' }}</span>
          <el-button link type="primary" @click="copyCode">复制当前文件</el-button>
        </div>
      </template>

      <el-empty v-if="!fileNames.length" description="暂无可预览的代码，请先完善生成配置" />

      <template v-else>
        <el-tabs v-model="activeFile" class="code-tabs">
          <el-tab-pane v-for="name in fileNames" :key="name" :label="name" :name="name" />
        </el-tabs>
        <pre class="code-block">{{ filesMap[activeFile] }}</pre>
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.code-tabs {
  margin-bottom: 12px;
}

.code-block {
  background: #1f2430;
  color: #d7dae0;
  padding: 16px;
  border-radius: 4px;
  overflow: auto;
  max-height: calc(100vh - 320px);
  font-size: 13px;
  line-height: 1.6;
  margin: 0;
}
</style>
