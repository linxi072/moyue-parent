<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { usePage } from '@/composables/usePage'
import { useAppStore } from '@/stores/app'
import {
  pageUsers,
  createUser,
  updateUser,
  deleteUser,
  changeUserStatus,
  resetUserPassword,
  getUserRoles,
  assignUserRoles
} from '@/api/user'
import { listDepts } from '@/api/dept'
import { pageRoles } from '@/api/role'
import type { SysUser, SysDept, SysRole } from '@/api/types'
import { USER_TYPE_OPTIONS, STATUS_OPTIONS, labelOf, tagOf } from '@/utils/enums'
import { buildTree, flattenLabel } from '@/utils/tree'

/**
 * ① 用户管理域 —— 统一主体 sys_user。
 *
 * <p>user_type（读者/作者/运营）是「主体性质」，与后台角色是两层：
 * 后台账号必须 user_type = 3 才允许进入 Admin（网关 AdminRoleInterceptor 强制），
 * 但具体能看哪些菜单由角色决定。
 */
const { loading, total, records, query, search, reset, handleSizeChange, handleCurrentChange } =
  usePage<SysUser>(pageUsers, {
    keyword: '',
    deptId: undefined,
    userType: undefined,
    status: undefined
  })

const deptTree = ref<SysDept[]>([])
const roleOptions = ref<SysRole[]>([])
const appStore = useAppStore()

// 分页布局：移动端精简（去掉每页条数与跳转，避免拥挤），桌面保持完整
const pagerLayout = computed(() =>
  appStore.isMobile ? 'total, prev, pager, next' : 'total, sizes, prev, pager, next, jumper'
)

const deptNameMap = computed(() => flattenLabel(deptTree.value, 'deptName'))

// ---------- 新增 / 编辑 ----------
const dialog = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<SysUser>({})
const rules: FormRules = {
  username: [
    { required: true, message: '请输入登录账号', trigger: 'blur' },
    { min: 4, max: 30, message: '长度 4 ~ 30 个字符', trigger: 'blur' }
  ],
  nickname: [{ required: true, message: '请输入用户昵称', trigger: 'blur' }],
  userType: [{ required: true, message: '请选择主体类型', trigger: 'change' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

// ---------- 角色授权 ----------
const roleDialog = ref(false)
const roleTarget = ref<SysUser>({})
const roleIds = ref<number[]>([])

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    username: '',
    nickname: '',
    userType: 3,
    phone: '',
    email: '',
    deptId: undefined,
    status: 1,
    remark: ''
  })
  dialog.value = true
}

function openEdit(row: SysUser) {
  editingId.value = row.id ?? null
  Object.assign(form, {
    id: row.id,
    username: row.username,
    nickname: row.nickname,
    userType: row.userType,
    phone: row.phone,
    email: row.email,
    deptId: row.deptId,
    status: row.status,
    remark: row.remark
  })
  dialog.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (editingId.value) {
    await updateUser(editingId.value, form)
    ElMessage.success('修改成功')
  } else {
    await createUser(form)
    ElMessage.success('新增成功')
  }
  dialog.value = false
  search()
}

async function handleDelete(row: SysUser) {
  await ElMessageBox.confirm(`确认删除用户「${row.nickname}」？`, '警告', { type: 'warning' })
  await deleteUser(row.id!)
  ElMessage.success('删除成功')
  search()
}

async function toggleStatus(row: SysUser) {
  const next = row.status === 1 ? 0 : 1
  await changeUserStatus(row.id!, next)
  ElMessage.success(next === 1 ? '已启用' : '已停用')
  search()
}

async function openResetPwd(row: SysUser) {
  const { value } = await ElMessageBox.prompt(
    `请输入「${row.nickname}」的新密码`,
    '重置密码',
    { inputPattern: /^.{8,20}$/, inputErrorMessage: '密码长度 8 ~ 20 位' }
  )
  await resetUserPassword(row.id!, value)
  ElMessage.success('密码已重置')
}

async function openAssignRoles(row: SysUser) {
  roleTarget.value = row
  roleIds.value = await getUserRoles(row.id!)
  roleDialog.value = true
}

async function submitAssignRoles() {
  await assignUserRoles(roleTarget.value.id!, roleIds.value)
  ElMessage.success('角色已更新')
  roleDialog.value = false
  search()
}

async function loadOptions() {
  const [depts, roles] = await Promise.all([
    listDepts({ page: 1, size: 500 }),
    pageRoles({ page: 1, size: 500 })
  ])
  deptTree.value = buildTree(depts || [], 0)
  roleOptions.value = roles?.records || []
}

onMounted(() => {
  loadOptions()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="search-bar">
      <el-form :inline="true" :model="query" @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="账号 / 昵称 / 手机号"
            clearable
            style="width: 180px"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="部门">
          <el-tree-select
            v-model="query.deptId"
            :data="deptTree"
            :props="{ label: 'deptName', children: 'children' }"
            node-key="id"
            check-strictly
            clearable
            placeholder="全部部门"
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item label="主体类型">
          <el-select v-model="query.userType" clearable placeholder="全部" style="width: 120px">
            <el-option
              v-for="o in USER_TYPE_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
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
        <span class="title">用户列表</span>
        <el-button type="primary" @click="openCreate">新增用户</el-button>
      </div>

      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="username" label="登录账号" min-width="120" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column label="主体类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(USER_TYPE_OPTIONS, row.userType)">
              {{ labelOf(USER_TYPE_OPTIONS, row.userType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
        <el-table-column label="部门" width="130">
          <template #default="{ row }">{{ deptNameMap[row.deptId] || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="tagOf(STATUS_OPTIONS, row.status)">
              {{ labelOf(STATUS_OPTIONS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="loginIp" label="最后登录 IP" width="130" />
        <el-table-column prop="loginDate" label="最后登录" width="170" />
        <el-table-column label="操作" width="290" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openAssignRoles(row)">分配角色</el-button>
            <el-button link type="warning" @click="openResetPwd(row)">重置密码</el-button>
            <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row)">
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
          :layout="pagerLayout"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 新增 / 编辑 -->
    <el-dialog
      v-model="dialog"
      :title="editingId ? '编辑用户' : '新增用户'"
      :width="'min(620px, 94vw)'"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item label="登录账号" prop="username">
              <el-input v-model="form.username" :disabled="!!editingId" placeholder="4 ~ 30 位" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="form.nickname" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="主体类型" prop="userType">
              <el-select v-model="form.userType" style="width: 100%">
                <el-option
                  v-for="o in USER_TYPE_OPTIONS"
                  :key="o.value"
                  :label="o.label"
                  :value="o.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="归属部门">
              <el-tree-select
                v-model="form.deptId"
                :data="deptTree"
                :props="{ label: 'deptName', children: 'children' }"
                node-key="id"
                check-strictly
                clearable
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" maxlength="11" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
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

    <!-- 角色授权 -->
    <el-dialog
      v-model="roleDialog"
      :title="`分配角色 - ${roleTarget.nickname}`"
      :width="'min(520px, 94vw)'"
    >
      <el-select v-model="roleIds" multiple style="width: 100%" placeholder="请选择角色">
        <el-option
          v-for="r in roleOptions"
          :key="r.id"
          :label="`${r.roleName}（${r.roleKey}）`"
          :value="r.id!"
          :disabled="r.status === 0"
        />
      </el-select>
      <template #footer>
        <el-button @click="roleDialog = false">取消</el-button>
        <el-button type="primary" @click="submitAssignRoles">确定</el-button>
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
