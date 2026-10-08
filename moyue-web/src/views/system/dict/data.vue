<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { usePage } from '@/composables/usePage'
import {
  pageDictTypes,
  pageDictData,
  createDictData,
  updateDictData,
  deleteDictData
} from '@/api/dict'
import type { SysDictType, SysDictData } from '@/api/types'
import { STATUS_OPTIONS, labelOf, tagOf } from '@/utils/enums'

/**
 * ⑤ 字典数据 —— 挂在某个字典类型下。
 * 支持从「字典类型」页带 typeId 跳转进来，也支持在本页切换字典类型。
 */
const route = useRoute()
const typeOptions = ref<SysDictType[]>([])
const typeId = ref<number>(Number(route.query.typeId) || 0)

const { loading, total, records, query, load, search, reset, handleSizeChange, handleCurrentChange } =
  usePage<SysDictData>(
    (params) => pageDictData(typeId.value, params),
    { dictLabel: '' }
  )

const dialog = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<SysDictData>({})
const rules: FormRules = {
  dictLabel: [{ required: true, message: '请输入字典标签', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入字典键值', trigger: 'blur' }]
}

function openCreate() {
  if (!typeId.value) {
    ElMessage.warning('请先选择字典类型')
    return
  }
  editingId.value = null
  Object.assign(form, {
    dictType: currentType.value?.dictType,
    dictLabel: '',
    dictValue: '',
    sort: 1,
    status: 1,
    remark: ''
  })
  dialog.value = true
}

function openEdit(row: SysDictData) {
  editingId.value = row.id ?? null
  Object.assign(form, { ...row })
  dialog.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (editingId.value) {
    await updateDictData(editingId.value, form)
    ElMessage.success('修改成功')
  } else {
    await createDictData(form)
    ElMessage.success('新增成功')
  }
  dialog.value = false
  load()
}

async function handleDelete(row: SysDictData) {
  await ElMessageBox.confirm(`确认删除字典项「${row.dictLabel}」？`, '警告', { type: 'warning' })
  await deleteDictData(row.id!)
  ElMessage.success('删除成功')
  load()
}

const currentType = ref<SysDictType | null>(null)

function changeType(id: number) {
  typeId.value = id
  currentType.value = typeOptions.value.find((t) => t.id === id) || null
  search()
}

onMounted(async () => {
  const res = await pageDictTypes({ page: 1, size: 500 })
  typeOptions.value = res?.records || []
  if (!typeId.value && typeOptions.value.length) {
    typeId.value = typeOptions.value[0].id!
  }
  currentType.value = typeOptions.value.find((t) => t.id === typeId.value) || null
  load()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="search-bar">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="字典类型">
          <el-select
            :model-value="typeId"
            filterable
            style="width: 240px"
            @change="changeType"
          >
            <el-option
              v-for="t in typeOptions"
              :key="t.id"
              :label="`${t.dictName}（${t.dictType}）`"
              :value="t.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="字典标签">
          <el-input v-model="query.dictLabel" clearable style="width: 160px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset({ dictLabel: '' })">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div class="table-toolbar">
        <span class="title">
          字典数据
          <el-text v-if="currentType" type="info" size="small">
            &nbsp;·&nbsp;{{ currentType.dictType }}
          </el-text>
        </span>
        <el-button type="primary" @click="openCreate">新增字典项</el-button>
      </div>

      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="dictLabel" label="字典标签" min-width="140" />
        <el-table-column prop="dictValue" label="字典键值" min-width="140" />
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(STATUS_OPTIONS, row.status)">
              {{ labelOf(STATUS_OPTIONS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
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

    <el-dialog v-model="dialog" :title="editingId ? '编辑字典项' : '新增字典项'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="字典类型">
          <el-input :model-value="currentType?.dictType" disabled />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="字典标签" prop="dictLabel">
              <el-input v-model="form.dictLabel" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="字典键值" prop="dictValue">
              <el-input v-model="form.dictValue" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="显示排序">
              <el-input-number v-model="form.sort" :min="0" :max="999" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio v-for="o in STATUS_OPTIONS" :key="o.value" :value="o.value">
                  {{ o.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
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
