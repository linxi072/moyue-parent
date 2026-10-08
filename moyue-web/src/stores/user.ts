import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, fetchProfile } from '@/api/auth'

const TOKEN_KEY = 'moyue_token'
const REFRESH_KEY = 'moyue_refresh_token'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const refreshToken = ref<string>(localStorage.getItem(REFRESH_KEY) || '')
  const userId = ref<number>(0)
  const username = ref<string>('')
  const nickname = ref<string>('')
  const userType = ref<number>(0)
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])

  const isOperator = computed(() => userType.value === 3)
  const isLogin = computed(() => !!token.value)

  function setToken(access: string, refresh: string) {
    token.value = access
    refreshToken.value = refresh
    localStorage.setItem(TOKEN_KEY, access)
    localStorage.setItem(REFRESH_KEY, refresh)
  }

  async function login(usernameStr: string, password: string) {
    const res = await apiLogin(usernameStr, password)
    setToken(res.accessToken, res.refreshToken)
    userId.value = res.userId
    userType.value = res.userType
    roles.value = res.roles || []
    await loadProfile()
    return res
  }

  async function loadProfile() {
    const info = await fetchProfile()
    // SysUser 字段均为可选（新增态可为空），这里落到 store 时统一兜底
    userId.value = info.id ?? 0
    username.value = info.username ?? ''
    nickname.value = info.nickname || info.username || ''
    userType.value = info.userType ?? 0
    roles.value = info.roleKeys || []
    permissions.value = info.permissions || []
    return info
  }

  function hasPermission(code: string) {
    if (!code) return true
    // 超管角色在后端恒通过，这里不重复判定，前端只做按钮级隐藏
    return permissions.value.includes('*:*:*') || permissions.value.includes(code)
  }

  function logout() {
    token.value = ''
    refreshToken.value = ''
    userId.value = 0
    roles.value = []
    permissions.value = []
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_KEY)
  }

  return {
    token, refreshToken, userId, username, nickname, userType, roles, permissions,
    isOperator, isLogin, login, loadProfile, hasPermission, logout, setToken
  }
})
