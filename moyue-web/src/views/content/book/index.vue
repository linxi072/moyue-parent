<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="filter">
        <el-form-item label="书名">
          <el-input v-model="filter.title" placeholder="模糊匹配" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filter.status" clearable placeholder="全部" style="width: 130px">
            <el-option :value="0" label="连载中" />
            <el-option :value="1" label="已完结" />
            <el-option :value="2" label="已下架" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="title" label="书名" min-width="180" show-overflow-tooltip />
      <el-table-column prop="authorName" label="作者" width="120" />
      <el-table-column prop="wordCount" label="字数" width="100" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 2 ? 'info' : 'success'">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="160" />
      <el-table-column label="操作" min-width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="success" v-if="(row as BookVO).status !== 1" @click="onOnline(row as BookVO)">上架</el-button>
          <el-button link type="warning" v-if="(row as BookVO).status !== 2" @click="onOffline(row as BookVO)">下架</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="load"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageAdminBooks, onlineBook, offlineBook, type BookVO } from '@/api/content'

const rows = ref<BookVO[]>([])
const total = ref(0)
const loading = ref(false)
const filter = reactive<{ title?: string; status?: number }>({})
const query = reactive({ page: 1, size: 10, title: undefined as string | undefined, status: undefined as number | undefined })

const statusText = (s?: number) => ({ 0: '连载中', 1: '已完结', 2: '已下架' }[s ?? -1] ?? '未知')

async function load() {
  loading.value = true
  try {
    const r = await pageAdminBooks({ ...query, title: filter.title || undefined, status: filter.status })
    rows.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

async function onOnline(row: BookVO) {
  await onlineBook(row.id)
  ElMessage.success('已上架')
  load()
}
async function onOffline(row: BookVO) {
  await ElMessageBox.confirm('确认下架该作品？', '提示', { type: 'warning' })
  await offlineBook(row.id)
  ElMessage.success('已下架')
  load()
}

onMounted(load)
</script>
