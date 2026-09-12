<template>
  <div class="moyue-page">
    <PageHeader title="书城" subtitle="翻阅墨阅全站作品，挑一本开始读">
      <template #extra>
        <el-tag type="info" effect="plain" size="small">共 {{ total }} 部作品</el-tag>
      </template>
    </PageHeader>

    <!-- 加载骨架：保持与真实卡片一致的栅格，避免加载完成时布局跳动 -->
    <el-row v-if="loading && books.length === 0" :gutter="16">
      <el-col v-for="n in size" :key="n" :xs="12" :sm="8" :md="6" :lg="4">
        <div class="book-card moyue-panel" style="padding: 0">
          <el-skeleton animated>
            <template #template>
              <el-skeleton-item variant="image" style="height: 0; padding-bottom: 133%" />
              <div style="padding: 10px">
                <el-skeleton-item variant="h3" style="width: 80%" />
                <el-skeleton-item variant="text" style="width: 50%; margin-top: 8px" />
                <el-skeleton-item variant="text" style="margin-top: 8px" />
              </div>
            </template>
          </el-skeleton>
        </div>
      </el-col>
    </el-row>

    <template v-else>
      <el-row :gutter="16">
        <el-col v-for="book in books" :key="book.bookId" :xs="12" :sm="8" :md="6" :lg="4">
          <el-card
            class="book-card moyue-panel moyue-hoverable"
            shadow="never"
            :body-style="{ padding: '10px' }"
            @click="goDetail(book.bookId)"
          >
            <CoverImage :src="book.coverUrl" :title="book.title" ratio="3 / 4">
              <span class="book-status">
                <StatusTag :value="book.status" :map="BOOK_STATUS_TEXT" :tones="BOOK_TONES" />
              </span>
            </CoverImage>

            <h3 class="book-title moyue-clamp-1" :title="book.title">{{ book.title }}</h3>
            <p class="book-author moyue-clamp-1">{{ book.author || '佚名' }} · {{ book.category }}</p>
            <p class="book-meta moyue-num">{{ formatWordCount(book.wordCount) }}</p>
            <p class="book-intro moyue-clamp-2" :title="book.intro">{{ book.intro || '暂无简介' }}</p>
          </el-card>
        </el-col>
      </el-row>

      <EmptyState
        v-if="!loading && books.length === 0"
        mark="书"
        title="书城还没有作品"
        description="成为第一个发布作品的作者吧。"
      >
        <template #action>
          <el-button type="primary" @click="goAuthorWorks">去创作</el-button>
        </template>
      </EmptyState>

      <div v-if="total > 0" class="book-pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[8, 12, 24, 48]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="load"
        />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { listBooks } from '@/api/book';
import type { BookVO } from '@/api/book';
import { BOOK_STATUS_TEXT, formatWordCount } from '@/api/types';
import PageHeader from '@/components/PageHeader.vue';
import CoverImage from '@/components/CoverImage.vue';
import StatusTag from '@/components/StatusTag.vue';
import EmptyState from '@/components/EmptyState.vue';

const router = useRouter();

const books = ref<BookVO[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(12);
const loading = ref(false);

/** 书籍状态 → 标签色调（已下架用 info 弱化，连载与完结做区分） */
const BOOK_TONES: Record<number, 'success' | 'warning' | 'danger' | 'info' | 'primary'> = {
  1: 'primary',
  2: 'success',
  3: 'info',
};

async function load() {
  loading.value = true;
  try {
    const res = await listBooks(page.value, size.value);
    books.value = res.records || [];
    total.value = res.total || 0;
  } catch (e) {
    // request.ts 已统一提示，这里只兜住加载态
    books.value = [];
  } finally {
    loading.value = false;
  }
}

function handleSizeChange() {
  // 改页大小后回到第一页，避免停在越界页码
  page.value = 1;
  load();
}

function goDetail(bookId: number) {
  router.push(`/books/${bookId}`);
}

function goAuthorWorks() {
  const raw = localStorage.getItem('moyue_user');
  const role = raw ? Number((JSON.parse(raw) as Record<string, unknown>).role || 1) : 1;
  if (role < 2) {
    ElMessage.info('创作需要先成为作者，可在个人中心查看角色');
    return;
  }
  router.push('/author/works');
}

onMounted(load);
</script>

<style scoped>
.book-card {
  margin-bottom: var(--moyue-gap-md);
  cursor: pointer;
  overflow: hidden;
}

.book-status {
  position: absolute;
  right: var(--moyue-gap-sm);
  top: var(--moyue-gap-sm);
}

.book-title {
  margin: 10px 0 4px;
  font-size: var(--moyue-font-md);
  font-weight: 600;
  color: var(--moyue-text);
  line-height: 1.4;
}

.book-author {
  margin: 0;
  font-size: var(--moyue-font-sm);
  color: var(--moyue-gold);
}

.book-meta {
  margin: var(--moyue-gap-xs) 0;
  font-size: var(--moyue-font-xs);
  color: var(--moyue-text-muted);
}

.book-intro {
  margin: var(--moyue-gap-sm) 0 0;
  font-size: var(--moyue-font-xs);
  color: var(--moyue-text-secondary);
  line-height: 1.6;
  min-height: 2.4em;
}

.book-pager {
  display: flex;
  justify-content: center;
  margin-top: var(--moyue-gap-lg);
}
</style>
