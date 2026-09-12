<template>
  <div class="messages">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel-header">
          <span class="panel-title">消息中心</span>
          <div class="header-actions">
            <el-select v-model="typeFilter" class="type-select" placeholder="全部类型" clearable size="small">
              <el-option label="系统" :value="1" />
              <el-option label="互动" :value="2" />
              <el-option label="公告" :value="3" />
            </el-select>
            <el-button type="primary" size="small" @click="openSend">发消息</el-button>
            <el-button size="small" :loading="loading" :disabled="!userId" @click="loadMessages">
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="!userId"
        class="tip"
        type="warning"
        show-icon
        :closable="false"
        title="未能获取当前登录用户 ID，请重新登录后重试。"
      />

      <el-alert
        v-if="errorTip"
        class="tip"
        type="error"
        show-icon
        :closable="false"
        :title="errorTip"
      />

      <div v-loading="loading">
        <EmptyState v-if="!loading && !errorTip && visibleMessages.length === 0" mark="信" :title="emptyText" />

        <template v-else>
          <div class="msg-summary">
            共 {{ allMessages.length }} 条消息，其中未读 {{ unreadCount }} 条。
          </div>
          <ul class="msg-list">
            <li v-for="item in visibleMessages" :key="item.id" class="msg-item" :class="{ unread: Number(item.isRead) !== 1 }">
              <div class="msg-head">
                <el-tag :type="typeTag(item.type)" size="small">{{ typeText(item.type) }}</el-tag>
                <span class="msg-title">{{ item.title }}</span>
                <el-tag v-if="Number(item.isRead) !== 1" type="danger" size="small" effect="plain">未读</el-tag>
              </div>
              <p class="msg-content">{{ item.content }}</p>
              <p class="msg-meta">类型：{{ typeText(item.type) }} · {{ formatTime(item.createTime) }}</p>
            </li>
          </ul>
        </template>
      </div>
    </el-card>

    <!-- 发消息弹窗 -->
    <el-dialog v-model="sendVisible" title="发消息" width="480px">
      <el-form ref="sendFormRef" :model="sendForm" :rules="sendRules" label-width="80px">
        <el-form-item label="接收用户" prop="userId">
          <el-input-number v-model="sendForm.userId" :min="1" :step="1" controls-position="right" class="full-width" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="sendForm.type" class="full-width">
            <el-option label="系统" :value="1" />
            <el-option label="互动" :value="2" />
            <el-option label="公告" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="sendForm.title" maxlength="50" show-word-limit placeholder="请输入消息标题" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="sendForm.content"
            type="textarea"
            :rows="4"
            maxlength="200"
            show-word-limit
            placeholder="请输入消息内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="sendVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="sending" @click="submitSend">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { listMessages, sendMessage } from '@/api/message';
import type { Notice } from '@/api/message';
import { formatTime } from '@/api/types';

import EmptyState from '@/components/EmptyState.vue';
type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger';

const userStore = useUserStore();

/** 当前登录用户 ID，取不到时降级为 0 */
const userId = computed<number>(() => {
  const n = Number(userStore.userInfo?.id);
  return Number.isFinite(n) ? n : 0;
});

const allMessages = ref<Notice[]>([]);
const loading = ref(false);
const errorTip = ref('');
const typeFilter = ref<number | undefined>(undefined);

const sendVisible = ref(false);
const sending = ref(false);
const sendFormRef = ref<FormInstance | null>(null);
const sendForm = ref<{ userId: number; title: string; content: string; type: number }>({
  userId: 1,
  title: '',
  content: '',
  type: 1,
});

const sendRules: FormRules = {
  userId: [{ required: true, message: '请输入接收用户 ID', trigger: 'blur' }],
  title: [{ required: true, message: '请输入消息标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入消息内容', trigger: 'blur' }],
  type: [{ required: true, message: '请选择消息类型', trigger: 'change' }],
};

const visibleMessages = computed<Notice[]>(() => {
  if (typeFilter.value === undefined) {
    return allMessages.value;
  }
  return allMessages.value.filter((item) => Number(item.type) === typeFilter.value);
});

const unreadCount = computed<number>(
  () => allMessages.value.filter((item) => Number(item.isRead) !== 1).length
);

const emptyText = computed<string>(() => {
  if (typeFilter.value !== undefined && allMessages.value.length > 0) {
    return '该类型下暂无消息';
  }
  return '消息盒空空如也，稍后再来看看吧';
});

async function loadMessages() {
  if (!userId.value) {
    allMessages.value = [];
    return;
  }
  loading.value = true;
  errorTip.value = '';
  try {
    allMessages.value = (await listMessages(userId.value)) || [];
  } catch {
    // request.ts 已统一提示业务错误，这里仅兜底防止未处理异常
    allMessages.value = [];
    errorTip.value = '消息加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

function openSend() {
  sendForm.value = {
    userId: userId.value > 0 ? userId.value : 1,
    title: '',
    content: '',
    type: 1,
  };
  sendVisible.value = true;
}

async function submitSend() {
  const form = sendFormRef.value;
  if (!form) {
    return;
  }
  const valid = await form.validate().catch(() => false);
  if (!valid) {
    return;
  }
  sending.value = true;
  try {
    await sendMessage({
      userId: Number(sendForm.value.userId),
      title: sendForm.value.title,
      content: sendForm.value.content,
      type: Number(sendForm.value.type),
    });
    ElMessage.success('消息已发送');
    sendVisible.value = false;
    await loadMessages();
  } catch {
    // request.ts 已统一提示业务错误，这里仅兜底防止未处理异常
  } finally {
    sending.value = false;
  }
}

function typeText(type: number | undefined | null): string {
  if (Number(type) === 2) return '互动';
  if (Number(type) === 3) return '公告';
  return '系统';
}

function typeTag(type: number | undefined | null): TagType {
  if (Number(type) === 2) return 'success';
  if (Number(type) === 3) return 'warning';
  return 'info';
}

onMounted(loadMessages);
</script>

<style scoped>
.messages {
  padding: 16px;
}
.panel {
  border-radius: var(--moyue-radius);
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--moyue-ink);
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.type-select {
  width: 120px;
}
.tip {
  margin-bottom: 12px;
}
.msg-summary {
  margin-bottom: 12px;
  font-size: 12px;
  color: var(--moyue-ink);
  opacity: 0.65;
}
.msg-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.msg-item {
  padding: 12px 14px;
  margin-bottom: 10px;
  border: 1px solid rgba(201, 56, 46, 0.14);
  border-left: 3px solid var(--moyue-gold);
  border-radius: var(--moyue-radius);
  background: var(--moyue-paper);
}
.msg-item.unread {
  border-left-color: var(--moyue-crimson);
  background: #fff;
}
.msg-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.msg-title {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  color: var(--moyue-ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.msg-content {
  margin: 0 0 6px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--moyue-ink);
  word-break: break-word;
}
.msg-meta {
  margin: 0;
  font-size: 12px;
  color: var(--moyue-ink);
  opacity: 0.55;
}
.full-width {
  width: 100%;
}
</style>
