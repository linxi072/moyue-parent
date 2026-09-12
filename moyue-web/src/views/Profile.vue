<template>
  <div class="profile">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel-header">
          <span class="panel-title">个人中心</span>
          <el-button type="primary" size="small" :loading="loading" :disabled="!userId" @click="loadProfile">
            刷新
          </el-button>
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

      <div v-loading="loading" class="profile-body">
        <EmptyState v-if="!loading && !errorTip && !profile" mark="人" title="暂未加载到用户资料" />

        <div v-else-if="profile" class="profile-main">
          <div class="avatar-box">
            <img v-if="profile.avatarUrl" :src="profile.avatarUrl" alt="头像" class="avatar" />
            <div v-else class="avatar avatar-empty">{{ avatarFallback }}</div>
          </div>

          <el-descriptions :column="1" border size="small" class="desc">
            <el-descriptions-item label="用户 ID">{{ profile.id }}</el-descriptions-item>
            <el-descriptions-item label="手机号">
              <span>{{ profile.phone }}</span>
              <el-tag class="inline-tag" size="small" type="info" effect="plain">不可修改</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="昵称">{{ profile.nickname || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="角色">
              <el-tag :type="roleTagType(profile.role)" size="small">{{ roleText(profile.role) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusTagType(profile.status)" size="small">
                {{ statusText(profile.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="注册时间">{{ formatTime(profile.createTime) }}</el-descriptions-item>
          </el-descriptions>

          <div class="actions">
            <el-button type="primary" size="small" @click="openEdit">编辑资料</el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 编辑资料弹窗：后端只允许修改昵称与头像 -->
    <el-dialog v-model="editVisible" title="编辑资料" width="480px">
      <el-alert
        class="tip"
        type="info"
        show-icon
        :closable="false"
        title="出于安全考虑，手机号、角色与状态不可在此修改。"
      />
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="80px">
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="editForm.nickname" maxlength="20" show-word-limit placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="头像" prop="avatarUrl">
          <el-input v-model="editForm.avatarUrl" placeholder="请输入头像图片地址" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="editVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { getUser, updateProfile } from '@/api/user';
import type { UserVO } from '@/api/user';
import { ROLE_TEXT, formatTime } from '@/api/types';

import EmptyState from '@/components/EmptyState.vue';
type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger';

const userStore = useUserStore();

/** 当前登录用户 ID，取不到时降级为 0 */
const userId = computed<number>(() => {
  const n = Number(userStore.userInfo?.id);
  return Number.isFinite(n) ? n : 0;
});

const profile = ref<UserVO | null>(null);
const loading = ref(false);
const errorTip = ref('');

const editVisible = ref(false);
const saving = ref(false);
const editFormRef = ref<FormInstance | null>(null);
const editForm = ref<{ nickname: string; avatarUrl: string }>({ nickname: '', avatarUrl: '' });

const editRules: FormRules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 2, max: 20, message: '昵称长度需在 2 到 20 个字符之间', trigger: 'blur' },
  ],
};

/** 头像缺失时用昵称首字兜底 */
const avatarFallback = computed<string>(() => {
  const nickname = String(profile.value?.nickname || '').trim();
  return nickname ? nickname.slice(0, 1) : '墨';
});

async function loadProfile() {
  if (!userId.value) {
    profile.value = null;
    return;
  }
  loading.value = true;
  errorTip.value = '';
  try {
    profile.value = await getUser(userId.value);
  } catch {
    // request.ts 已统一提示业务错误，这里仅兜底防止未处理异常
    profile.value = null;
    errorTip.value = '用户资料加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

function openEdit() {
  editForm.value = {
    nickname: profile.value?.nickname || '',
    avatarUrl: profile.value?.avatarUrl || '',
  };
  editVisible.value = true;
}

async function submitEdit() {
  const form = editFormRef.value;
  if (!form) {
    return;
  }
  const valid = await form.validate().catch(() => false);
  if (!valid) {
    return;
  }
  saving.value = true;
  try {
    const updated = await updateProfile(userId.value, {
      nickname: editForm.value.nickname.trim(),
      avatarUrl: editForm.value.avatarUrl.trim(),
    });
    profile.value = updated;
    // 同步回登录态，保持 localStorage 里的昵称/头像一致
    const prev: Record<string, unknown> = userStore.userInfo ? { ...userStore.userInfo } : {};
    userStore.setUserInfo({
      ...prev,
      id: updated.id,
      nickname: updated.nickname,
      avatarUrl: updated.avatarUrl || '',
    });
    ElMessage.success('资料已保存');
    editVisible.value = false;
  } catch {
    // request.ts 已统一提示业务错误，这里仅兜底防止未处理异常
  } finally {
    saving.value = false;
  }
}

function roleText(role: number | undefined | null): string {
  return ROLE_TEXT[Number(role)] || '未知角色';
}

function roleTagType(role: number | undefined | null): TagType {
  const key = Number(role);
  if (key === 3) return 'danger';
  if (key === 2) return 'warning';
  return 'info';
}

function statusText(status: number | undefined | null): string {
  return Number(status) === 1 ? '正常' : '禁用';
}

function statusTagType(status: number | undefined | null): TagType {
  return Number(status) === 1 ? 'success' : 'danger';
}

onMounted(loadProfile);
</script>

<style scoped>
.profile {
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
.tip {
  margin-bottom: 12px;
}
.profile-body {
  min-height: 120px;
}
.profile-main {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  align-items: flex-start;
}
.avatar-box {
  flex: 0 0 auto;
}
.avatar {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--moyue-crimson-deep);
}
.avatar-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  color: #fff;
}
.desc {
  flex: 1 1 360px;
}
.inline-tag {
  margin-left: 8px;
}
.actions {
  flex: 1 1 100%;
}
</style>
