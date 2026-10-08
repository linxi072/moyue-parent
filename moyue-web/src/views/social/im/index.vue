<template>
  <div class="page-container">
    <div class="table-toolbar"><span class="toolbar-title">会话管理</span></div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag>{{ (row as ImConversationVO).type === 2 ? '群聊' : '单聊' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="群名" min-width="140" show-overflow-tooltip />
      <el-table-column prop="ownerId" label="创建者" width="100" />
      <el-table-column label="成员" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ (row as ImConversationVO).memberIds?.join(', ') || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="(row as ImConversationVO).disabled === 1 ? 'info' : 'success'">
            {{ (row as ImConversationVO).disabled === 1 ? '已禁用' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastMessageTime" label="最近消息" min-width="160" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="onView(row as ImConversationVO)">查看消息</el-button>
          <el-button link type="danger" v-if="(row as ImConversationVO).disabled !== 1" @click="onDisable(row as ImConversationVO)">禁用</el-button>
          <el-button link type="success" v-if="(row as ImConversationVO).disabled === 1" @click="onEnable(row as ImConversationVO)">启用</el-button>
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

    <el-dialog v-model="dialog" title="会话消息" width="min(640px, 94vw)">
      <el-table :data="messages" v-loading="msgLoading" border stripe max-height="420">
        <el-table-column prop="senderId" label="发送者" width="100" />
        <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createTime" label="时间" min-width="160" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listAdminConversations,
  listAdminMessages,
  disableConversation,
  type ImConversationVO,
  type ImMessageVO
} from '@/api/social'

const rows = ref<ImConversationVO[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ page: 1, size: 10 })

const dialog = ref(false)
const messages = ref<ImMessageVO[]>([])
const msgLoading = ref(false)

async function load() {
  loading.value = true
  try {
    const r = await listAdminConversations({ ...query })
    rows.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

async function onView(row: ImConversationVO) {
  dialog.value = true
  msgLoading.value = true
  try {
    messages.value = await listAdminMessages(row.id, 50)
  } finally {
    msgLoading.value = false
  }
}

async function onDisable(row: ImConversationVO) {
  await ElMessageBox.confirm('确认禁用该会话？', '提示', { type: 'warning' })
  await disableConversation(row.id, 1)
  ElMessage.success('已禁用')
  load()
}

async function onEnable(row: ImConversationVO) {
  await disableConversation(row.id, 0)
  ElMessage.success('已启用')
  load()
}

onMounted(load)
</script>
