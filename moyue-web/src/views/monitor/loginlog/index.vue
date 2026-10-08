<script setup lang="ts">
import { onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePage } from '@/composables/usePage'
import {
  pageLoginLogs,
  deleteLoginLog,
  clearLoginLogs,
  unlockAccount
} from '@/api/loginlog'
import type { SysLoginLog } from '@/api/types'
import { downloadFile } from '@/utils/download'
import { STATUS_OPTIONS, labelOf, tagOf } from '@/utils/enums'

/** ⑧ 登录日志域 —— 失败计数达到阈值会锁账号，这里提供「解锁」运维动作 */
const { loading, total, records, query, search, reset, handleSizeChange, handleCurrentChange } =
  usePage<SysLoginLog>(pageLoginLogs, { username: '', ip: '', status: undefined })

async function handleDelete(row: SysLoginLog) {
  await ElMessageBox.confirm('确认删除该条登录日志？', '提示', { type: 'warning' })
  await deleteLoginLog(row.id!)
  ElMessage.success('已删除')
  search()
}

async function handleClear() {
  await ElMessageBox.confirm('确认清空全部登录日志？该操作不可恢复', '警告', { type: 'warning' })
  await clearLoginLogs()
  ElMessage.success('已清空')
  search()
}

async function handleUnlock(row: SysLoginLog) {
  if (!row.username) return
  await ElMessageBox.confirm(`确认解锁账号「${row.username}」？`, '提示', { type: 'warning' })
  await unlockAccount(row.username)
  ElMessage.success('已解锁')
}

function handleExport() {
  downloadFile('/admin/system/logs/login/export', 'login-log.csv')
}

onMounted(() => reset())
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
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 110px">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset()">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div class="table-toolbar">
        <span class="title">登录日志</span>
        <div>
          <el-button @click="handleExport">导出 CSV</el-button>
          <el-button type="danger" @click="handleClear">清空</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="username" label="登录账号" width="150" />
        <el-table-column prop="ip" label="登录 IP" width="140" />
        <el-table-column prop="location" label="登录地点" min-width="140" />
        <el-table-column prop="browser" label="浏览器" width="130" />
        <el-table-column prop="os" label="操作系统" width="130" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(STATUS_OPTIONS, row.status)">
              {{ labelOf(STATUS_OPTIONS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="提示消息" min-width="140" show-overflow-tooltip />
        <el-table-column prop="loginTime" label="登录时间" width="170" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="warning" @click="handleUnlock(row)">解锁</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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
