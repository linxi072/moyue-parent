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
          <span class="panel-title">公告管理</span>
          <div class="panel-actions">
            <el-select v-model="status" style="width: 140px" @change="handleFilterChange">
              <el-option
                v-for="opt in statusOptions"
                :key="String(opt.value)"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <el-button type="primary" size="small" @click="openCreate">新建公告</el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="errorTip"
        class="error-tip"
        type="error"
        show-icon
        :closable="false"
        :title="errorTip"
      />

      <el-table v-loading="loading" :data="list" border stripe style="width: 100%">
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            {{ typeText(row.type) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="是否置顶" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.isTop === 1" type="danger">置顶</el-tag>
            <span v-else class="muted">否</span>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" min-width="180">
          <template #default="{ row }">
            {{ formatTime(row.publishTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <EmptyState v-if="!loading && !errorTip && list.length === 0" mark="告" title="暂无公告" />

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

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑公告' : '新建公告'" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="请输入公告标题" maxlength="100" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="5"
            placeholder="请输入公告正文"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width: 100%">
            <el-option
              v-for="opt in typeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option
              v-for="opt in editStatusOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="是否置顶">
          <el-switch
            v-model="form.isTop"
            :active-value="1"
            :inactive-value="0"
            active-text="置顶"
            inactive-text="否"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { formatTime } from '@/api/types';
import {
  createAnnouncement,
  deleteAnnouncement,
  listAnnouncements,
  updateAnnouncement,
} from '@/api/admin';
import type { AnnouncementEntity, AnnouncementPayload } from '@/api/admin';

import EmptyState from '@/components/EmptyState.vue';
const list = ref<AnnouncementEntity[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const errorTip = ref('');
const status = ref<number | ''>('');

const dialogVisible = ref(false);
const submitting = ref(false);
const editing = ref<AnnouncementEntity | null>(null);

const form = reactive<AnnouncementPayload>({
  title: '',
  content: '',
  type: 1,
  status: 0,
  isTop: 0,
});

const statusOptions: { label: string; value: number | '' }[] = [
  { label: '全部状态', value: '' },
  { label: '草稿（0）', value: 0 },
  { label: '已发布（1）', value: 1 },
  { label: '已下线（2）', value: 2 },
];

const typeOptions: { label: string; value: number }[] = [
  { label: '系统（1）', value: 1 },
  { label: '活动（2）', value: 2 },
  { label: '维护（3）', value: 3 },
];

const editStatusOptions: { label: string; value: number }[] = [
  { label: '草稿（0）', value: 0 },
  { label: '已发布（1）', value: 1 },
  { label: '已下线（2）', value: 2 },
];

async function load() {
  loading.value = true;
  errorTip.value = '';
  try {
    const res = await listAnnouncements(
      page.value,
      size.value,
      status.value === '' ? undefined : status.value
    );
    list.value = (res && res.records) || [];
    total.value = (res && res.total) || 0;
  } catch (err) {
    list.value = [];
    total.value = 0;
    const detail = err instanceof Error && err.message ? `（${err.message}）` : '';
    errorTip.value = `公告列表加载失败${detail}。请确认当前账号为管理员（role=3），否则后端会拒绝该请求。`;
  } finally {
    loading.value = false;
  }
}

function handleFilterChange() {
  page.value = 1;
  load();
}

function handlePage(p: number) {
  page.value = p;
  load();
}

function typeText(type: number) {
  if (type === 1) return '系统';
  if (type === 2) return '活动';
  if (type === 3) return '维护';
  return `未知（${type}）`;
}

function statusText(s: number) {
  if (s === 0) return '草稿';
  if (s === 1) return '已发布';
  if (s === 2) return '已下线';
  return `未知（${s}）`;
}

function openCreate() {
  editing.value = null;
  form.title = '';
  form.content = '';
  form.type = 1;
  form.status = 0;
  form.isTop = 0;
  dialogVisible.value = true;
}

function openEdit(row: AnnouncementEntity) {
  editing.value = row;
  form.title = row.title || '';
  form.content = row.content || '';
  form.type = row.type || 1;
  form.status = row.status || 0;
  form.isTop = row.isTop || 0;
  dialogVisible.value = true;
}

async function handleSubmit() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写公告标题');
    return;
  }
  submitting.value = true;
  try {
    const payload: AnnouncementPayload = {
      title: form.title,
      content: form.content,
      type: form.type,
      status: form.status,
      isTop: form.isTop,
    };
    if (editing.value) {
      await updateAnnouncement(editing.value.id, payload);
      ElMessage.success('公告已更新');
    } else {
      await createAnnouncement(payload);
      ElMessage.success('公告已创建');
    }
    dialogVisible.value = false;
    await load();
  } catch (err) {
    ElMessage.error(`保存失败：${err instanceof Error ? err.message : '未知错误'}`);
  } finally {
    submitting.value = false;
  }
}

async function handleDelete(row: AnnouncementEntity) {
  try {
    await ElMessageBox.confirm(`确认删除公告「${row.title}」？删除后不可恢复。`, '删除公告', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    await deleteAnnouncement(row.id);
    ElMessage.success('公告已删除');
    await load();
  } catch (err) {
    ElMessage.error(`删除失败：${err instanceof Error ? err.message : '未知错误'}`);
  }
}

onMounted(load);
</script>

<style scoped>
.admin-page {
  padding: 16px;
}
.role-tip,
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
.muted {
  color: var(--moyue-ink);
  opacity: 0.5;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
