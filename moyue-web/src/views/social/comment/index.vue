<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="query">
        <el-form-item label="作品 ID">
          <el-input v-model.number="query.bookId" placeholder="bookId" style="width: 160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 130px">
            <el-option :value="0" label="正常" />
            <el-option :value="1" label="待审核" />
            <el-option :value="2" label="已下架" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-toolbar"><span class="toolbar-title">评论管理</span></div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="bookId" label="作品" width="90" />
      <el-table-column prop="userId" label="用户" width="90" />
      <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="置顶" width="80">
        <template #default="{ row }">
          <el-tag v-if="(row as CommentVO).top === 1" type="warning">置顶</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="likeCount" label="点赞" width="80" />
      <el-table-column prop="createTime" label="时间" min-width="160" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="warning" @click="onTop(row as CommentVO)">
            {{ (row as CommentVO).top === 1 ? '取消置顶' : '置顶' }}
          </el-button>
          <el-button link type="danger" v-if="(row as CommentVO).status !== 2" @click="onAudit(row as CommentVO)">下架</el-button>
          <el-button link type="success" v-if="(row as CommentVO).status === 2" @click="onRecover(row as CommentVO)">恢复</el-button>
          <el-button link type="danger" @click="onDelete(row as CommentVO)">删除</el-button>
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
import {
  pageAdminComments,
  deleteAdminComment,
  topAdminComment,
  auditAdminComment,
  type CommentVO
} from '@/api/social'

const rows = ref<CommentVO[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ page: 1, size: 10, bookId: undefined as number | undefined, status: undefined as number | undefined })

const statusText = (s?: number) => ({ 0: '正常', 1: '待审核', 2: '已下架' }[s ?? 0] ?? '未知')
const statusType = (s?: number) => (s === 2 ? 'info' : s === 1 ? 'warning' : 'success')

async function load() {
  loading.value = true
  try {
    const r = await pageAdminComments({ ...query })
    rows.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

async function onTop(row: CommentVO) {
  const top = row.top === 1 ? 0 : 1
  await topAdminComment(row.id, top)
  ElMessage.success(top === 1 ? '已置顶' : '已取消置顶')
  load()
}

async function onAudit(row: CommentVO) {
  await ElMessageBox.confirm('确认下架该评论？', '提示', { type: 'warning' })
  await auditAdminComment(row.id, 2)
  ElMessage.success('已下架')
  load()
}

async function onRecover(row: CommentVO) {
  await auditAdminComment(row.id, 0)
  ElMessage.success('已恢复')
  load()
}

async function onDelete(row: CommentVO) {
  await ElMessageBox.confirm('确认删除该评论？', '提示', { type: 'warning' })
  await deleteAdminComment(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
