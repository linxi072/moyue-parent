<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="作品名">
          <el-input v-model="query.bookTitle" placeholder="模糊搜索" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-toolbar"><span class="toolbar-title">用户书架</span></div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="bookId" label="作品 ID" width="100" />
      <el-table-column prop="bookTitle" label="作品名" min-width="180" />
      <el-table-column prop="userId" label="所属用户 ID" width="140" />
      <el-table-column prop="lastChapterNo" label="最近章节" width="100" />
      <el-table-column prop="createTime" label="收藏时间" min-width="160" />
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
import { pageAdminShelf, type BookShelfVO } from '@/api/content'

const rows = ref<BookShelfVO[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ page: 1, size: 10, bookTitle: '' })

async function load() {
  loading.value = true
  try {
    const r = await pageAdminShelf({ ...query })
    rows.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
