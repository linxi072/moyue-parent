<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { dbTables, importTables } from '@/api/generator'

/** ⑮-a 导入表结构 —— 从 information_schema 读取未导入的业务表 */
const router = useRouter()
const loading = ref(false)
const tables = ref<any[]>([])
const selected = ref<string[]>([])
const query = reactive({ tableName: '' })

async function load() {
  loading.value = true
  try {
    tables.value = (await dbTables(query.tableName)) || []
  } finally {
    loading.value = false
  }
}

async function submitImport() {
  if (!selected.value.length) {
    ElMessage.warning('请先勾选要导入的表')
    return
  }
  await importTables(selected.value)
  ElMessage.success(`已导入 ${selected.value.length} 张表`)
  router.push('/tool/generator/config')
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="search-bar">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="表名称">
          <el-input v-model="query.tableName" clearable style="width: 220px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-loading="loading" shadow="never">
      <div class="table-toolbar">
        <span class="title">数据库表（已勾选 {{ selected.length }} 张）</span>
        <el-button type="primary" @click="submitImport">导入选中</el-button>
      </div>

      <el-table
        :data="tables"
        border
        stripe
        max-height="520"
        @selection-change="(rows: any[]) => (selected = rows.map((r) => r.tableName))"
      >
        <el-table-column type="selection" width="45" />
        <el-table-column prop="tableName" label="表名称" min-width="220" />
        <el-table-column prop="tableComment" label="表描述" min-width="220" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="170" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.title {
  font-weight: 600;
  font-size: 15px;
}
</style>
