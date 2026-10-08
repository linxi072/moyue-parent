<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { fetchProfile, updateProfile, updatePassword } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { USER_TYPE_OPTIONS, labelOf } from '@/utils/enums'

/** 个人中心 —— 运营账号自助维护资料与密码 */
const userStore = useUserStore()
const activeTab = ref('basic')

const profileRef = ref<FormInstance>()
const profile = reactive({
  id: 0,
  username: '',
  nickname: '',
  phone: '',
  email: '',
  remark: ''
})
const profileRules: FormRules = {
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const pwdRef = ref<FormInstance>()
const pwd = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 20, message: '长度 8 ~ 20 位', trigger: 'blur' }
  ],
  confirmPassword: [
    {
      validator: (_r, value, callback) => {
        if (value !== pwd.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function submitProfile() {
  const valid = await profileRef.value?.validate().catch(() => false)
  if (!valid) return
  await updateProfile({
    nickname: profile.nickname,
    phone: profile.phone,
    email: profile.email
  })
  ElMessage.success('资料已更新')
  await userStore.loadProfile()
}

async function submitPwd() {
  const valid = await pwdRef.value?.validate().catch(() => false)
  if (!valid) return
  await updatePassword(pwd.oldPassword, pwd.newPassword)
  ElMessage.success('密码已修改，请牢记新密码')
  pwd.oldPassword = ''
  pwd.newPassword = ''
  pwd.confirmPassword = ''
}

onMounted(async () => {
  const info = await fetchProfile()
  Object.assign(profile, {
    id: info.id,
    username: info.username,
    nickname: info.nickname,
    phone: info.phone,
    email: info.email
  })
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本资料" name="basic">
          <el-form
            ref="profileRef"
            :model="profile"
            :rules="profileRules"
            label-width="90px"
            style="max-width: 560px"
          >
            <el-form-item label="登录账号">
              <el-input :model-value="profile.username" disabled />
            </el-form-item>
            <el-form-item label="主体类型">
              <el-tag>{{ labelOf(USER_TYPE_OPTIONS, userStore.userType) }}</el-tag>
            </el-form-item>
            <el-form-item label="所属角色">
              <el-tag
                v-for="r in userStore.roles"
                :key="r"
                type="info"
                style="margin-right: 6px"
              >
                {{ r }}
              </el-tag>
              <el-text v-if="!userStore.roles?.length" type="info">未分配角色</el-text>
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="profile.nickname" />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="profile.phone" maxlength="11" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="profile.email" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="submitProfile">保存</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="修改密码" name="pwd">
          <el-form
            ref="pwdRef"
            :model="pwd"
            :rules="pwdRules"
            label-width="90px"
            style="max-width: 560px"
          >
            <el-form-item label="原密码" prop="oldPassword">
              <el-input v-model="pwd.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="pwd.newPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="pwd.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="submitPwd">提交</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>
