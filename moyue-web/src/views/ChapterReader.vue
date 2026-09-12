<template>
  <div class="chapter-reader">
    <!-- 顶部工具条：滚动时吸顶，字号与书架操作不脱离视线 -->
    <div class="reader-bar">
      <router-link class="reader-back" :to="`/books/${bookId}`">‹ 返回目录</router-link>

      <span v-if="publishedCatalog.length > 0" class="reader-progress moyue-num">
        第 {{ currentIndex + 1 }} / {{ publishedCatalog.length }} 章
      </span>

      <div class="reader-font">
        <span class="reader-font-label">字号</span>
        <el-radio-group v-model="fontSize" size="small" @change="persistFontSize">
          <el-radio-button value="small">小</el-radio-button>
          <el-radio-button value="medium">中</el-radio-button>
          <el-radio-button value="large">大</el-radio-button>
        </el-radio-group>
      </div>

      <div class="reader-shelf">
        <el-button v-if="inShelf" size="small" plain @click="handleRemoveShelf">移出书架</el-button>
        <el-button v-else size="small" type="primary" plain :loading="shelfing" @click="handleAddShelf">
          加入书架
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="reader-body">
      <EmptyState
        v-if="!loading && loadError"
        mark="阅"
        title="章节加载失败"
        description="可能是章节已下架或网络不稳定。"
      >
        <template #action>
          <el-button type="primary" plain @click="loadChapter">重新加载</el-button>
        </template>
      </EmptyState>

      <template v-else-if="chapter">
        <h1 class="reader-title">{{ chapter.title }}</h1>
        <p class="reader-meta">
          第 {{ chapter.chapterNo }} 章 · {{ formatWordCount(chapter.wordCount) }} ·
          {{ formatTime(chapter.publishTime || chapter.createTime) }}
        </p>

        <!-- 正文：行高与字距按阅读场景放宽，纯黑降一档避免刺眼 -->
        <div class="reader-content" :style="{ fontSize: fontSizePx + 'px' }">
          {{ chapter.content || '本章暂无正文' }}
        </div>

        <div class="reader-nav">
          <button
            class="nav-btn"
            type="button"
            :disabled="!prevChapter"
            @click="goChapter(prevChapter)"
          >
            <span class="nav-hint">上一章</span>
            <span class="nav-title moyue-clamp-1">
              {{ prevChapter ? prevChapter.title : '已经是第一章' }}
            </span>
          </button>
          <button
            class="nav-btn nav-btn-next"
            type="button"
            :disabled="!nextChapter"
            @click="goChapter(nextChapter)"
          >
            <span class="nav-hint">下一章</span>
            <span class="nav-title moyue-clamp-1">
              {{ nextChapter ? nextChapter.title : '已经是最后一章' }}
            </span>
          </button>
        </div>

        <p class="reader-tip">提示：键盘 ← → 可快速翻章</p>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getChapter, listChapters } from '@/api/chapter';
import type { ChapterEntity } from '@/api/chapter';
import { formatTime, formatWordCount } from '@/api/types';
import { addToShelf, getBookshelf, removeFromShelf, updateProgress } from '@/api/read';
import { useUserStore } from '@/stores/user';
import EmptyState from '@/components/EmptyState.vue';

type FontSize = 'small' | 'medium' | 'large';

const FONT_SIZE_KEY = 'moyue_reader_font_size';
const FONT_SIZE_PX: Record<FontSize, number> = {
  small: 15,
  medium: 17,
  large: 20,
};

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const bookId = computed(() => Number(route.params.bookId) || 0);
const chapterId = computed(() => Number(route.params.chapterId) || 0);
const currentUserId = computed(() => Number(userStore.userInfo?.id) || 0);

const loading = ref(false);
const loadError = ref(false);
const chapter = ref<ChapterEntity | null>(null);
const catalog = ref<ChapterEntity[]>([]);

// ------------------------------ 字号 ------------------------------

function readFontSize(): FontSize {
  const saved = localStorage.getItem(FONT_SIZE_KEY);
  return saved === 'small' || saved === 'medium' || saved === 'large' ? saved : 'medium';
}

const fontSize = ref<FontSize>(readFontSize());
const fontSizePx = computed(() => FONT_SIZE_PX[fontSize.value]);

function persistFontSize(value: string | number | boolean) {
  const next = String(value) as FontSize;
  fontSize.value = next;
  localStorage.setItem(FONT_SIZE_KEY, next);
}

// ------------------------------ 上一章 / 下一章 ------------------------------

/** 已发布章节按 chapter_no 升序构成的目录 */
const publishedCatalog = computed(() =>
  catalog.value.filter((item) => item.status === 2).slice().sort((a, b) => a.chapterNo - b.chapterNo)
);

const currentIndex = computed(() =>
  publishedCatalog.value.findIndex((item) => item.id === chapterId.value)
);

const prevChapter = computed<ChapterEntity | null>(() => {
  const idx = currentIndex.value;
  return idx > 0 ? publishedCatalog.value[idx - 1] : null;
});

const nextChapter = computed<ChapterEntity | null>(() => {
  const idx = currentIndex.value;
  return idx >= 0 && idx < publishedCatalog.value.length - 1
    ? publishedCatalog.value[idx + 1]
    : null;
});

function goChapter(target: ChapterEntity | null) {
  if (!target) {
    return;
  }
  router.push(`/read/${bookId.value}/${target.id}`);
}

/** 翻章后回到顶部：否则会停在上一章的滚动位置，体验很割裂 */
function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

/**
 * 键盘翻章：左箭头上一章 / 右箭头下一章。
 * 输入框获得焦点时不响应，避免干扰评论与搜索输入。
 */
function handleKeydown(e: KeyboardEvent) {
  const el = e.target as HTMLElement | null;
  if (el && ['INPUT', 'TEXTAREA'].includes(el.tagName)) {
    return;
  }
  if (e.key === 'ArrowLeft' && prevChapter.value) {
    goChapter(prevChapter.value);
  } else if (e.key === 'ArrowRight' && nextChapter.value) {
    goChapter(nextChapter.value);
  }
}

// ------------------------------ 书架 ------------------------------

const inShelf = ref(false);
const shelfing = ref(false);

async function loadShelfState() {
  if (currentUserId.value === 0 || bookId.value === 0) {
    return;
  }
  try {
    const shelf = await getBookshelf(currentUserId.value);
    inShelf.value = (shelf || []).some((item) => item.bookId === bookId.value);
  } catch {
    inShelf.value = false;
  }
}

async function handleAddShelf() {
  if (bookId.value === 0) {
    return;
  }
  shelfing.value = true;
  try {
    await addToShelf({ bookId: bookId.value });
    inShelf.value = true;
    ElMessage.success('已加入书架');
    await recordProgress();
  } catch {
    // request.ts 已统一错误提示
  } finally {
    shelfing.value = false;
  }
}

async function handleRemoveShelf() {
  try {
    await ElMessageBox.confirm('确定把这本书移出书架吗？', '移出书架', {
      type: 'warning',
      confirmButtonText: '移出',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    await removeFromShelf(bookId.value);
    inShelf.value = false;
    ElMessage.success('已移出书架');
  } catch {
    // request.ts 已统一错误提示
  }
}

// ------------------------------ 阅读进度 ------------------------------

/** 记录阅读进度；后端要求该书已在书架中，否则会报资源不存在，这里静默失败 */
async function recordProgress() {
  if (inShelf.value && bookId.value !== 0 && chapterId.value !== 0) {
    try {
      await updateProgress(bookId.value, chapterId.value);
    } catch {
      // 未加入书架时后端会报错，属于预期内，不打扰用户
    }
  }
}

// ------------------------------ 加载 ------------------------------

async function loadChapter() {
  if (bookId.value === 0 || chapterId.value === 0) {
    loadError.value = true;
    return;
  }
  loading.value = true;
  loadError.value = false;
  try {
    const [detail, catalogRes] = await Promise.all([
      getChapter(chapterId.value),
      listChapters(bookId.value, 1, 100),
    ]);
    chapter.value = detail;
    catalog.value = catalogRes.records || [];
    await loadShelfState();
    await recordProgress();
  } catch {
    loadError.value = true;
    chapter.value = null;
    catalog.value = [];
  } finally {
    loading.value = false;
  }
}

// 上一章 / 下一章通过修改路由参数跳转，这里监听参数变化重新加载并回顶
watch(chapterId, async () => {
  await loadChapter();
  await nextTick();
  scrollToTop();
});

onMounted(() => {
  fontSize.value = readFontSize();
  loadChapter();
  window.addEventListener('keydown', handleKeydown);
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown);
});
</script>

<style scoped>
.chapter-reader {
  /* 阅读区宽度按「一行 30~40 字」收紧，过长行会拖累阅读节奏 */
  max-width: 760px;
  margin: 0 auto;
  padding: 0;
}
.reader-bar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: var(--moyue-gap-md);
  flex-wrap: wrap;
  background: var(--moyue-paper-raised);
  border: 1px solid var(--moyue-border);
  border-radius: var(--moyue-radius);
  padding: var(--moyue-gap-sm) var(--moyue-gap-md);
  margin-bottom: var(--moyue-gap-md);
  box-shadow: var(--moyue-shadow-sm);
}
.reader-back {
  font-size: var(--moyue-font-sm);
  color: var(--moyue-crimson);
}
.reader-progress {
  font-size: var(--moyue-font-xs);
  color: var(--moyue-text-muted);
}
.reader-font {
  display: flex;
  align-items: center;
  gap: var(--moyue-gap-sm);
}
.reader-font-label {
  font-size: var(--moyue-font-sm);
  color: var(--moyue-text-secondary);
}
.reader-shelf {
  margin-left: auto;
}
.reader-body {
  min-height: 320px;
  background: var(--moyue-paper-raised);
  border: 1px solid var(--moyue-border);
  border-radius: var(--moyue-radius);
  padding: var(--moyue-gap-xl) var(--moyue-gap-xl);
  box-shadow: var(--moyue-shadow-sm);
}
.reader-title {
  margin: 0;
  font-size: var(--moyue-font-xl);
  font-weight: 700;
  color: var(--moyue-text);
  text-align: center;
  letter-spacing: 0.5px;
}
.reader-meta {
  margin: var(--moyue-gap-sm) 0 var(--moyue-gap-xl);
  font-size: var(--moyue-font-xs);
  color: var(--moyue-text-muted);
  text-align: center;
}
.reader-content {
  white-space: pre-wrap;
  word-break: break-word;
  /* 行高 1.9 + 段后距，长文阅读更松快；字色降一档，纯黑久读刺眼 */
  line-height: 1.9;
  color: #332e29;
  text-indent: 2em;
  letter-spacing: 0.3px;
}
.reader-nav {
  display: flex;
  justify-content: space-between;
  gap: var(--moyue-gap);
  margin-top: var(--moyue-gap-xl);
  padding-top: var(--moyue-gap-md);
  border-top: 1px solid var(--moyue-divider);
}
.nav-btn {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--moyue-gap-sm) var(--moyue-gap);
  border: 1px solid var(--moyue-border);
  border-radius: var(--moyue-radius);
  background: var(--moyue-paper);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.18s ease, background 0.18s ease;
}
.nav-btn-next {
  text-align: right;
}
.nav-btn:hover:not(:disabled) {
  border-color: var(--moyue-crimson);
  background: rgba(201, 56, 46, 0.04);
}
.nav-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.nav-hint {
  font-size: var(--moyue-font-xs);
  color: var(--moyue-text-muted);
}
.nav-title {
  font-size: var(--moyue-font-sm);
  color: var(--moyue-text);
}
.reader-tip {
  margin: var(--moyue-gap) 0 0;
  text-align: center;
  font-size: var(--moyue-font-xs);
  color: var(--moyue-text-muted);
}
</style>
