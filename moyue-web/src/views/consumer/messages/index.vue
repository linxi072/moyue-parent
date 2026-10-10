<template>
  <div class="consumer-page">
    <div class="bar">
      <span class="title">收件箱</span>
      <el-button link type="primary" :disabled="!unreadCount" @click="onReadAll">全部已读</el-button>
    </div>

    <el-tabs v-model="seg" @tab-change="onSeg">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="未读" name="unread" />
      <el-tab-pane label="已读" name="read" />
    </el-tabs>

    <div v-loading="loading" class="list">
      <div
        v-for="m in rows"
        :key="m.id"
        class="msg"
        :class="{ unread: m.readFlag !== 1 }"
        @click="onOpen(m)"
      >
        <div class="msg-head">
          <span class="dot" v-if="m.readFlag !== 1" />
          <span class="msg-title">{{ m.title || '（无标题）' }}</span>
          <span class="msg-time">{{ m.createTime }}</span>
        </div>
        <div class="msg-content">{{ m.content || '（无正文）' }}</div>
        <div class="msg-foot">
          <el-tag size="small" :type="typeTag(m.type)">{{ typeText(m.type) }}</el-tag>
          <el-button
            v-if="m.readFlag !== 1"
            link
            type="primary"
            size="small"
            @click.stop="onRead([m.id])"
          >标记已读</el-button>
        </div>
      </div>
      <el-empty v-if="!loading && !rows.length" description="暂无消息" />
    </div>

    <div class="pager">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20]"
        layout="total, prev, pager, next"
        @current-change="load"
        @size-change="load"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  pageMyMessages,
  myUnreadCount,
  readMessages,
  readAllMessages,
  type MessageVO
} from '@/api/message'

// 收件箱：用户只读自己的消息（后端按 userId 强制归属），可单条/全部标记已读。
const rows = ref<MessageVO[]>([])
const total = ref(0)
const loading = ref(false)
const unreadCount = ref(0)
const seg = ref('all')
const filter = reactive({ readFlag: undefined as number | undefined })
const query = reactive({ page: 1, size: 10, readFlag: undefined as number | undefined })

const typeText = (t?: number) => ({ 1: '系统', 2: '活动', 3: '私信' }[t ?? 1] ?? '系统')
const typeTag = (t?: number) => (t === 2 ? 'warning' : t === 3 ? 'info' : 'success')

function readFlagOf(s: string) {
  return s === 'unread' ? 0 : s === 'read' ? 1 : undefined
}

function onSeg() {
  filter.readFlag = readFlagOf(seg.value)
  query.page = 1
  load()
}

async function load() {
  loading.value = true
  query.readFlag = filter.readFlag
  try {
    const [r, u] = await Promise.all([pageMyMessages(query), myUnreadCount()])
    rows.value = r.records
    total.value = r.total
    unreadCount.value = u
  } finally {
    loading.value = false
  }
}

async function onRead(ids: number[]) {
  await readMessages(ids)
  ElMessage.success('已标记为已读')
  load()
}

async function onReadAll() {
  await readAllMessages()
  ElMessage.success('已全部标记已读')
  load()
}

function onOpen(m: MessageVO) {
  if (m.readFlag !== 1) onRead([m.id])
}

onMounted(load)
</script>

<style scoped lang="scss">
.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  .title { font-size: 16px; font-weight: 600; color: var(--color-text); }
}
.list { display: flex; flex-direction: column; gap: 10px; margin-top: 6px; }
.msg {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 12px;
  cursor: pointer;
  &.unread { border-left: 3px solid var(--color-primary); }
  .msg-head { display: flex; align-items: center; gap: 6px; }
  .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--color-primary); flex-shrink: 0; }
  .msg-title { flex: 1; font-weight: 600; color: var(--color-text); }
  .msg-time { font-size: 12px; color: var(--color-text-muted); }
  .msg-content {
    margin: 6px 0;
    font-size: 13px;
    color: var(--color-text-secondary);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  .msg-foot { display: flex; align-items: center; justify-content: space-between; }
}
.pager { display: flex; justify-content: center; margin-top: 12px; }
</style>
