<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { usePage } from '@/composables/usePage'
import { pageConfigs, createConfig, updateConfig, deleteConfig } from '@/api/config'
import type { SysConfig } from '@/api/types'
import { CONFIG_TYPE_OPTIONS, labelOf, tagOf } from '@/utils/enums'

/**
 * ⑥ 参数管理域 —— config_type = 1 为系统内置，前端禁止删除（后端也会拒绝）。
 */
const { loading, total, records, query, search, reset, handleSizeChange, handleCurrentChange } =
  usePage<SysConfig>(pageConfigs, { configName: '', configKey: '', configType: undefined })

const dialog = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<SysConfig>({})
const rules: FormRules = {
  configName: [{ required: true, message: '请输入参数名称', trigger: 'blur' }],
  configKey: [
    { required: true, message: '请输入参数键名', trigger: 'blur' },
    { pattern: /^[a-zA-Z][\w.]*$/, message: '字母开头，仅含字母数字下划线点', trigger: 'blur' }
  ],
  configValue: [{ required: true, message: '请输入参数键值', trigger: 'blur' }]
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { configName: '', configKey: '', configValue: '', configType: 0, remark: '' })
  dialog.value = true
}

function openEdit(row: SysConfig) {
  editingId.value = row.id ?? null
  Object.assign(form, { ...row })
  dialog.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (editingId.value) {
    await updateConfig(editingId.value, form)
    ElMessage.success('修改成功')
  } else {
    await createConfig(form)
    ElMessage.success('新增成功')
  }
  dialog.value = false
  search()
}

async function handleDelete(row: SysConfig) {
  if (row.configType === 1) {
    ElMessage.warning('系统内置参数不允许删除')
    return
  }
  await ElMessageBox.confirm(`确认删除参数「${row.configName}」？`, '警告', { type: 'warning' })
  await deleteConfig(row.id!)
  ElMessage.success('删除成功')
  search()
}

onMounted(() => reset())
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="search-bar">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="参数名称">
          <el-input v-model="query.configName" clearable style="width: 160px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="参数键名">
          <el-input v-model="query.configKey" clearable style="width: 180px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="系统内置">
          <el-select v-model="query.configType" clearable placeholder="全部" style="width: 120px">
            <el-option
              v-for="o in CONFIG_TYPE_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
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
        <span class="title">参数列表</span>
        <el-button type="primary" @click="openCreate">新增参数</el-button>
      </div>

      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="configName" label="参数名称" min-width="160" />
        <el-table-column prop="configKey" label="参数键名" min-width="180" />
        <el-table-column prop="configValue" label="参数键值" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(CONFIG_TYPE_OPTIONS, row.configType)">
              {{ labelOf(CONFIG_TYPE_OPTIONS, row.configType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" :disabled="row.configType === 1" @click="handleDelete(row)">
              删除
            </el-button>
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

    <el-dialog v-model="dialog" :title="editingId ? '编辑参数' : '新增参数'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="参数名称" prop="configName">
          <el-input v-model="form.configName" />
        </el-form-item>
        <el-form-item label="参数键名" prop="configKey">
          <el-input v-model="form.configKey" placeholder="如 moyue.reader.defaultVip" />
        </el-form-item>
        <el-form-item label="参数键值" prop="configValue">
          <el-input v-model="form.configValue" />
        </el-form-item>
        <el-form-item label="系统内置">
          <el-radio-group v-model="form.configType">
            <el-radio v-for="o in CONFIG_TYPE_OPTIONS" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
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
