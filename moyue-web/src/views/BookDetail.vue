<template>
  <div class="book-detail moyue-page">
    <div v-loading="loading" class="detail-body">
      <!-- 加载失败兜底 -->
      <EmptyState
        v-if="!loading && loadError"
        mark="惜"
        title="书籍信息加载失败"
        description="可能是作品已下架，或网络不稳定。"
      >
        <template #action>
          <el-button type="primary" plain @click="loadAll">重新加载</el-button>
        </template>
      </EmptyState>

      <template v-else-if="book">
        <!-- 书籍头部信息 -->
        <div class="detail-head moyue-panel">
          <CoverImage
            class="detail-cover"
            :src="book.coverUrl"
            :title="book.title"
            ratio="3 / 4"
            radius="var(--moyue-radius)"
          />
          <div class="detail-info">
            <div class="detail-title-row">
              <h2 class="detail-title">{{ book.title }}</h2>
              <StatusTag :value="book.status" :map="BOOK_STATUS_TEXT" :tones="BOOK_TONES" />
              <div class="head-btns">
                <el-button type="warning" plain @click="openReward">打赏作者</el-button>
              </div>
            </div>
            <p class="detail-sub">
              {{ book.author || '佚名' }} · {{ book.category }} ·
              {{ formatWordCount(book.wordCount) }} · 共 {{ chapterTotal }} 章
            </p>
            <p class="detail-intro">{{ book.intro || '作者还没写简介~' }}</p>
            <div class="detail-actions">
              <el-button
                type="primary"
                :disabled="firstChapterId === null"
                @click="startReading"
              >
                {{ firstChapterId === null ? '暂无可读章节' : '开始阅读' }}
              </el-button>
              <el-button
                :type="inShelf ? 'default' : 'primary'"
                plain
                :disabled="inShelf"
                :loading="shelfing"
                @click="handleAddShelf"
              >
                {{ inShelf ? '已加入书架' : '加入书架' }}
              </el-button>
            </div>
          </div>
        </div>

        <!-- 目录 / 评论 -->
        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane label="目录" name="chapters">
            <div v-loading="chapterLoading">
              <ul v-if="chapters.length > 0" class="chapter-list">
                <li
                  v-for="chapter in chapters"
                  :key="chapter.id"
                  class="chapter-item"
                  @click="openChapter(chapter.id)"
                >
                  <span class="chapter-title">第{{ chapter.chapterNo }}章 {{ chapter.title }}</span>
                  <span class="chapter-meta">
                    {{ formatWordCount(chapter.wordCount) }} · {{ formatTime(chapter.publishTime) }}
                  </span>
                </li>
              </ul>
              <EmptyState
                v-else-if="!chapterLoading"
                mark="章"
                title="暂无已发布章节"
                description="作者还在码字，先加入书架，更新后第一时间看到。"
              />
              <div v-if="chapterTotal > chapterSize" class="detail-pager">
                <el-pagination
                  layout="prev, pager, next"
                  :total="chapterTotal"
                  :page-size="chapterSize"
                  :current-page="chapterPage"
                  @current-change="handleChapterPage"
                />
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane :label="`评论(${commentTotal})`" name="comments">
            <div v-loading="commentLoading">
              <div class="comment-editor">
                <el-input
                  v-model="commentText"
                  type="textarea"
                  :rows="3"
                  maxlength="200"
                  show-word-limit
                  placeholder="说点什么吧，支持一下作者~"
                />
                <div class="comment-editor-foot">
                  <el-button
                    type="primary"
                    :loading="posting"
                    :disabled="commentText.trim() === ''"
                    @click="handlePostComment"
                  >
                    发表评论
                  </el-button>
                </div>
              </div>

              <ul v-if="comments.length > 0" class="comment-list">
                <li v-for="comment in comments" :key="comment.id" class="comment-item">
                  <div class="comment-main">
                    <p class="comment-content">{{ comment.content }}</p>
                    <p class="comment-meta">
                      <span class="comment-author">读者 {{ comment.userId }}</span>
                      <span v-if="comment.userId === currentUserId" class="comment-mine">我的</span>
                      · {{ formatTime(comment.createTime) }}
                    </p>
                  </div>
                  <div class="comment-ops">
                    <el-button link type="primary" @click="handleLike(comment.id)">
                      点赞 {{ comment.likeCount || 0 }}
                    </el-button>
                    <el-button
                      v-if="comment.userId === currentUserId"
                      link
                      type="danger"
                      @click="handleDeleteComment(comment.id)"
                    >
                      删除
                    </el-button>
                  </div>
                </li>
              </ul>
              <EmptyState
                v-else-if="!commentLoading"
                mark="评"
                title="还没有评论"
                description="来抢个沙发，给作者一点鼓励。"
              />
              <div v-if="commentTotal > commentSize" class="detail-pager">
                <el-pagination
                  layout="prev, pager, next"
                  :total="commentTotal"
                  :page-size="commentSize"
                  :current-page="commentPage"
                  @current-change="handleCommentPage"
                />
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>

    <!-- 打赏弹窗 -->
    <el-dialog v-model="rewardVisible" title="打赏作者" width="440px">
      <el-form label-width="88px">
        <el-form-item label="打赏金额">
          <el-radio-group v-model="rewardAmount">
            <el-radio-button :value="6.6">6.6 元</el-radio-button>
            <el-radio-button :value="18.8">18.8 元</el-radio-button>
            <el-radio-button :value="58">58 元</el-radio-button>
            <el-radio-button :value="0">自定义</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="rewardAmount === 0" label="自定义金额">
          <el-input-number
            v-model="customAmount"
            :min="0.01"
            :max="10000"
            :precision="2"
            :step="1"
          />
          <span class="reward-unit">元</span>
        </el-form-item>
        <el-form-item label="支付方式">
          <!-- 后端 RewardOrderEntity 仅定义 1 微信 / 2 支付宝，不提供未实现的余额渠道 -->
          <el-radio-group v-model="payChannel">
            <el-radio :value="1">微信</el-radio>
            <el-radio :value="2">支付宝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-alert
          class="reward-tip"
          type="info"
          :closable="false"
          show-icon
          title="演示环境为模拟支付，不会真实扣款"
        />
      </el-form>
      <template #footer>
        <el-button @click="rewardVisible = false">取消</el-button>
        <el-button type="primary" :loading="rewarding" @click="submitReward">
          确认打赏 {{ displayAmount > 0 ? `¥${displayAmount}` : '' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { bookDetail } from '@/api/book';
import type { BookVO } from '@/api/book';
import { BOOK_STATUS_TEXT, formatTime, formatWordCount } from '@/api/types';
import { listChapters } from '@/api/chapter';
import type { ChapterEntity } from '@/api/chapter';
import { addComment, deleteComment, listComments, toggleLike } from '@/api/comment';
import type { CommentEntity } from '@/api/comment';
import { addToShelf } from '@/api/read';
import { createReward, payReward } from '@/api/reward';
import { useUserStore } from '@/stores/user';
import CoverImage from '@/components/CoverImage.vue';
import StatusTag from '@/components/StatusTag.vue';
import EmptyState from '@/components/EmptyState.vue';

/** 书籍状态 → 标签色调 */
const BOOK_TONES: Record<number, 'success' | 'warning' | 'danger' | 'info' | 'primary'> = {
  1: 'primary',
  2: 'success',
  3: 'info',
};

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

/** 当前书籍 ID，取自路由 /books/:id */
const bookId = computed(() => Number(route.params.id) || 0);
const currentUserId = computed(() => Number(userStore.userInfo?.id) || 0);

const loading = ref(false);
const loadError = ref(false);
const book = ref<BookVO | null>(null);
const activeTab = ref('chapters');

// ------------------------------ 书架 ------------------------------

const inShelf = ref(false);
const shelfing = ref(false);

async function handleAddShelf() {
  if (bookId.value === 0) {
    return;
  }
  shelfing.value = true;
  try {
    await addToShelf({ bookId: bookId.value });
    inShelf.value = true;
    ElMessage.success('已加入书架');
  } catch {
    // request.ts 已统一错误提示，这里只兜住未处理异常
  } finally {
    shelfing.value = false;
  }
}

// ------------------------------ 目录 ------------------------------

const chapters = ref<ChapterEntity[]>([]);
const chapterLoading = ref(false);
const chapterPage = ref(1);
const chapterSize = ref(20);
const chapterTotal = ref(0);

async function loadChapters() {
  if (bookId.value === 0) {
    return;
  }
  chapterLoading.value = true;
  try {
    const res = await listChapters(bookId.value, chapterPage.value, chapterSize.value);
    // 后端目录接口不做状态过滤，前端只展示已发布（status = 2）章节
    chapters.value = (res.records || []).filter((item) => item.status === 2);
    chapterTotal.value = res.total;
  } catch {
    chapters.value = [];
    chapterTotal.value = 0;
  } finally {
    chapterLoading.value = false;
  }
}

function handleChapterPage(p: number) {
  chapterPage.value = p;
  loadChapters();
}

function openChapter(chapterId: number) {
  router.push(`/read/${bookId.value}/${chapterId}`);
}

/**
 * 目录按 chapterNo 升序，取第一章作为「开始阅读」入口。
 * 已在书架且有进度时优先续读（进度由书架页维护，这里不额外请求）。
 */
const firstChapterId = computed(() => {
  if (chapters.value.length === 0) {
    return null;
  }
  const sorted = [...chapters.value].sort((a, b) => (a.chapterNo ?? 0) - (b.chapterNo ?? 0));
  return sorted[0].id ?? null;
});

function startReading() {
  if (firstChapterId.value === null) {
    return;
  }
  openChapter(firstChapterId.value);
}

// ------------------------------ 评论 ------------------------------

const comments = ref<CommentEntity[]>([]);
const commentLoading = ref(false);
const commentPage = ref(1);
const commentSize = ref(10);
const commentTotal = ref(0);
const commentText = ref('');
const posting = ref(false);

async function loadComments() {
  if (bookId.value === 0) {
    return;
  }
  commentLoading.value = true;
  try {
    const res = await listComments(bookId.value, commentPage.value, commentSize.value);
    comments.value = res.records || [];
    commentTotal.value = res.total;
  } catch {
    comments.value = [];
    commentTotal.value = 0;
  } finally {
    commentLoading.value = false;
  }
}

function handleCommentPage(p: number) {
  commentPage.value = p;
  loadComments();
}

async function handlePostComment() {
  const content = commentText.value.trim();
  if (content === '' || bookId.value === 0) {
    return;
  }
  posting.value = true;
  try {
    await addComment({ bookId: bookId.value, content });
    commentText.value = '';
    commentPage.value = 1;
    ElMessage.success('评论已发表');
    await loadComments();
  } catch {
    // request.ts 已统一错误提示
  } finally {
    posting.value = false;
  }
}

async function handleLike(commentId: number) {
  try {
    const likeCount = await toggleLike(commentId);
    const target = comments.value.find((item) => item.id === commentId);
    if (target) {
      target.likeCount = likeCount;
    }
  } catch {
    // request.ts 已统一错误提示
  }
}

async function handleDeleteComment(commentId: number) {
  try {
    await ElMessageBox.confirm('确定删除这条评论吗？', '删除评论', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    await deleteComment(commentId);
    ElMessage.success('评论已删除');
    await loadComments();
  } catch {
    // request.ts 已统一错误提示
  }
}

// ------------------------------ 打赏 ------------------------------

const rewardVisible = ref(false);
const rewarding = ref(false);
const rewardAmount = ref(6.6);
const customAmount = ref(10);
const payChannel = ref(1);

/** 弹窗底部按钮上显示的金额：自定义时取输入框的值 */
const displayAmount = computed(() => {
  const amount = rewardAmount.value === 0 ? customAmount.value : rewardAmount.value;
  return amount > 0 ? Number(amount.toFixed(2)) : 0;
});

function openReward() {
  rewardAmount.value = 6.6;
  customAmount.value = 10;
  payChannel.value = 1;
  rewardVisible.value = true;
}

async function submitReward() {
  const amount = rewardAmount.value === 0 ? customAmount.value : rewardAmount.value;
  if (!(amount > 0)) {
    ElMessage.warning('请输入正确的打赏金额');
    return;
  }
  rewarding.value = true;
  try {
    const order = await createReward({
      bookId: bookId.value,
      amount,
      payChannel: payChannel.value,
    });
    await payReward(order.orderNo);
    ElMessage.success('打赏成功');
    rewardVisible.value = false;
  } catch {
    // request.ts 已统一错误提示
  } finally {
    rewarding.value = false;
  }
}

// ------------------------------ 入口 ------------------------------

async function loadAll() {
  if (bookId.value === 0) {
    loadError.value = true;
    return;
  }
  loading.value = true;
  loadError.value = false;
  try {
    book.value = await bookDetail(bookId.value);
    await Promise.all([loadChapters(), loadComments()]);
  } catch {
    loadError.value = true;
    book.value = null;
  } finally {
    loading.value = false;
  }
}

onMounted(loadAll);
</script>

<style scoped>
.book-detail {
  padding: 0;
}
.detail-head {
  display: flex;
  gap: var(--moyue-gap-lg);
}
.detail-cover {
  width: 150px;
  flex-shrink: 0;
}
.detail-info {
  flex: 1;
  min-width: 0;
}
.detail-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.detail-title {
  margin: 0;
  font-size: var(--moyue-font-xl);
  color: var(--moyue-text);
  letter-spacing: 0.5px;
}
.head-btns {
  margin-left: auto;
  display: flex;
  gap: var(--moyue-gap-sm);
}
.detail-sub {
  margin: var(--moyue-gap) 0 0;
  font-size: var(--moyue-font-sm);
  color: var(--moyue-gold);
}
.detail-intro {
  margin: var(--moyue-gap) 0 0;
  font-size: var(--moyue-font);
  line-height: 1.75;
  color: var(--moyue-text-secondary);
}
.detail-actions {
  margin-top: var(--moyue-gap-lg);
  display: flex;
  gap: var(--moyue-gap-sm);
}
.detail-tabs {
  margin-top: var(--moyue-gap-md);
  background: var(--moyue-paper-raised);
  border: 1px solid var(--moyue-border);
  border-radius: var(--moyue-radius);
  padding: var(--moyue-gap-sm) var(--moyue-gap-md) var(--moyue-gap-md);
}
.reward-tip {
  margin-top: var(--moyue-gap-sm);
}
.chapter-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.chapter-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 6px;
  border-bottom: 1px dashed rgba(38, 34, 30, 0.12);
  cursor: pointer;
}
.chapter-item:hover .chapter-title {
  color: var(--moyue-crimson);
}
.chapter-title {
  font-size: var(--moyue-font-md);
  color: var(--moyue-text);
  transition: color 0.18s ease;
}
.chapter-meta {
  font-size: var(--moyue-font-xs);
  color: var(--moyue-text-muted);
  flex-shrink: 0;
}
.comment-editor {
  margin-bottom: 16px;
}
.comment-editor-foot {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}
.comment-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.comment-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid rgba(38, 34, 30, 0.08);
}
.comment-main {
  flex: 1;
  min-width: 0;
}
.comment-content {
  margin: 0;
  font-size: var(--moyue-font);
  line-height: 1.65;
  color: var(--moyue-text);
  word-break: break-word;
}
.comment-meta {
  margin: var(--moyue-gap-xs) 0 0;
  font-size: var(--moyue-font-xs);
  color: var(--moyue-text-muted);
}
.comment-author {
  color: var(--moyue-text-secondary);
}
.comment-mine {
  display: inline-block;
  margin-left: var(--moyue-gap-xs);
  padding: 0 5px;
  border-radius: var(--moyue-radius-sm);
  background: rgba(232, 163, 61, 0.16);
  color: var(--moyue-gold);
  font-size: 11px;
}
.comment-ops {
  flex-shrink: 0;
}
.reward-unit {
  margin-left: 8px;
  font-size: 13px;
  color: var(--moyue-ink);
  opacity: 0.7;
}
.detail-pager {
  display: flex;
  justify-content: center;
  margin-top: 12px;
}
</style>
