<template>
  <div class="chapter-editor">
    <el-card shadow="never" class="panel">
      <template #header>
        <div class="panel-header">
          <div class="header-left">
            <el-button link @click="back">
              <span class="back-text">← 返回作品列表</span>
            </el-button>
            <span class="panel-title">{{ bookTitle }}</span>
          </div>
          <div class="panel-actions">
            <el-button type="primary" size="small" @click="openCreate">新建章节</el-button>
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

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="全部章节" name="all" />
        <el-tab-pane label="草稿箱" name="drafts" />
      </el-tabs>

      <el-table v-loading="loading" :data="list" border stripe style="width: 100%">
        <el-table-column prop="chapterNo" label="章节号" width="90" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="字数" width="110">
          <template #default="{ row }">
            {{ formatWordCount(row.wordCount) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" min-width="170">
          <template #default="{ row }">
            {{ formatTime(row.publishTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="row.status !== 2"
              type="success"
              link
              @click="handlePublish(row)"
            >
              发布
            </el-button>
            <el-button type="primary" link @click="openReorder(row)">排序</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <EmptyState v-if="!loading && !errorTip && list.length === 0" mark="章" :title="activeTab === 'drafts' ? '草稿箱是空的' : '还没有章节，先写第一章吧'">
        <template #action>
          <el-button type="primary" size="small" @click="openCreate">新建章节</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑章节' : '新建章节'" width="720px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" placeholder="请输入章节标题" maxlength="100" />
        </el-form-item>
        <el-form-item label="正文" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="12"
            placeholder="开始创作吧…"
          />
        </el-form-item>
        <el-form-item label="章节号" required>
          <el-input-number v-model="form.chapterNo" :min="1" :step="1" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 200px">
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

    <el-dialog v-model="reorderVisible" title="调整章节序号" width="400px">
      <el-form label-width="80px">
        <el-form-item label="章节">
          <span class="reorder-title">{{ reorderTarget?.title }}</span>
        </el-form-item>
        <el-form-item label="新序号">
          <el-input-number v-model="reorderNo" :min="1" :step="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reorderVisible = false">取消</el-button>
        <el-button type="primary" :loading="reordering" @click="handleReorderSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { formatTime, formatWordCount } from '@/api/types';
import { bookDetail } from '@/api/book';
import {
  CHAPTER_STATUS_TEXT,
  createChapter,
  deleteChapter,
  listChapters,
  listDrafts,
  publishChapter,
  reorderChapter,
  updateChapter,
} from '@/api/chapterWrite';
import type { ChapterEntity } from '@/api/chapterWrite';

import EmptyState from '@/components/EmptyState.vue';
const route = useRoute();
const router = useRouter();

const bookId = Number(route.params.bookId);
const bookTitle = ref('章节管理');

const list = ref<ChapterEntity[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const errorTip = ref('');
const activeTab = ref<'all' | 'drafts'>('all');

const dialogVisible = ref(false);
const submitting = ref(false);
const editing = ref<ChapterEntity | null>(null);

interface ChapterForm {
  title: string;
  content: string;
  chapterNo: number;
  status: number;
}

const form = reactive<ChapterForm>({
  title: '',
  content: '',
  chapterNo: 1,
  status: 0,
});

/** 可提交给后端的状态：0 草稿 / 1 审核中（已发布与已驳回由发布流程流转） */
const statusOptions: { label: string; value: number }[] = [
  { label: '草稿（0）', value: 0 },
  { label: '提交审核（1）', value: 1 },
];

const reorderVisible = ref(false);
const reordering = ref(false);
const reorderTarget = ref<ChapterEntity | null>(null);
const reorderNo = ref(1);

async function loadBook() {
  try {
    const book = await bookDetail(bookId);
    if (book && book.title) {
      bookTitle.value = book.title;
    }
  } catch {
    // 书名拿不到不影响章节管理，保留默认标题
  }
}

async function load() {
  loading.value = true;
  errorTip.value = '';
  try {
    const res =
      activeTab.value === 'drafts'
        ? await listDrafts(bookId, page.value, size.value)
        : await listChapters(bookId, page.value, size.value);
    list.value = (res && res.records) || [];
    total.value = (res && res.total) || 0;
  } catch (err) {
    list.value = [];
    total.value = 0;
    const detail = err instanceof Error && err.message ? `（${err.message}）` : '';
    errorTip.value = `章节列表加载失败${detail}`;
  } finally {
    loading.value = false;
  }
}

function handleTabChange() {
  page.value = 1;
  load();
}

function handlePage(p: number) {
  page.value = p;
  load();
}

function back() {
  router.push({ name: 'author-works' });
}

function statusText(status: number) {
  return CHAPTER_STATUS_TEXT[status] || `未知（${status}）`;
}

function statusTag(status: number): 'success' | 'warning' | 'info' | 'danger' {
  if (status === 2) return 'success';
  if (status === 1) return 'warning';
  if (status === 3) return 'danger';
  return 'info';
}

/** 新章节默认序号：当前最大章节号 + 1 */
function nextChapterNo() {
  let max = 0;
  for (const item of list.value) {
    if (item.chapterNo > max) {
      max = item.chapterNo;
    }
  }
  return max + 1;
}

function openCreate() {
  editing.value = null;
  form.title = '';
  form.content = '';
  form.chapterNo = nextChapterNo();
  form.status = 0;
  dialogVisible.value = true;
}

function openEdit(row: ChapterEntity) {
  editing.value = row;
  form.title = row.title || '';
  form.content = row.content || '';
  form.chapterNo = row.chapterNo || 1;
  form.status = row.status === 2 ? 1 : row.status || 0;
  dialogVisible.value = true;
}

async function handleSubmit() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写章节标题');
    return;
  }
  if (!form.content.trim()) {
    ElMessage.warning('请填写章节正文');
    return;
  }
  const target = editing.value;
  submitting.value = true;
  try {
    if (target) {
      await updateChapter(target.id, {
        title: form.title.trim(),
        content: form.content,
        chapterNo: form.chapterNo,
        status: form.status,
      });
      ElMessage.success('章节已更新');
    } else {
      await createChapter({
        bookId,
        title: form.title.trim(),
        content: form.content,
        chapterNo: form.chapterNo,
        status: form.status,
      });
      ElMessage.success('章节已创建');
    }
    dialogVisible.value = false;
    await load();
  } catch (err) {
    ElMessage.error(`保存失败：${err instanceof Error ? err.message : '未知错误'}`);
  } finally {
    submitting.value = false;
  }
}

async function handlePublish(row: ChapterEntity) {
  try {
    await ElMessageBox.confirm(`确认发布章节「${row.title}」？`, '发布章节', {
      type: 'warning',
      confirmButtonText: '立即发布',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    await publishChapter(row.id);
    ElMessage.success('章节已发布');
    await load();
  } catch (err) {
    ElMessage.error(`发布失败：${err instanceof Error ? err.message : '未知错误'}`);
  }
}

function openReorder(row: ChapterEntity) {
  reorderTarget.value = row;
  reorderNo.value = row.chapterNo || 1;
  reorderVisible.value = true;
}

async function handleReorderSubmit() {
  const target = reorderTarget.value;
  if (!target) {
    return;
  }
  reordering.value = true;
  try {
    await reorderChapter(target.id, reorderNo.value);
    ElMessage.success('章节序号已更新');
    reorderVisible.value = false;
    await load();
  } catch (err) {
    ElMessage.error(`排序失败：${err instanceof Error ? err.message : '未知错误'}`);
  } finally {
    reordering.value = false;
  }
}

async function handleDelete(row: ChapterEntity) {
  try {
    await ElMessageBox.confirm(`确认删除章节「${row.title}」？删除后不可恢复。`, '删除章节', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    await deleteChapter(row.id);
    ElMessage.success('章节已删除');
    await load();
  } catch (err) {
    ElMessage.error(`删除失败：${err instanceof Error ? err.message : '未知错误'}`);
  }
}

onMounted(() => {
  loadBook();
  load();
});
</script>

<style scoped>
.chapter-editor {
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
.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.back-text {
  color: var(--moyue-crimson);
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
.error-tip {
  margin-bottom: 16px;
}
.reorder-title {
  color: var(--moyue-ink);
  opacity: 0.7;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
