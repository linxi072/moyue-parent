<template>
  <div class="bookshelf moyue-page">
    <PageHeader title="我的书架" subtitle="接着上次的进度继续读">
      <template #extra>
        <el-tag v-if="items.length > 0" type="info" effect="plain" size="small">
          共 {{ items.length }} 本
        </el-tag>
      </template>
    </PageHeader>

    <!-- 书架需要逐本补全书名与进度，首屏用骨架占位 -->
    <el-row v-if="loading && items.length === 0" :gutter="16">
      <el-col v-for="n in 3" :key="n" :xs="24" :sm="12" :md="8">
        <div class="shelf-card moyue-panel" style="padding: 0">
          <el-skeleton animated>
            <template #template>
              <div style="padding: 16px">
                <el-skeleton-item variant="h3" style="width: 70%" />
                <el-skeleton-item variant="text" style="width: 40%; margin-top: 10px" />
                <el-skeleton-item variant="text" style="margin-top: 12px" />
                <el-skeleton-item variant="text" style="width: 60%; margin-top: 8px" />
              </div>
            </template>
          </el-skeleton>
        </div>
      </el-col>
    </el-row>

    <div v-else>
      <EmptyState v-if="!loading && loadError" mark="架" title="书架加载失败">
        <template #action>
          <el-button type="primary" plain @click="loadShelf">重新加载</el-button>
        </template>
      </EmptyState>

      <EmptyState
        v-else-if="!loading && items.length === 0"
        mark="架"
        title="书架还是空的"
        description="去书城挑一本喜欢的，加入书架后就能在这里续读。"
      >
        <template #action>
          <el-button type="primary" @click="goBookStore">去书城</el-button>
        </template>
      </EmptyState>

      <el-row v-else :gutter="16">
        <el-col v-for="item in items" :key="item.bookId" :xs="24" :sm="12" :md="8">
          <el-card class="shelf-card moyue-panel moyue-hoverable" shadow="never">
            <h3 class="shelf-card-title moyue-clamp-1" :title="item.title">{{ item.title }}</h3>
            <p class="shelf-card-author moyue-clamp-1">{{ item.author }}</p>
            <p class="shelf-card-progress">
              <span class="progress-label">最近读到</span>{{ item.chapterText }}
            </p>
            <p class="shelf-card-time">加入于 {{ formatTime(item.createTime) }}</p>
            <div class="shelf-card-ops">
              <el-button size="small" type="primary" plain @click="continueRead(item)">
                继续阅读
              </el-button>
              <el-button size="small" type="danger" plain @click="handleRemove(item)">
                移出书架
              </el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { bookDetail } from '@/api/book';
import { getChapter } from '@/api/chapter';
import { formatTime } from '@/api/types';
import { getBookshelf, removeFromShelf } from '@/api/read';
import { useUserStore } from '@/stores/user';

import EmptyState from '@/components/EmptyState.vue';
import PageHeader from '@/components/PageHeader.vue';
/** 书架卡片视图模型：书架行 + 书籍概要 + 章节进度文案 */
interface ShelfItem {
  bookId: number;
  lastChapterId: number | null;
  createTime: string;
  title: string;
  author: string;
  chapterText: string;
}

const router = useRouter();
const userStore = useUserStore();

const loading = ref(false);
const loadError = ref(false);
const items = ref<ShelfItem[]>([]);

const currentUserId = () => Number(userStore.userInfo?.id) || 0;

async function loadShelf() {
  const userId = currentUserId();
  if (userId === 0) {
    loadError.value = true;
    return;
  }
  loading.value = true;
  loadError.value = false;
  try {
    const shelf = await getBookshelf(userId);
    const rows = shelf || [];
    items.value = await Promise.all(
      rows.map(async (row) => {
        let title = `作品 ${row.bookId}`;
        let author = '未知作者';
        try {
          const book = await bookDetail(row.bookId);
          if (book) {
            title = book.title;
            author = book.author;
          }
        } catch {
          // 单本书籍拉取失败不影响其余卡片
        }

        let chapterText = '尚未开始阅读';
        if (row.lastChapterId) {
          try {
            const chapter = await getChapter(row.lastChapterId);
            chapterText = `第 ${chapter.chapterNo} 章 ${chapter.title}`;
          } catch {
            chapterText = '章节已不可读';
          }
        }

        return {
          bookId: row.bookId,
          lastChapterId: row.lastChapterId,
          createTime: row.createTime,
          title,
          author,
          chapterText,
        };
      })
    );
  } catch {
    loadError.value = true;
    items.value = [];
  } finally {
    loading.value = false;
  }
}

function continueRead(item: ShelfItem) {
  if (item.lastChapterId) {
    router.push(`/read/${item.bookId}/${item.lastChapterId}`);
  } else {
    router.push(`/books/${item.bookId}`);
  }
}

async function handleRemove(item: ShelfItem) {
  try {
    await ElMessageBox.confirm(`确定把《${item.title}》移出书架吗？`, '移出书架', {
      type: 'warning',
      confirmButtonText: '移出',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    await removeFromShelf(item.bookId);
    ElMessage.success('已移出书架');
    items.value = items.value.filter((row) => row.bookId !== item.bookId);
  } catch {
    // request.ts 已统一错误提示
  }
}

function goBookStore() {
  router.push('/books');
}

onMounted(loadShelf);
</script>

<style scoped>
.bookshelf {
  padding: 0;
}
.shelf-card {
  margin-bottom: var(--moyue-gap-md);
}
.shelf-card-title {
  margin: 0;
  font-size: var(--moyue-font-md);
  font-weight: 600;
  color: var(--moyue-text);
}
.shelf-card-author {
  margin: var(--moyue-gap-xs) 0 0;
  font-size: var(--moyue-font-sm);
  color: var(--moyue-gold);
}
.shelf-card-progress {
  margin: var(--moyue-gap-sm) 0 0;
  font-size: var(--moyue-font-sm);
  color: var(--moyue-text);
}
.progress-label {
  display: inline-block;
  margin-right: var(--moyue-gap-sm);
  padding: 1px 6px;
  border-radius: var(--moyue-radius-sm);
  background: rgba(201, 56, 46, 0.08);
  color: var(--moyue-crimson);
  font-size: var(--moyue-font-xs);
}
.shelf-card-time {
  margin: var(--moyue-gap-xs) 0 0;
  font-size: var(--moyue-font-xs);
  color: var(--moyue-text-muted);
}
.shelf-card-ops {
  display: flex;
  gap: var(--moyue-gap-sm);
  margin-top: var(--moyue-gap);
  padding-top: var(--moyue-gap);
  border-top: 1px solid var(--moyue-divider);
}
</style>
