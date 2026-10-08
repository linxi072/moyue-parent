<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { usePage } from '@/composables/usePage'
import {
  pageGenConfigs,
  updateGenConfig,
  deleteGenConfig,
  syncGenTable,
  downloadGenCodeUrl,
  generateToPath
} from '@/api/generator'
import { downloadFile } from '@/utils/download'
import type { GenTable, GenTableColumn } from '@/api/types'

/** ⑮ 代码生成 —— 生成配置列表（导入后的表在这里调整字段映射再生成） */
const router = useRouter()
const { loading, total, records, query, search, reset, handleSizeChange, handleCurrentChange } =
  usePage<GenTable>(pageGenConfigs, { tableName: '', tableComment: '' })

const dialog = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<GenTable>({ id: undefined })
const columns = ref<GenTableColumn[]>([])
const rules: FormRules = {
  className: [{ required: true, message: '请输入实体类名', trigger: 'blur' }],
  packageName: [{ required: true, message: '请输入包名', trigger: 'blur' }],
  moduleName: [{ required: true, message: '请输入模块名', trigger: 'blur' }],
  businessName: [{ required: true, message: '请输入业务名', trigger: 'blur' }],
  functionName: [{ required: true, message: '请输入功能名', trigger: 'blur' }]
}

async function openEdit(row: GenTable) {
  // 后端返回 { table, columns }，此处通过列表行 + 详情接口补齐列配置
  form.id = row.id
  Object.assign(form, { ...row })
  columns.value = []
  dialog.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  await updateGenConfig({ ...form, columns: columns.value })
  ElMessage.success('配置已保存')
  dialog.value = false
  search()
}

async function handleDelete(row: GenTable) {
  await ElMessageBox.confirm(`确认删除「${row.tableName}」的生成配置？`, '警告', {
    type: 'warning'
  })
  await deleteGenConfig(row.id!)
  ElMessage.success('删除成功')
  search()
}

async function handleSync(row: GenTable) {
  await ElMessageBox.confirm(`确认同步「${row.tableName}」的最新表结构？`, '提示', {
    type: 'warning'
  })
  await syncGenTable(row.id!)
  ElMessage.success('已同步')
  search()
}

/** 打包下载（ZIP 文件流） */
function handleDownload(row: GenTable) {
  downloadFile(downloadGenCodeUrl(row.id!), `${row.className || row.tableName}.zip`, 'post')
}

/**
 * 写入服务器磁盘。
 * 后端在容器只读 / 未配置 genPath 时会返回 403，这里显式提示而非静默失败。
 */
async function handleGenerate(row: GenTable) {
  try {
    const files = await generateToPath(row.id!)
    ElMessage.success(`已生成 ${files?.length ?? 0} 个文件`)
  } catch (e: any) {
    ElMessage.error(e?.message || '生成失败：目标路径不可写或未配置')
  }
}

function goPreview(row: GenTable) {
  router.push({ path: '/tool/generator/preview', query: { tableId: row.id, tableName: row.tableName } })
}

onMounted(() => reset())
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="search-bar">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="表名称">
          <el-input v-model="query.tableName" clearable style="width: 180px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="表描述">
          <el-input v-model="query.tableComment" clearable style="width: 180px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset()">重置</el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="success" @click="router.push('/tool/generator/import')">
            导入表结构
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div class="table-toolbar">
        <span class="title">生成配置</span>
      </div>

      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="tableName" label="表名称" min-width="180" />
        <el-table-column prop="tableComment" label="表描述" min-width="160" />
        <el-table-column prop="className" label="实体类" min-width="160" />
        <el-table-column prop="packageName" label="包名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="moduleName" label="模块" width="100" />
        <el-table-column prop="functionAuthor" label="作者" width="110" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="info" @click="goPreview(row)">预览</el-button>
            <el-button link type="success" @click="handleDownload(row)">下载 ZIP</el-button>
            <el-button link type="warning" @click="handleGenerate(row)">写入磁盘</el-button>
            <el-button link type="warning" @click="handleSync(row)">同步</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialog" title="编辑生成配置" width="720px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="表名称">
              <el-input :model-value="form.tableName" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="表描述">
              <el-input v-model="form.tableComment" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="实体类名" prop="className">
              <el-input v-model="form.className" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="包名" prop="packageName">
              <el-input v-model="form.packageName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模块名" prop="moduleName">
              <el-input v-model="form.moduleName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="业务名" prop="businessName">
              <el-input v-model="form.businessName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="功能名" prop="functionName">
              <el-input v-model="form.functionName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="作者">
              <el-input v-model="form.functionAuthor" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生成方式">
              <el-select v-model="form.genType" style="width: 100%">
                <el-option label="下载 ZIP" :value="0" />
                <el-option label="写入磁盘" :value="1" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生成路径">
              <el-input v-model="form.genPath" placeholder="写入磁盘时必填" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.title {
  font-weight: 600;
  font-size: 15px;
}
</style>
