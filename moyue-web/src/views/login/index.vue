<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
// 注意：后端 V12 初始化数据的真实默认密码为 123456（非注释中的 Admin@123）
const form = reactive({ username: 'admin', password: '123456' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  await formRef.value?.validate()
  loading.value = true
  try {
    await userStore.login(form.username, form.password)
    if (!userStore.isOperator) {
      ElMessage.warning('当前账号不是运营主体，无法进入后台')
      userStore.logout()
      return
    }
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.push(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <span class="brand-logo">墨</span>
        <h2>墨阅小说网 · 运营管理后台</h2>
      </div>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入账号"
            :prefix-icon="'User'"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            :prefix-icon="'Lock'"
            autocomplete="current-password"
          />
        </el-form-item>
        <el-button type="primary" class="submit" :loading="loading" @click="handleLogin">
          登 录
        </el-button>
      </el-form>
      <p class="tip">初始账号 admin / 123456，首次登录后请立即修改密码</p>
    </div>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-4);
  background: linear-gradient(135deg, #5b7fff 0%, #7b5bff 100%);
}

.login-card {
  width: min(380px, 92vw);
  padding: var(--space-8);
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-pop);
  animation: card-in 0.32s ease both;

  .brand {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    margin-bottom: var(--space-6);

    .brand-logo {
      width: 40px;
      height: 40px;
      border-radius: var(--radius-md);
      background: var(--color-primary);
      color: #fff;
      font-weight: 700;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }

    h2 {
      margin: 0;
      font-size: 18px;
      font-weight: 600;
      line-height: 1.3;
    }
  }

  .submit {
    width: 100%;
    min-height: var(--tap-min);
    letter-spacing: 4px;
  }

  .tip {
    margin: var(--space-4) 0 0;
    font-size: 12px;
    color: var(--color-text-muted);
    text-align: center;
  }
}

@keyframes card-in {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

// 手机：缩小内边距，输入框更大更易点
@media (max-width: 575.98px) {
  .login-card {
    padding: var(--space-6) var(--space-5);
  }
}
</style>
