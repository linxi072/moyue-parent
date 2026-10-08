<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePage } from '@/composables/usePage'
import { pageFormData, getFormData, deleteFormData, exportFormDataUrl } from '@/api/form'
import { downloadFile } from '@/utils/download'
import type { SysFormData } from '@/api/types'

/** ⑯-b 收集数据 —— data_json 为整包 JSON，列表只展示摘要，详情弹窗展示全部 */
const route = useRoute()
const formId = ref<number>(Number(route.query.formId) || 0)
const formName = ref<string>((route.query.formName as string) || '')

const { loading, total, records, query, search, reset, handleSizeChange, handleCurrentChange } =
  usePage<SysFormData>((params) => pageFormData(formId.value, params), {})

const detailVisible = ref(false)
const detail = ref<Record<string, unknown>>({})
const detailMeta = ref<SysFormData>({})

function parseJson(text?: string): Record<string, unknown> {
  try {
    return JSON.parse(text || '{}')
  } catch {
    return {}
  }
}

function summary(row: SysFormData) {
  const obj = parseJson(row.dataJson)
  const keys = Object.keys(obj).slice(0, 2)
  return keys.map((k) => `${k}: ${obj[k]}`).join(' / ') || '-'
}

async function openDetail(row: SysFormData) {
  const data = await getFormData(formId.value, row.id!)
  detailMeta.value = data
  detail.value = parseJson(data.dataJson)
  detailVisible.value = true
}

async function handleDelete(row: SysFormData) {
  await ElMessageBox.confirm('确认删除该条收集数据？', '提示', { type: 'warning' })
  await deleteFormData(formId.value, row.id!)
  ElMessage.success('已删除')
  search()
}

function handleExport() {
  downloadFile(exportFormDataUrl(formId.value), `form-${formId.value}.csv`)
}

onMounted(() => reset())
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>收集数据{{ formName ? ` · ${formName}` : '' }}</span>
          <div>
            <el-button link type="primary" @click="search">刷新</el-button>
            <el-button link type="success" @click="handleExport">导出 CSV</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column label="数据摘要" min-width="320" show-overflow-tooltip>
          <template #default="{ row }">{{ summary(row) }}</template>
        </el-table-column>
        <el-table-column prop="submitName" label="提交人" width="140" />
        <el-table-column prop="submitIp" label="提交 IP" width="140" />
        <el-table-column prop="submitTime" label="提交时间" width="180" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
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

    <el-dialog v-model="detailVisible" title="提交详情" width="600px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="提交人">{{ detailMeta.submitName || '匿名' }}</el-descriptions-item>
        <el-descriptions-item label="提交 IP">{{ detailMeta.submitIp }}</el-descriptions-item>
        <el-descriptions-item label="提交时间" :span="2">
          {{ detailMeta.submitTime }}
        </el-descriptions-item>
      </el-descriptions>
      <el-table :data="Object.entries(detail).map(([k, v]) => ({ k, v }))" size="small" class="mt-2">
        <el-table-column prop="k" label="字段" width="180" />
        <el-table-column prop="v" label="值" min-width="200" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
