<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { usePage } from '@/composables/usePage'
import {
  pageRoles,
  createRole,
  updateRole,
  deleteRole,
  assignRoleMenus,
  assignRoleDepts,
  getRoleMenus,
  getRoleDepts,
  listAllMenus
} from '@/api/role'
import { listDepts } from '@/api/dept'
import type { SysRole, SysMenu, SysDept } from '@/api/types'
import { STATUS_OPTIONS, DATA_SCOPE_OPTIONS, labelOf, tagOf } from '@/utils/enums'
import { buildTree } from '@/utils/tree'

/** ② 角色管理域 —— 菜单权限（功能权限）+ 部门范围（数据权限）双授权 */
const { loading, total, records, query, search, reset, handleSizeChange, handleCurrentChange } =
  usePage<SysRole>(pageRoles, { roleName: '', roleKey: '', status: undefined })

const menuTree = ref<SysMenu[]>([])
const deptTree = ref<SysDept[]>([])

const dialog = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<SysRole>({})
const rules: FormRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleKey: [
    { required: true, message: '请输入权限字符', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_:]*$/, message: '仅允许字母数字下划线冒号', trigger: 'blur' }
  ],
  dataScope: [{ required: true, message: '请选择数据范围', trigger: 'change' }]
}

// 菜单授权
const menuDialog = ref(false)
const menuTarget = ref<SysRole>({})
const menuTreeRef = ref()
const checkedMenus = ref<number[]>([])

// 数据权限
const deptDialog = ref(false)
const deptTarget = ref<SysRole>({})
const deptTreeRef = ref()
const checkedDepts = ref<number[]>([])

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    roleName: '',
    roleKey: '',
    sort: 1,
    status: 1,
    dataScope: 1,
    remark: ''
  })
  dialog.value = true
}

function openEdit(row: SysRole) {
  editingId.value = row.id ?? null
  Object.assign(form, { ...row })
  dialog.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (editingId.value) {
    await updateRole(editingId.value, form)
    ElMessage.success('修改成功')
  } else {
    await createRole(form)
    ElMessage.success('新增成功')
  }
  dialog.value = false
  search()
}

async function handleDelete(row: SysRole) {
  await ElMessageBox.confirm(`确认删除角色「${row.roleName}」？`, '警告', { type: 'warning' })
  await deleteRole(row.id!)
  ElMessage.success('删除成功')
  search()
}

async function toggleStatus(row: SysRole) {
  const next = row.status === 1 ? 0 : 1
  // 说明书未定义角色启停端点，走整体编辑接口改 status
  await updateRole(row.id!, { ...row, status: next })
  ElMessage.success(next === 1 ? '已启用' : '已停用')
  search()
}

async function openAssignMenus(row: SysRole) {
  menuTarget.value = row
  checkedMenus.value = (await getRoleMenus(row.id!)) || []
  menuDialog.value = true
}

async function submitAssignMenus() {
  const tree = menuTreeRef.value
  const ids = [...(tree?.getCheckedKeys() || []), ...(tree?.getHalfCheckedKeys() || [])]
  await assignRoleMenus(menuTarget.value.id!, ids)
  ElMessage.success('菜单权限已更新')
  menuDialog.value = false
}

async function openAssignDepts(row: SysRole) {
  deptTarget.value = row
  checkedDepts.value = (await getRoleDepts(row.id!)) || []
  deptDialog.value = true
}

async function submitAssignDepts() {
  const tree = deptTreeRef.value
  const ids = [...(tree?.getCheckedKeys() || []), ...(tree?.getHalfCheckedKeys() || [])]
  await assignRoleDepts(deptTarget.value.id!, ids)
  ElMessage.success('数据范围已更新')
  deptDialog.value = false
}

onMounted(async () => {
  const [menus, depts] = await Promise.all([
    listAllMenus(),
    listDepts({ page: 1, size: 500 })
  ])
  menuTree.value = buildTree(menus || [], 0)
  deptTree.value = buildTree(depts || [], 0)
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="search-bar">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="角色名称">
          <el-input v-model="query.roleName" clearable style="width: 160px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="权限字符">
          <el-input v-model="query.roleKey" clearable style="width: 160px" @keyup.enter="search" />
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
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset()">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div class="table-toolbar">
        <span class="title">角色列表</span>
        <el-button type="primary" @click="openCreate">新增角色</el-button>
      </div>

      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="roleName" label="角色名称" min-width="140" />
        <el-table-column prop="roleKey" label="权限字符" min-width="140" />
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column label="数据范围" width="140">
          <template #default="{ row }">{{ labelOf(DATA_SCOPE_OPTIONS, row.dataScope) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(STATUS_OPTIONS, row.status)">
              {{ labelOf(STATUS_OPTIONS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="310" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openAssignMenus(row)">菜单权限</el-button>
            <el-button link type="primary" @click="openAssignDepts(row)">数据权限</el-button>
            <el-button
              link
              :type="row.status === 1 ? 'danger' : 'success'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
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

    <el-dialog v-model="dialog" :title="editingId ? '编辑角色' : '新增角色'" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="角色名称" prop="roleName">
              <el-input v-model="form.roleName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="权限字符" prop="roleKey">
              <el-input v-model="form.roleKey" placeholder="如 content:book:edit" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
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
            <el-form-item label="数据范围" prop="dataScope">
              <el-select v-model="form.dataScope" style="width: 100%">
                <el-option
                  v-for="o in DATA_SCOPE_OPTIONS"
                  :key="o.value"
                  :label="o.label"
                  :value="o.value"
                />
              </el-select>
              <div class="hint">选择「自定义数据」后，需要在列表页点击「数据权限」勾选部门</div>
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

    <el-dialog v-model="menuDialog" :title="`菜单权限 - ${menuTarget.roleName}`" width="480px">
      <el-tree
        ref="menuTreeRef"
        :data="menuTree"
        :props="{ label: 'menuName', children: 'children' }"
        node-key="id"
        show-checkbox
        :default-checked-keys="checkedMenus"
        default-expand-all
      />
      <template #footer>
        <el-button @click="menuDialog = false">取消</el-button>
        <el-button type="primary" @click="submitAssignMenus">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="deptDialog" :title="`数据权限 - ${deptTarget.roleName}`" width="480px">
      <el-tree
        ref="deptTreeRef"
        :data="deptTree"
        :props="{ label: 'deptName', children: 'children' }"
        node-key="id"
        show-checkbox
        :default-checked-keys="checkedDepts"
        default-expand-all
      />
      <template #footer>
        <el-button @click="deptDialog = false">取消</el-button>
        <el-button type="primary" @click="submitAssignDepts">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.title {
  font-weight: 600;
  font-size: 15px;
}

.hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}
</style>
