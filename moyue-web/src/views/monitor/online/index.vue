<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePage } from '@/composables/usePage'
import { pageOnlineUsers, forceLogout, batchForceLogout, onlineCount } from '@/api/online'
import type { SysUserOnline } from '@/api/types'

/** ⑨ 在线用户域 —— 强退即删除 Redis 中的 token，被踢用户下一次请求即 401 */
const { loading, total, records, query, search, reset, handleSizeChange, handleCurrentChange } =
  usePage<SysUserOnline>(pageOnlineUsers, { username: '', ip: '' })

const count = ref(0)
const tableRef = ref()
const multipleSelection = ref<SysUserOnline[]>([])

async function loadCount() {
  try {
    count.value = await onlineCount()
  } catch {
    count.value = 0
  }
}

async function kick(row: SysUserOnline) {
  await ElMessageBox.confirm(`确认强退「${row.nickname || row.username}」？`, '提示', {
    type: 'warning'
  })
  await forceLogout(row.tokenId!)
  ElMessage.success('已强退')
  search()
  loadCount()
}

async function kickBatch() {
  const ids = multipleSelection.value.map((i) => i.tokenId!)
  if (!ids.length) {
    ElMessage.warning('请先勾选要强退的用户')
    return
  }
  await ElMessageBox.confirm(`确认强退选中的 ${ids.length} 个用户？`, '提示', { type: 'warning' })
  await batchForceLogout(ids)
  ElMessage.success('批量强退完成')
  search()
  loadCount()
}

function handleSelectionChange(rows: SysUserOnline[]) {
  multipleSelection.value = rows
}

onMounted(() => {
  reset()
  loadCount()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="search-bar">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="登录账号">
          <el-input v-model="query.username" clearable style="width: 160px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="登录 IP">
          <el-input v-model="query.ip" clearable style="width: 150px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset()">重置</el-button>
        </el-form-item>
        <el-form-item>
          <el-text type="info">当前在线：{{ count }}</el-text>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div class="table-toolbar">
        <span class="title">在线用户</span>
        <el-button type="danger" @click="kickBatch">批量强退</el-button>
      </div>

      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="records"
        border
        stripe
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="45" />
        <el-table-column prop="username" label="登录账号" width="140" />
        <el-table-column prop="nickname" label="昵称" width="140" />
        <el-table-column prop="ip" label="登录 IP" width="140" />
        <el-table-column prop="browser" label="浏览器" width="130" />
        <el-table-column prop="os" label="操作系统" width="130" />
        <el-table-column prop="loginTime" label="登录时间" width="170" />
        <el-table-column prop="lastAccessTime" label="最后访问" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="kick(row)">强退</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.title {
  font-weight: 600;
  font-size: 15px;
}
</style>
