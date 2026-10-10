<template>
  <div class="consumer-page">
    <div class="bar"><span class="title">搜索发现</span></div>

    <el-autocomplete
      v-model="keyword"
      :fetch-suggestions="querySuggest"
      placeholder="输入关键词，联想热词"
      clearable
      class="search-box"
      @select="onSelect"
    >
      <template #append>
        <el-button @click="onSearch">搜索</el-button>
      </template>
    </el-autocomplete>

    <div class="section-title">热门搜索</div>
    <div class="hot-words">
      <el-tag
        v-for="w in hotWords"
        :key="w.id"
        class="hot-word"
        effect="plain"
        @click="onPick(w.word || '')"
      >{{ w.word }}</el-tag>
      <el-empty v-if="!hotWords.length" description="暂无热词" :image-size="60" />
    </div>

    <div v-if="suggestions.length" class="section-title">联想结果</div>
    <div class="hot-words">
      <el-tag
        v-for="w in suggestions"
        :key="w.id"
        class="hot-word"
        type="info"
        effect="plain"
        @click="onPick(w.word || '')"
      >{{ w.word }}</el-tag>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { consumerHotWords, consumerSuggest, type SearchHotWordVO } from '@/api/search'

// 搜索发现（公开只读）：热词榜 + 实时联想。内容检索结果接口由搜索模块后续接入。
const keyword = ref('')
const hotWords = ref<SearchHotWordVO[]>([])
const suggestions = ref<SearchHotWordVO[]>([])

async function loadHot() {
  try {
    hotWords.value = await consumerHotWords(10)
  } catch {
    /* 忽略：热词非关键 */
  }
}

function querySuggest(queryString: string, cb: (data: any[]) => void) {
  const k = queryString.trim()
  if (!k) {
    cb([])
    return
  }
  consumerSuggest(k)
    .then((list) => {
      cb(
        (list || []).map((w) => ({ value: w.word || '', word: w.word, id: w.id, hitCount: w.hitCount }))
      )
    })
    .catch(() => cb([]))
}

function onSelect(item: Record<string, any>) {
  keyword.value = (item?.value as string) || ''
  suggestions.value = []
}

function onPick(w: string) {
  keyword.value = w
  suggestions.value = []
}

function onSearch() {
  if (!keyword.value.trim()) return
  // 当前 C 端以「发现」能力为主；内容检索结果接口（/api/v1/search/...）由搜索模块补齐后接入
  ElMessage.info(`关键词：${keyword.value}（内容检索结果接口待搜索模块接入）`)
}

onMounted(loadHot)
</script>

<style scoped lang="scss">
.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  .title { font-size: 16px; font-weight: 600; color: var(--color-text); }
}
.search-box { width: 100%; margin-bottom: 14px; }
.section-title { font-size: 13px; color: var(--color-text-muted); margin: 6px 0 8px; }
.hot-words { display: flex; flex-wrap: wrap; gap: 8px; }
.hot-word { cursor: pointer; }
</style>
