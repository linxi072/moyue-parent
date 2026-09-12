<template>
  <div class="author-works">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel-header">
          <span class="panel-title">我的作品</span>
          <el-button type="primary" size="small" @click="openCreate">新建作品</el-button>
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
        <el-table-column prop="title" label="书名" min-width="180" show-overflow-tooltip />
        <el-table-column label="分类" width="100">
          <template #default="{ row }">
            {{ row.category || '未知' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="字数" width="110">
          <template #default="{ row }">
            {{ formatWordCount(row.wordCount) }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="gotoChapters(row)">章节管理</el-button>
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <EmptyState v-if="!loading && !errorTip && list.length === 0" mark="书" title="还没有作品，先创建第一部吧">
        <template #action>
          <el-button type="primary" size="small" @click="openCreate">新建作品</el-button>
        </template>
      </EmptyState>

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

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑作品' : '新建作品'" width="560px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="书名" required>
          <el-input v-model="form.title" placeholder="请输入作品名称" maxlength="100" />
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 100%">
            <el-option
              v-for="opt in BOOK_CATEGORY_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="封面">
          <el-input v-model="form.coverUrl" placeholder="封面图地址，可留空" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="多个标签用逗号分隔，可留空" maxlength="100" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input
            v-model="form.intro"
            type="textarea"
            :rows="4"
            placeholder="一句话介绍你的作品"
          />
        </el-form-item>
        <el-form-item v-if="editing" label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option
              v-for="opt in statusOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
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
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { formatTime, formatWordCount } from '@/api/types';
import {
  BOOK_CATEGORY_ID_BY_NAME,
  BOOK_CATEGORY_OPTIONS,
  BOOK_STATUS_TEXT,
  createBook,
  deleteBook,
  listMyBooks,
  updateBook,
} from '@/api/book';
import type { BookSummaryDTO } from '@/api/book';

import EmptyState from '@/components/EmptyState.vue';
const router = useRouter();

const list = ref<BookSummaryDTO[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const errorTip = ref('');

const dialogVisible = ref(false);
const submitting = ref(false);
const editing = ref<BookSummaryDTO | null>(null);

interface BookForm {
  title: string;
  categoryId: number | undefined;
  coverUrl: string;
  tags: string;
  intro: string;
  status: number;
}

const form = reactive<BookForm>({
  title: '',
  categoryId: undefined,
  coverUrl: '',
  tags: '',
  intro: '',
  status: 1,
});

const statusOptions: { label: string; value: number }[] = [
  { label: '连载中（1）', value: 1 },
  { label: '已完结（2）', value: 2 },
  { label: '已下架（3）', value: 3 },
];

async function load() {
  loading.value = true;
  errorTip.value = '';
  try {
    const res = await listMyBooks(page.value, size.value);
    list.value = (res && res.records) || [];
    total.value = (res && res.total) || 0;
  } catch (err) {
    list.value = [];
    total.value = 0;
    const detail = err instanceof Error && err.message ? `（${err.message}）` : '';
    errorTip.value = `作品列表加载失败${detail}。请确认当前账号为作者（role=2），否则后端会拒绝该请求。`;
  } finally {
    loading.value = false;
  }
}

function handlePage(p: number) {
  page.value = p;
  load();
}

function statusText(status: number) {
  return BOOK_STATUS_TEXT[status] || `未知（${status}）`;
}

function statusTag(status: number): 'success' | 'info' | 'warning' {
  if (status === 1) return 'success';
  if (status === 2) return 'info';
  return 'warning';
}

function openCreate() {
  editing.value = null;
  form.title = '';
  form.categoryId = undefined;
  form.coverUrl = '';
  form.tags = '';
  form.intro = '';
  form.status = 1;
  dialogVisible.value = true;
}

function openEdit(row: BookSummaryDTO) {
  editing.value = row;
  form.title = row.title || '';
  // DTO 只回传分类名，用字典反查回填；查不到时留空（不覆盖后端原值）
  form.categoryId = row.categoryId ?? BOOK_CATEGORY_ID_BY_NAME[row.category || ''];
  form.coverUrl = row.coverUrl || '';
  form.tags = row.tags || '';
  form.intro = row.intro || '';
  form.status = row.status || 1;
  dialogVisible.value = true;
}

async function handleSubmit() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写作品名称');
    return;
  }
  const categoryId = form.categoryId;
  if (categoryId === undefined) {
    ElMessage.warning('请选择作品分类');
    return;
  }
  const target = editing.value;
  submitting.value = true;
  try {
    if (target) {
      await updateBook(target.bookId, {
        title: form.title.trim(),
        categoryId,
        coverUrl: form.coverUrl || undefined,
        tags: form.tags || undefined,
        intro: form.intro || undefined,
        status: form.status,
      });
      ElMessage.success('作品已更新');
    } else {
      await createBook({
        title: form.title.trim(),
        categoryId,
        coverUrl: form.coverUrl || undefined,
        tags: form.tags || undefined,
        intro: form.intro || undefined,
      });
      ElMessage.success('作品已创建');
    }
    dialogVisible.value = false;
    await load();
  } catch (err) {
    ElMessage.error(`保存失败：${err instanceof Error ? err.message : '未知错误'}`);
  } finally {
    submitting.value = false;
  }
}

async function handleDelete(row: BookSummaryDTO) {
  try {
    await ElMessageBox.confirm(`确认删除作品「${row.title}」？删除后不可恢复。`, '删除作品', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    await deleteBook(row.bookId);
    ElMessage.success('作品已删除');
    await load();
  } catch (err) {
    ElMessage.error(`删除失败：${err instanceof Error ? err.message : '未知错误'}`);
  }
}

function gotoChapters(row: BookSummaryDTO) {
  router.push({ name: 'chapter-editor', params: { bookId: String(row.bookId) } });
}

onMounted(load);
</script>

<style scoped>
.author-works {
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
.error-tip {
  margin-bottom: 16px;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
