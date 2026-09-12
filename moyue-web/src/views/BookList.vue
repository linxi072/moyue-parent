<template>
  <div class="book-list">
    <el-row :gutter="16">
      <el-col v-for="book in books" :key="book.bookId" :xs="24" :sm="12" :md="8" :lg="6">
        <el-card class="book-card" shadow="hover">
          <div class="book-cover" :style="{ backgroundImage: `url(${book.coverUrl})` }">
            <span class="book-status">{{ statusText(book.status) }}</span>
          </div>
          <h3 class="book-title">{{ book.title }}</h3>
          <p class="book-author">{{ book.author }} · {{ book.category }}</p>
          <p class="book-meta">字数 {{ formatWord(book.wordCount) }}</p>
          <p class="book-intro">{{ book.intro }}</p>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="!loading && books.length === 0" description="暂无书籍" />
    <div v-if="total > size" class="book-pager">
      <el-pagination
        layout="prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="handlePage"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { listBooks } from '@/api/book';
import type { BookVO } from '@/api/book';

const books = ref<BookVO[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(8);
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    const res = await listBooks(page.value, size.value);
    books.value = res.records || [];
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}

function handlePage(p: number) {
  page.value = p;
  load();
}

function statusText(status: number) {
  return status === 1 ? '连载中' : status === 2 ? '已完结' : '已下架';
}

function formatWord(n: number) {
  return n >= 10000 ? (n / 10000).toFixed(1) + '万字' : n + '字';
}

onMounted(load);
</script>

<style scoped>
.book-list {
  padding: 16px;
}
.book-card {
  margin-bottom: 16px;
  border-radius: var(--moyue-radius);
}
.book-cover {
  height: 120px;
  border-radius: 6px;
  background-size: cover;
  background-position: center;
  background-color: var(--moyue-crimson-deep);
  position: relative;
}
.book-status {
  position: absolute;
  right: 8px;
  top: 8px;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 4px;
}
.book-title {
  margin: 10px 0 4px;
  font-size: 16px;
  color: var(--moyue-ink);
}
.book-author {
  margin: 0;
  font-size: 13px;
  color: var(--moyue-gold);
}
.book-meta {
  margin: 4px 0;
  font-size: 12px;
  color: var(--moyue-ink);
  opacity: 0.6;
}
.book-intro {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--moyue-ink);
  opacity: 0.7;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.book-pager {
  display: flex;
  justify-content: center;
  margin-top: 8px;
}
</style>
