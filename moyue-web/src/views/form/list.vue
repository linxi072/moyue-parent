<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { usePage } from '@/composables/usePage'
import {
  pageForms,
  createForm,
  updateForm,
  deleteForm,
  copyForm,
  changeFormStatus,
  formHistory,
  rollbackForm
} from '@/api/form'
import type { SysForm } from '@/api/types'
import { STATUS_OPTIONS } from '@/utils/enums'

/** ⑯ 在线构建器 —— 表单定义管理（Schema 在设计器里维护） */
const router = useRouter()
const { loading, total, records, query, search, reset, handleSizeChange, handleCurrentChange } =
  usePage<SysForm>(pageForms, { formName: '', status: undefined })

const dialog = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<SysForm>({})
const rules: FormRules = {
  formName: [{ required: true, message: '请输入表单名称', trigger: 'blur' }],
  formKey: [
    { required: true, message: '请输入表单标识', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]*$/, message: '小写字母开头，仅含小写字母数字下划线', trigger: 'blur' }
  ]
}

// 历史版本
const historyVisible = ref(false)
const historyTarget = ref<SysForm>({})
const historyList = ref<any[]>([])

function openCreate() {
  editingId.value = null
  Object.assign(form, { formName: '', formKey: '', formDesc: '', status: 0 })
  dialog.value = true
}

function openEdit(row: SysForm) {
  editingId.value = row.id ?? null
  Object.assign(form, { ...row })
  dialog.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (editingId.value) {
    await updateForm(editingId.value, form)
    ElMessage.success('修改成功')
  } else {
    await createForm(form)
    ElMessage.success('新增成功')
  }
  dialog.value = false
  search()
}

async function handleDelete(row: SysForm) {
  try {
    await ElMessageBox.confirm(`确认删除表单「${row.formName}」？`, '警告', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteForm(row.id!)
    ElMessage.success('删除成功')
    search()
  } catch (e: any) {
    // 已有收集数据的表单后端会拒绝删除并提示，这里给用户「强制删除」的出口
    await ElMessageBox.confirm('该表单已收集数据，是否强制删除？数据将一并移除', '提示', {
      type: 'warning'
    })
    await deleteForm(row.id!, true)
    ElMessage.success('已强制删除')
    search()
  }
}

async function handleCopy(row: SysForm) {
  await copyForm(row.id!)
  ElMessage.success('已复制')
  search()
}

async function toggleStatus(row: SysForm) {
  const next = row.status === 1 ? 0 : 1
  await changeFormStatus(row.id!, next)
  ElMessage.success(next === 1 ? '已发布' : '已下线')
  search()
}

async function openHistory(row: SysForm) {
  historyTarget.value = row
  historyList.value = (await formHistory(row.id!)) || []
  historyVisible.value = true
}

async function handleRollback(versionId: number) {
  await ElMessageBox.confirm('确认回滚到该版本？当前版本会先自动留档', '提示', {
    type: 'warning'
  })
  await rollbackForm(historyTarget.value.id!, versionId)
  ElMessage.success('已回滚')
  historyVisible.value = false
  search()
}

function goDesigner(row: SysForm) {
  router.push({ path: '/form/designer', query: { formId: row.id, formName: row.formName } })
}

function goData(row: SysForm) {
  router.push({ path: '/form/data', query: { formId: row.id, formName: row.formName } })
}

function goPreview(row: SysForm) {
  router.push({ path: '/form/preview', query: { formId: row.id } })
}

onMounted(() => reset())
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="search-bar">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="表单名称">
          <el-input v-model="query.formName" clearable style="width: 180px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 120px">
            <el-option label="已发布" :value="1" />
            <el-option label="草稿" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset()">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div class="table-toolbar">
        <span class="title">表单列表</span>
        <el-button type="primary" @click="openCreate">新增表单</el-button>
      </div>

      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="formName" label="表单名称" min-width="180" />
        <el-table-column prop="formKey" label="表单标识" min-width="160" />
        <el-table-column prop="formDesc" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" align="center" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="360" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="goDesigner(row)">设计</el-button>
            <el-button link type="primary" @click="goData(row)">数据</el-button>
            <el-button link type="info" @click="goPreview(row)">预览</el-button>
            <el-button link type="warning" @click="toggleStatus(row)">
              {{ row.status === 1 ? '下线' : '发布' }}
            </el-button>
            <el-button link @click="handleCopy(row)">复制</el-button>
            <el-button link @click="openHistory(row)">版本</el-button>
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

    <el-dialog v-model="dialog" :title="editingId ? '编辑表单' : '新增表单'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="表单名称" prop="formName">
          <el-input v-model="form.formName" />
        </el-form-item>
        <el-form-item label="表单标识" prop="formKey">
          <el-input v-model="form.formKey" placeholder="如 reader_feedback" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio v-for="o in STATUS_OPTIONS" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.formDesc" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="historyVisible" :title="`历史版本 - ${historyTarget.formName}`" width="640px">
      <el-table :data="historyList" border size="small" max-height="400">
        <el-table-column prop="version" label="版本" width="80" align="center" />
        <el-table-column prop="createTime" label="保存时间" width="170" />
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleRollback(row.id)">回滚</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!historyList.length" description="暂无历史版本（发布后自动生成留档）" />
    </el-dialog>
  </div>
</template>

<style scoped>
.title {
  font-weight: 600;
  font-size: 15px;
}
</style>
