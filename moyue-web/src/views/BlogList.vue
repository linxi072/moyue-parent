<template>
  <div class="blog-list moyue-page">
    <PageHeader title="博客广场" subtitle="作者与读者的自留地，聊聊创作与阅读">
      <template #extra>
        <el-button type="primary" @click="openCreate">写帖子</el-button>
      </template>
    </PageHeader>

    <el-alert
      v-if="errorTip"
      class="error-tip"
      type="error"
      show-icon
      :closable="false"
      :title="errorTip"
    />

    <el-row v-loading="loading" :gutter="16">
      <el-col v-for="post in list" :key="post.id" :xs="24" :sm="12" :md="8">
        <el-card
          class="post-card moyue-panel moyue-hoverable"
          shadow="never"
          :body-style="{ padding: '10px' }"
          @click="openDetail(post)"
        >
          <CoverImage
            :src="post.coverUrl"
            :title="post.title"
            ratio="16 / 9"
            height="110px"
          />
          <h3 class="post-title moyue-clamp-1" :title="post.title">{{ post.title }}</h3>
          <p class="post-author">{{ post.authorName || '匿名作者' }}</p>
          <p class="post-summary moyue-clamp-2">{{ post.summary || post.content }}</p>
          <div class="post-meta">
            <span class="moyue-num">赞 {{ post.likeCount || 0 }}</span>
            <span class="moyue-num">评论 {{ post.commentCount || 0 }}</span>
            <span>{{ formatTime(post.createTime) }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <EmptyState
      v-if="!loading && !errorTip && list.length === 0"
      mark="笔"
      title="还没有帖子"
      description="发第一篇，和大家聊聊你在读什么。"
    >
      <template #action>
        <el-button type="primary" @click="openCreate">写帖子</el-button>
      </template>
    </EmptyState>

    <div v-if="total > 0" class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[9, 12, 24]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="handleSizeChange"
        @current-change="load"
      />
    </div>

    <el-dialog v-model="dialogVisible" title="写帖子" width="640px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" placeholder="请输入帖子标题" maxlength="100" />
        </el-form-item>
        <el-form-item label="封面">
          <el-input v-model="form.coverUrl" placeholder="封面图地址，可留空" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" type="textarea" :rows="2" placeholder="一句话摘要，可留空" />
        </el-form-item>
        <el-form-item label="正文" required>
          <el-input v-model="form.content" type="textarea" :rows="10" placeholder="分享你的想法…" />
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
        <el-button type="primary" :loading="submitting" @click="handleSubmit">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { formatTime } from '@/api/types';
import { createPost, listPosts } from '@/api/blog';
import type { BlogPostDTO } from '@/api/blog';
import PageHeader from '@/components/PageHeader.vue';
import CoverImage from '@/components/CoverImage.vue';
import EmptyState from '@/components/EmptyState.vue';

const router = useRouter();
const userStore = useUserStore();

const list = ref<BlogPostDTO[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(12);
const loading = ref(false);
const errorTip = ref('');

const dialogVisible = ref(false);
const submitting = ref(false);

interface PostForm {
  title: string;
  coverUrl: string;
  summary: string;
  content: string;
  status: number;
}

const form = reactive<PostForm>({
  title: '',
  coverUrl: '',
  summary: '',
  content: '',
  status: 1,
});

const statusOptions: { label: string; value: number }[] = [
  { label: '草稿（0）', value: 0 },
  { label: '已发布（1）', value: 1 },
];

/** 当前登录用户 ID；userInfo 为 Record<string, unknown>，这里做安全取数 */
function currentUserId(): number {
  const info = userStore.userInfo;
  const id = info ? Number(info.id) : 0;
  return Number.isFinite(id) ? id : 0;
}

async function load() {
  loading.value = true;
  errorTip.value = '';
  try {
    const res = await listPosts(page.value, size.value);
    list.value = (res && res.records) || [];
    total.value = (res && res.total) || 0;
  } catch (err) {
    list.value = [];
    total.value = 0;
    const detail = err instanceof Error && err.message ? `（${err.message}）` : '';
    errorTip.value = `帖子列表加载失败${detail}`;
  } finally {
    loading.value = false;
  }
}

function handleSizeChange() {
  page.value = 1;
  load();
}

function openDetail(post: BlogPostDTO) {
  router.push({ name: 'blog-detail', params: { id: String(post.id) } });
}

function openCreate() {
  form.title = '';
  form.coverUrl = '';
  form.summary = '';
  form.content = '';
  form.status = 1;
  dialogVisible.value = true;
}

async function handleSubmit() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写帖子标题');
    return;
  }
  if (!form.content.trim()) {
    ElMessage.warning('请填写帖子正文');
    return;
  }
  submitting.value = true;
  try {
    await createPost({
      authorId: currentUserId(),
      title: form.title.trim(),
      coverUrl: form.coverUrl || undefined,
      summary: form.summary || undefined,
      content: form.content,
      status: form.status,
    });
    ElMessage.success('帖子已发布');
    dialogVisible.value = false;
    await load();
  } catch (err) {
    ElMessage.error(`发布失败：${err instanceof Error ? err.message : '未知错误'}`);
  } finally {
    submitting.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.blog-list {
  padding: 0;
}
.error-tip {
  margin-bottom: var(--moyue-gap-md);
}
.post-card {
  margin-bottom: var(--moyue-gap-md);
  cursor: pointer;
  overflow: hidden;
}
.post-title {
  margin: 10px 0 4px;
  font-size: var(--moyue-font-md);
  font-weight: 600;
  color: var(--moyue-text);
}
.post-author {
  margin: 0;
  font-size: var(--moyue-font-sm);
  color: var(--moyue-gold);
}
.post-summary {
  margin: var(--moyue-gap-sm) 0 0;
  font-size: var(--moyue-font-xs);
  color: var(--moyue-text-secondary);
  line-height: 1.6;
  min-height: 2.4em;
}
.post-meta {
  display: flex;
  gap: var(--moyue-gap);
  margin-top: var(--moyue-gap-sm);
  font-size: var(--moyue-font-xs);
  color: var(--moyue-text-muted);
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: var(--moyue-gap-lg);
}
</style>
