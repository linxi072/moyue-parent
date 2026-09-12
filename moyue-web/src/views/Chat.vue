<template>
  <div class="chat-page">
    <el-card shadow="never" class="chat-panel">
      <div class="chat-body">
        <aside class="conv-side">
          <div class="side-head">
            <span class="panel-title">会话</span>
            <el-button type="primary" size="small" @click="openCreate">新建</el-button>
          </div>

          <el-alert
            v-if="errorTip"
            class="side-error"
            type="error"
            show-icon
            :closable="false"
            :title="errorTip"
          />

          <div v-loading="convLoading" class="conv-list">
            <div
              v-for="c in conversations"
              :key="c.id"
              class="conv-item"
              :class="{ active: currentId === c.id }"
              @click="selectConversation(c.id)"
            >
              <div class="conv-title">
                {{ convTitle(c) }}
                <el-tag size="small" :type="c.type === 2 ? 'warning' : 'info'">
                  {{ CONVERSATION_TYPE_TEXT[c.type] || '未知' }}
                </el-tag>
              </div>
              <div class="conv-preview">{{ c.lastMessagePreview || '暂无消息' }}</div>
            </div>
          </div>

          <EmptyState v-if="!convLoading && !errorTip && conversations.length === 0" mark="聊" title="还没有会话" />
        </aside>

        <section class="msg-main">
          <div v-if="currentId === null" class="msg-empty">
            <EmptyState mark="聊" title="从左侧选择一个会话开始聊天" />
          </div>

          <template v-else>
            <div class="msg-head">
              <span class="panel-title">{{ currentTitle }}</span>
              <span class="poll-tip">每 3 秒自动刷新</span>
            </div>

            <div v-loading="msgLoading" class="msg-list" ref="msgListRef">
              <div
                v-for="m in messages"
                :key="m.id"
                class="msg-row"
                :class="{ mine: m.senderId === myId }"
              >
                <div class="msg-bubble">
                  <div class="msg-sender">
                    {{ m.senderId === myId ? '我' : m.senderName || `用户${m.senderId}` }}
                  </div>
                  <div class="msg-content">{{ m.content }}</div>
                  <div class="msg-time">{{ formatTime(m.createTime) }}</div>
                </div>
              </div>

              <EmptyState v-if="!msgLoading && messages.length === 0" mark="聊" title="还没有消息，发送第一条吧" />
            </div>

            <div class="msg-input">
              <el-input
                v-model="draft"
                type="textarea"
                :rows="2"
                placeholder="输入消息，回车发送"
                resize="none"
                @keydown.enter.exact.prevent="handleSend"
              />
              <div class="input-actions">
                <el-button type="primary" size="small" :loading="sending" @click="handleSend">
                  发送
                </el-button>
              </div>
            </div>
          </template>
        </section>
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新建会话" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="类型" required>
          <el-select v-model="form.type" style="width: 100%">
            <el-option
              v-for="opt in typeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.type === 2" label="群名称" required>
          <el-input v-model="form.title" placeholder="请输入群聊名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="成员 ID" required>
          <el-input v-model="form.memberIds" placeholder="多个成员 ID 用英文逗号分隔，如 1001,1002" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { formatTime } from '@/api/types';
import {
  CONVERSATION_TYPE_TEXT,
  createConversation,
  listConversations,
  listMessages,
  sendMessage,
} from '@/api/im';
import type { ConversationDTO, MessageDTO } from '@/api/im';

import EmptyState from '@/components/EmptyState.vue';
const userStore = useUserStore();

/** 后端 WebSocket 不经网关（技术债 16-4），这里用 REST 轮询替代推送 */
const POLL_INTERVAL = 3000;

const conversations = ref<ConversationDTO[]>([]);
const convLoading = ref(false);
const errorTip = ref('');

const messages = ref<MessageDTO[]>([]);
const msgLoading = ref(false);
const msgListRef = ref<HTMLElement | null>(null);

const currentId = ref<number | null>(null);
const draft = ref('');
const sending = ref(false);

const dialogVisible = ref(false);
const submitting = ref(false);

interface ConvForm {
  type: number;
  title: string;
  memberIds: string;
}

const form = reactive<ConvForm>({
  type: 1,
  title: '',
  memberIds: '',
});

const typeOptions: { label: string; value: number }[] = [
  { label: '单聊（1）', value: 1 },
  { label: '群聊（2）', value: 2 },
];

let pollTimer: ReturnType<typeof setInterval> | null = null;

function currentUserId(): number {
  const info = userStore.userInfo;
  const id = info ? Number(info.id) : 0;
  return Number.isFinite(id) ? id : 0;
}

const myId = computed(() => currentUserId());

const currentTitle = computed(() => {
  const hit = conversations.value.find((c) => c.id === currentId.value);
  return hit ? convTitle(hit) : '会话';
});

function convTitle(conv: ConversationDTO): string {
  if (conv.title) {
    return conv.title;
  }
  const ids = conv.memberIds || [];
  const others = ids.filter((id) => id !== myId.value);
  return others.length > 0 ? `用户 ${others.join('、')}` : `会话 ${conv.id}`;
}

/** 逗号分隔字符串 → 数字数组，非法片段直接丢弃 */
function parseMemberIds(raw: string): number[] {
  return raw
    .split(/[,，\s]+/)
    .map((s) => s.trim())
    .filter((s) => s !== '')
    .map((s) => Number(s))
    .filter((n) => Number.isFinite(n) && n > 0);
}

async function loadConversations() {
  convLoading.value = true;
  errorTip.value = '';
  try {
    const res = await listConversations(myId.value, 1, 50);
    conversations.value = (res && res.records) || [];
  } catch (err) {
    conversations.value = [];
    const detail = err instanceof Error && err.message ? `（${err.message}）` : '';
    errorTip.value = `会话列表加载失败${detail}`;
  } finally {
    convLoading.value = false;
  }
}

async function scrollToBottom() {
  await nextTick();
  const el = msgListRef.value;
  if (el) {
    el.scrollTop = el.scrollHeight;
  }
}

let lastMessageCount = 0;

async function loadMessages(forceScroll: boolean) {
  const id = currentId.value;
  if (id === null) {
    return;
  }
  // 轮询时不再置 loading，避免消息列表每 3 秒抖动一次
  if (forceScroll) {
    msgLoading.value = true;
  }
  try {
    const res = await listMessages(id, 1, 100);
    messages.value = (res && res.records) || [];
    const changed = messages.value.length !== lastMessageCount;
    lastMessageCount = messages.value.length;
    if (forceScroll || changed) {
      await scrollToBottom();
    }
  } catch (err) {
    if (forceScroll) {
      ElMessage.error(`消息加载失败：${err instanceof Error ? err.message : '未知错误'}`);
    }
  } finally {
    if (forceScroll) {
      msgLoading.value = false;
    }
  }
}

function stopPolling() {
  if (pollTimer !== null) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

function startPolling() {
  stopPolling();
  pollTimer = setInterval(() => {
    loadMessages(false);
  }, POLL_INTERVAL);
}

async function selectConversation(id: number) {
  currentId.value = id;
  messages.value = [];
  lastMessageCount = 0;
  await loadMessages(true);
  startPolling();
}

async function handleSend() {
  const content = draft.value.trim();
  if (!content) {
    ElMessage.warning('请输入消息内容');
    return;
  }
  const id = currentId.value;
  if (id === null) {
    return;
  }
  sending.value = true;
  try {
    await sendMessage(id, { senderId: myId.value, content, type: 1 });
    draft.value = '';
    await loadMessages(true);
    await loadConversations();
  } catch (err) {
    ElMessage.error(`发送失败：${err instanceof Error ? err.message : '未知错误'}`);
  } finally {
    sending.value = false;
  }
}

function openCreate() {
  form.type = 1;
  form.title = '';
  form.memberIds = '';
  dialogVisible.value = true;
}

async function handleCreate() {
  const memberIds = parseMemberIds(form.memberIds);
  if (memberIds.length === 0) {
    ElMessage.warning('请填写至少一个成员 ID');
    return;
  }
  if (form.type === 2 && !form.title.trim()) {
    ElMessage.warning('请填写群聊名称');
    return;
  }
  submitting.value = true;
  try {
    const conv = await createConversation({
      type: form.type,
      // 后端 CreateConversationRequest 需要 ownerId（创建人 / 群主）
      ownerId: myId.value,
      title: form.type === 2 ? form.title.trim() : undefined,
      memberIds,
    });
    ElMessage.success('会话已创建');
    dialogVisible.value = false;
    await loadConversations();
    if (conv && conv.id) {
      await selectConversation(conv.id);
    }
  } catch (err) {
    ElMessage.error(`创建失败：${err instanceof Error ? err.message : '未知错误'}`);
  } finally {
    submitting.value = false;
  }
}

onMounted(loadConversations);

onUnmounted(() => {
  stopPolling();
});
</script>

<style scoped>
.chat-page {
  padding: 16px;
}
.chat-panel {
  border-radius: var(--moyue-radius);
}
.chat-body {
  display: flex;
  height: 560px;
}
.conv-side {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid rgba(38, 34, 30, 0.08);
}
.side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(38, 34, 30, 0.08);
}
.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--moyue-ink);
}
.side-error {
  margin-top: 12px;
}
.conv-list {
  flex: 1;
  overflow-y: auto;
  margin-top: 8px;
}
.conv-item {
  padding: 10px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
}
.conv-item:hover {
  background: var(--moyue-paper);
}
.conv-item.active {
  background: rgba(201, 56, 46, 0.08);
}
.conv-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  font-size: 14px;
  color: var(--moyue-ink);
}
.conv-preview {
  margin-top: 4px;
  font-size: 12px;
  color: var(--moyue-ink);
  opacity: 0.5;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.msg-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding-left: 16px;
}
.msg-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.msg-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(38, 34, 30, 0.08);
}
.poll-tip {
  font-size: 12px;
  color: var(--moyue-ink);
  opacity: 0.4;
}
.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px 0;
}
.msg-row {
  display: flex;
  margin-bottom: 12px;
}
.msg-row.mine {
  justify-content: flex-end;
}
.msg-bubble {
  max-width: 70%;
  padding: 8px 12px;
  border-radius: var(--moyue-radius);
  background: var(--moyue-paper);
}
.msg-row.mine .msg-bubble {
  background: var(--moyue-crimson);
  color: #fff;
}
.msg-sender {
  font-size: 12px;
  opacity: 0.65;
  margin-bottom: 2px;
}
.msg-content {
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.msg-time {
  margin-top: 4px;
  font-size: 11px;
  opacity: 0.45;
}
.msg-input {
  border-top: 1px solid rgba(38, 34, 30, 0.08);
  padding-top: 12px;
}
.input-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
