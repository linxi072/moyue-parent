<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="filter">
        <el-form-item label="作品">
          <el-select
            v-model="filter.bookId"
            filterable
            placeholder="选择作品"
            style="width: 240px"
            @change="onBookChange"
          >
            <el-option v-for="b in bookOptions" :key="b.id" :label="b.title" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadChapters" :disabled="!filter.bookId">查询</el-button>
          <el-button @click="openDrafts" :disabled="!filter.bookId">草稿箱</el-button>
          <el-button type="success" @click="openCreate" :disabled="!filter.bookId">新建章节</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-toolbar">
      <span class="toolbar-title">章节目录</span>
      <span class="toolbar-tip" v-if="filter.bookId">共 {{ total }} 章</span>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="chapterNo" label="序号" width="80" />
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="wordCount" label="字数" width="100" />
      <el-table-column prop="createTime" label="创建时间" min-width="160" />
      <el-table-column label="操作" min-width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row as ChapterVO)">编辑</el-button>
          <el-button link type="success" @click="onPublish(row as ChapterVO)" v-if="(row as ChapterVO).status !== 1">发布</el-button>
          <el-button link type="warning" @click="onOffshelf(row as ChapterVO)" v-if="(row as ChapterVO).status === 1">下架</el-button>
          <el-button link type="warning" @click="onReorder(row as ChapterVO)">排序</el-button>
          <el-button link type="danger" @click="onDelete(row as ChapterVO)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadChapters"
        @size-change="loadChapters"
      />
    </div>

    <el-dialog v-model="dialog" :title="form.id ? '编辑章节' : '新建章节'" width="min(620px, 94vw)">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="章节标题" />
        </el-form-item>
        <el-form-item label="正文">
          <el-input v-model="form.content" type="textarea" :rows="10" placeholder="章节正文" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageAdminBooks,
  pageAdminChapters,
  createChapter,
  updateChapter,
  deleteAdminChapter,
  publishAdminChapter,
  offshelfAdminChapter,
  reorderAdminChapter,
  drafts,
  type BookVO,
  type ChapterVO
} from '@/api/content'

const bookOptions = ref<BookVO[]>([])
const rows = ref<ChapterVO[]>([])
const total = ref(0)
const loading = ref(false)
const dialog = ref(false)
const filter = reactive<{ bookId?: number }>({})
const query = reactive({ page: 1, size: 10, bookId: undefined as number | undefined })
const form = reactive<Partial<ChapterVO>>({})

const statusText = (s?: number) => (s === 1 ? '已发布' : s === 2 ? '定时发布' : '草稿')
const statusType = (s?: number) => (s === 1 ? 'success' : s === 2 ? 'warning' : 'info')

async function loadBooks() {
  const r = await pageAdminBooks({ page: 1, size: 200 })
  bookOptions.value = r.records
}

function onBookChange(id: number) {
  query.bookId = id
  query.page = 1
  loadChapters()
}

async function loadChapters() {
  if (!query.bookId) return
  loading.value = true
  try {
    const r = await pageAdminChapters({ ...query, bookId: query.bookId })
    rows.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: undefined, title: '', content: '', bookId: filter.bookId })
  dialog.value = true
}

function openEdit(row: ChapterVO) {
  Object.assign(form, { id: row.id, title: row.title, content: '' })
  dialog.value = true
}

async function submit() {
  if (!form.title) return ElMessage.warning('请填写标题')
  if (form.id) {
    await updateChapter(form.id, { title: form.title, content: form.content })
    ElMessage.success('已保存')
  } else {
    await createChapter({ title: form.title, content: form.content, bookId: filter.bookId })
    ElMessage.success('已创建（草稿）')
  }
  dialog.value = false
  loadChapters()
}

async function onPublish(row: ChapterVO) {
  await publishAdminChapter(row.id)
  ElMessage.success('已发布')
  loadChapters()
}

async function onOffshelf(row: ChapterVO) {
  await ElMessageBox.confirm('确认下架该章节（回到草稿）？', '提示', { type: 'warning' })
  await offshelfAdminChapter(row.id)
  ElMessage.success('已下架')
  loadChapters()
}

async function onReorder(row: ChapterVO) {
  const no = await ElMessageBox.prompt('目标序号', '调整章节排序', { inputValue: String(row.chapterNo) })
  await reorderAdminChapter(row.id, Number(no.value))
  ElMessage.success('已排序')
  loadChapters()
}

async function onDelete(row: ChapterVO) {
  await ElMessageBox.confirm('确认删除该章节？', '提示', { type: 'warning' })
  await deleteAdminChapter(row.id)
  ElMessage.success('已删除')
  loadChapters()
}

async function openDrafts() {
  if (!filter.bookId) return
  const list = await drafts(filter.bookId)
  rows.value = list
  total.value = list.length
}

onMounted(loadBooks)
</script>
