<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { listMenus, createMenu, updateMenu, deleteMenu } from '@/api/menu'
import type { SysMenu } from '@/api/types'
import { MENU_TYPE_OPTIONS, VISIBLE_OPTIONS, STATUS_OPTIONS, labelOf, tagOf } from '@/utils/enums'
import { buildTree } from '@/utils/tree'

/** ③ 菜单管理域 —— M 目录 / C 菜单 / F 按钮，权限标识 perms 落在 F 上 */
const loading = ref(false)
const list = ref<SysMenu[]>([])
const flat = ref<SysMenu[]>([])
const query = reactive({ menuName: '', status: undefined as number | undefined })

const dialog = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<SysMenu>({})
const rules: FormRules = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }]
}

async function load() {
  loading.value = true
  try {
    const data = await listMenus({ page: 1, size: 500, menuName: query.menuName, status: query.status })
    flat.value = data || []
    list.value = buildTree(flat.value, 0)
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.menuName = ''
  query.status = undefined
  load()
}

function openCreate(row?: SysMenu) {
  editingId.value = null
  Object.assign(form, {
    parentId: row?.id ?? 0,
    menuName: '',
    menuType: 'M',
    path: '',
    component: '',
    perms: '',
    icon: '',
    sort: 1,
    visible: 1,
    status: 1
  })
  dialog.value = true
}

function openEdit(row: SysMenu) {
  editingId.value = row.id ?? null
  Object.assign(form, { ...row })
  dialog.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (editingId.value) {
    await updateMenu(editingId.value, form)
    ElMessage.success('修改成功')
  } else {
    await createMenu(form)
    ElMessage.success('新增成功')
  }
  dialog.value = false
  load()
}

async function handleDelete(row: SysMenu) {
  await ElMessageBox.confirm(`确认删除菜单「${row.menuName}」？子菜单将一并删除`, '警告', {
    type: 'warning'
  })
  await deleteMenu(row.id!)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="search-bar">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="菜单名称">
          <el-input v-model="query.menuName" clearable style="width: 180px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 110px">
            <el-option
              v-for="o in STATUS_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div class="table-toolbar">
        <span class="title">菜单列表</span>
        <el-button type="primary" @click="openCreate()">新增菜单</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="list"
        row-key="id"
        border
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="menuName" label="菜单名称" min-width="180" />
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(MENU_TYPE_OPTIONS, row.menuType)">
              {{ labelOf(MENU_TYPE_OPTIONS, row.menuType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="70" align="center" />
        <el-table-column prop="path" label="路由地址" min-width="140" />
        <el-table-column prop="component" label="组件路径" min-width="160" show-overflow-tooltip />
        <el-table-column prop="perms" label="权限标识" min-width="160" show-overflow-tooltip />
        <el-table-column label="可见" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(VISIBLE_OPTIONS, row.visible)">
              {{ labelOf(VISIBLE_OPTIONS, row.visible) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="row.menuType !== 'F'"
              link
              type="primary"
              @click="openCreate(row)"
            >
              新增子项
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog" :title="editingId ? '编辑菜单' : '新增菜单'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="上级菜单">
              <el-tree-select
                v-model="form.parentId"
                :data="[{ id: 0, menuName: '顶级菜单', children: list }]"
                :props="{ label: 'menuName', children: 'children' }"
                node-key="id"
                check-strictly
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="菜单类型" prop="menuType">
              <el-radio-group v-model="form.menuType">
                <el-radio v-for="o in MENU_TYPE_OPTIONS" :key="o.value" :value="o.value">
                  {{ o.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="form.sort" :min="0" :max="999" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="菜单名称" prop="menuName">
              <el-input v-model="form.menuName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="菜单图标">
              <el-input v-model="form.icon" placeholder="Element Plus 图标名，如 Setting" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType !== 'F'" :span="12">
            <el-form-item label="路由地址">
              <el-input v-model="form.path" placeholder="如 /system/user" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType === 'C'" :span="12">
            <el-form-item label="组件路径">
              <el-input v-model="form.component" placeholder="如 system/user/index" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="权限标识">
              <el-input v-model="form.perms" placeholder="如 system:user:add" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="显示状态">
              <el-radio-group v-model="form.visible">
                <el-radio v-for="o in VISIBLE_OPTIONS" :key="o.value" :value="o.value">
                  {{ o.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="菜单状态">
              <el-radio-group v-model="form.status">
                <el-radio v-for="o in STATUS_OPTIONS" :key="o.value" :value="o.value">
                  {{ o.label }}
                </el-radio>
              </el-radio-group>
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
