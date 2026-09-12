<template>
  <div class="blog-detail">
    <el-card v-loading="loading" shadow="never" class="panel">
      <template v-if="post">
        <div class="detail-head">
          <div>
            <h2 class="detail-title">{{ post.title }}</h2>
            <p class="detail-meta">
              {{ post.authorName || '匿名作者' }} · {{ formatTime(post.createTime) }} · 浏览
              {{ post.viewCount || 0 }}
            </p>
          </div>
          <div v-if="isMine" class="detail-actions">
            <el-button type="primary" link @click="openEdit">编辑</el-button>
            <el-button type="danger" link @click="handleDelete">删除</el-button>
          </div>
        </div>

        <div v-if="post.coverUrl" class="detail-cover">
          <img :src="post.coverUrl" :alt="post.title" />
        </div>

        <p v-if="post.summary" class="detail-summary">{{ post.summary }}</p>
        <div class="detail-content">{{ post.content }}</div>

        <div class="detail-like">
          <el-button :type="liked ? 'danger' : 'default'" plain @click="handleLike">
            {{ liked ? '已点赞' : '点赞' }} {{ post.likeCount || 0 }}
          </el-button>
        </div>
      </template>

      <EmptyState v-if="!loading && !post && !errorTip" mark="帖" title="帖子不存在或已删除" />
      <el-alert
        v-if="errorTip"
        class="error-tip"
        type="error"
        show-icon
        :closable="false"
        :title="errorTip"
      />
    </el-card>

    <el-card shadow="never" class="panel comment-panel">
      <template #header>
        <span class="panel-title">评论（{{ comments.length }}）</span>
      </template>

      <div class="comment-editor">
        <el-input
          v-model="commentText"
          type="textarea"
          :rows="3"
          placeholder="说点什么…"
          maxlength="500"
        />
        <div class="comment-submit">
          <el-button type="primary" size="small" :loading="commenting" @click="handleComment">
            发表评论
          </el-button>
        </div>
      </div>

      <div v-for="c in comments" :key="c.id" class="comment-item">
        <div class="comment-meta">
          <span class="comment-user">{{ c.userName || `用户${c.userId}` }}</span>
          <span class="comment-time">{{ formatTime(c.createTime) }}</span>
        </div>
        <div class="comment-content">{{ c.content }}</div>
      </div>

      <EmptyState v-if="!commentsLoading && comments.length === 0" mark="帖" title="还没有评论，来抢沙发" />
    </el-card>

    <el-dialog v-model="dialogVisible" title="编辑帖子" width="640px">
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
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { formatTime } from '@/api/types';
import { addComment, deletePost, getPost, listComments, toggleLike, updatePost } from '@/api/blog';
import type { BlogCommentDTO, BlogPostDTO } from '@/api/blog';

import EmptyState from '@/components/EmptyState.vue';
const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const postId = Number(route.params.id);

const post = ref<BlogPostDTO | null>(null);
const loading = ref(false);
const errorTip = ref('');

const comments = ref<BlogCommentDTO[]>([]);
const commentsLoading = ref(false);
const commentText = ref('');
const commenting = ref(false);

/** 点赞态只在前端会话内记录，点赞数以后端返回为准 */
const liked = ref(false);

const dialogVisible = ref(false);
const submitting = ref(false);

interface PostForm {
  title: string;
  coverUrl: string;
  summary: string;
  content: string;
}

const form = reactive<PostForm>({
  title: '',
  coverUrl: '',
  summary: '',
  content: '',
});

function currentUserId(): number {
  const info = userStore.userInfo;
  const id = info ? Number(info.id) : 0;
  return Number.isFinite(id) ? id : 0;
}

const isMine = computed(() => !!post.value && post.value.authorId === currentUserId());

async function load() {
  loading.value = true;
  errorTip.value = '';
  try {
    post.value = await getPost(postId);
  } catch (err) {
    post.value = null;
    const detail = err instanceof Error && err.message ? `（${err.message}）` : '';
    errorTip.value = `帖子加载失败${detail}`;
  } finally {
    loading.value = false;
  }
}

async function loadComments() {
  commentsLoading.value = true;
  try {
    const res = await listComments(postId, 1, 50);
    comments.value = (res && res.records) || [];
  } catch (err) {
    comments.value = [];
    ElMessage.error(`评论加载失败：${err instanceof Error ? err.message : '未知错误'}`);
  } finally {
    commentsLoading.value = false;
  }
}

async function handleLike() {
  try {
    const count = await toggleLike(postId, currentUserId());
    liked.value = !liked.value;
    if (post.value) {
      post.value.likeCount = typeof count === 'number' ? count : post.value.likeCount;
    }
  } catch (err) {
    ElMessage.error(`操作失败：${err instanceof Error ? err.message : '未知错误'}`);
  }
}

async function handleComment() {
  if (!commentText.value.trim()) {
    ElMessage.warning('请输入评论内容');
    return;
  }
  commenting.value = true;
  try {
    await addComment(postId, currentUserId(), commentText.value.trim());
    commentText.value = '';
    ElMessage.success('评论已发表');
    await loadComments();
  } catch (err) {
    ElMessage.error(`评论失败：${err instanceof Error ? err.message : '未知错误'}`);
  } finally {
    commenting.value = false;
  }
}

function openEdit() {
  if (!post.value) {
    return;
  }
  form.title = post.value.title || '';
  form.coverUrl = post.value.coverUrl || '';
  form.summary = post.value.summary || '';
  form.content = post.value.content || '';
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
    await updatePost(postId, {
      title: form.title.trim(),
      coverUrl: form.coverUrl || undefined,
      summary: form.summary || undefined,
      content: form.content,
      status: post.value?.status ?? 1,
    });
    ElMessage.success('帖子已更新');
    dialogVisible.value = false;
    await load();
  } catch (err) {
    ElMessage.error(`保存失败：${err instanceof Error ? err.message : '未知错误'}`);
  } finally {
    submitting.value = false;
  }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm('确认删除这篇帖子？删除后不可恢复。', '删除帖子', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    await deletePost(postId);
    ElMessage.success('帖子已删除');
    router.push({ name: 'blog-list' });
  } catch (err) {
    ElMessage.error(`删除失败：${err instanceof Error ? err.message : '未知错误'}`);
  }
}

onMounted(() => {
  load();
  loadComments();
});
</script>

<style scoped>
.blog-detail {
  padding: 16px;
}
.panel {
  border-radius: var(--moyue-radius);
  margin-bottom: 16px;
}
.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--moyue-ink);
}
.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.detail-title {
  margin: 0 0 6px;
  font-size: 22px;
  color: var(--moyue-ink);
}
.detail-meta {
  margin: 0;
  font-size: 13px;
  color: var(--moyue-ink);
  opacity: 0.55;
}
.detail-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}
.detail-cover {
  margin: 16px 0;
}
.detail-cover img {
  max-width: 100%;
  border-radius: var(--moyue-radius);
}
.detail-summary {
  margin: 12px 0;
  padding: 10px 12px;
  background: var(--moyue-paper);
  border-left: 3px solid var(--moyue-gold);
  font-size: 14px;
  color: var(--moyue-ink);
  opacity: 0.8;
}
.detail-content {
  margin: 16px 0;
  font-size: 15px;
  line-height: 1.9;
  white-space: pre-wrap;
  color: var(--moyue-ink);
}
.detail-like {
  display: flex;
  justify-content: center;
  padding-top: 8px;
  border-top: 1px solid rgba(38, 34, 30, 0.08);
}
.error-tip {
  margin-top: 16px;
}
.comment-editor {
  margin-bottom: 16px;
}
.comment-submit {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
.comment-item {
  padding: 12px 0;
  border-top: 1px solid rgba(38, 34, 30, 0.08);
}
.comment-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--moyue-ink);
  opacity: 0.55;
}
.comment-user {
  color: var(--moyue-gold);
  opacity: 1;
}
.comment-content {
  margin-top: 6px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  color: var(--moyue-ink);
}
</style>
