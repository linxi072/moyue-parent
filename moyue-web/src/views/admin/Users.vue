<template>
  <div class="admin-page">
    <el-alert
      class="role-tip"
      type="info"
      show-icon
      :closable="false"
      title="管理后台需要管理员角色（role=3），非管理员访问会被后端拒绝并返回业务码 10003。"
    />

    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel-header">
          <span class="panel-title">用户管理</span>
          <div class="panel-actions">
            <el-input
              v-model="keyword"
              class="search"
              placeholder="按昵称或手机号筛选"
              clearable
            />
            <el-button type="primary" size="small" :loading="loading" @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <el-alert
        class="hint-tip"
        type="warning"
        show-icon
        :closable="false"
        title="后端未提供搜索参数，此处筛选仅对当前页数据做前端过滤；翻页后筛选条件依然生效，但不会跨页匹配。"
      />

      <el-alert
        v-if="errorTip"
        class="error-tip"
        type="error"
        show-icon
        :closable="false"
        :title="errorTip"
      />

      <el-table v-loading="loading" :data="filteredUsers" border stripe style="width: 100%">
        <el-table-column prop="id" label="ID" min-width="160" />
        <el-table-column prop="phone" label="手机号" min-width="140" />
        <el-table-column prop="nickname" label="昵称" min-width="140" show-overflow-tooltip />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 3 ? 'danger' : 'info'">{{ roleText(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'">
              {{ userStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <EmptyState v-if="!loading && !errorTip && filteredUsers.length === 0" mark="人" :title="keyword ? '当前页没有匹配的用户' : '暂无用户'" />

      <div v-if="total > size" class="pager">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="handlePage"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="编辑用户" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="用户 ID">
          <span class="readonly">{{ editing ? editing.id : '-' }}</span>
        </el-form-item>
        <el-form-item label="手机号">
          <span class="readonly">{{ editing ? editing.phone : '-' }}</span>
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="请输入昵称" maxlength="30" />
        </el-form-item>
        <el-form-item label="角色">
          <span class="readonly">{{ editing ? roleText(editing.role) : '-' }}</span>
        </el-form-item>
        <el-form-item label="状态">
          <span class="readonly">{{ editing ? userStatusText(editing.status) : '-' }}</span>
        </el-form-item>
      </el-form>

      <el-alert
        class="dialog-tip"
        type="info"
        :closable="false"
        show-icon
        title="角色与状态暂不可修改：后端仅开放 /users/{id}/profile 修改昵称与头像，未提供角色、状态的更新接口。"
      />

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { ROLE_TEXT, formatTime } from '@/api/types';
import { listUsers, updateProfile } from '@/api/user';
import type { UserVO } from '@/api/user';

import EmptyState from '@/components/EmptyState.vue';
const users = ref<UserVO[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const errorTip = ref('');
const keyword = ref('');

const dialogVisible = ref(false);
const submitting = ref(false);
const editing = ref<UserVO | null>(null);
const form = reactive<{ nickname: string }>({ nickname: '' });

/** 前端过滤：后端 /users 列表接口无搜索参数 */
const filteredUsers = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  if (!kw) return users.value;
  return users.value.filter((u) => {
    const nickname = (u.nickname || '').toLowerCase();
    const phone = (u.phone || '').toLowerCase();
    return nickname.includes(kw) || phone.includes(kw);
  });
});

async function load() {
  loading.value = true;
  errorTip.value = '';
  try {
    const res = await listUsers(page.value, size.value);
    users.value = (res && res.records) || [];
    total.value = (res && res.total) || 0;
  } catch (err) {
    users.value = [];
    total.value = 0;
    const detail = err instanceof Error && err.message ? `（${err.message}）` : '';
    errorTip.value = `用户列表加载失败${detail}。请确认当前账号为管理员（role=3），否则后端会拒绝该请求。`;
  } finally {
    loading.value = false;
  }
}

function handlePage(p: number) {
  page.value = p;
  load();
}

function roleText(role: number) {
  return ROLE_TEXT[role] || `未知（${role}）`;
}

function userStatusText(status: number) {
  if (status === 1) return '正常';
  if (status === 0) return '已禁用';
  return `未知（${status}）`;
}

function openEdit(row: UserVO) {
  editing.value = row;
  form.nickname = row.nickname || '';
  dialogVisible.value = true;
}

async function handleSubmit() {
  if (!editing.value) return;
  if (!form.nickname.trim()) {
    ElMessage.warning('请填写昵称');
    return;
  }
  submitting.value = true;
  try {
    await updateProfile(editing.value.id, { nickname: form.nickname.trim() });
    ElMessage.success('用户信息已更新');
    dialogVisible.value = false;
    await load();
  } catch (err) {
    ElMessage.error(`保存失败：${err instanceof Error ? err.message : '未知错误'}`);
  } finally {
    submitting.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.admin-page {
  padding: 16px;
}
.role-tip {
  margin-bottom: 16px;
}
.hint-tip,
.error-tip {
  margin-bottom: 16px;
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
.panel-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.search {
  width: 200px;
}
.readonly {
  color: var(--moyue-ink);
  opacity: 0.7;
}
.dialog-tip {
  margin-top: 8px;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
