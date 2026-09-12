<template>
  <div class="login-page">
    <el-card class="login-card" shadow="never">
      <h1 class="login-title">墨阅小说网</h1>
      <p class="login-sub">免费读书 · 海量小说</p>
      <el-form :model="form" @submit.prevent="handleLogin">
        <el-form-item>
          <el-input v-model="form.phone" placeholder="手机号" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            size="large"
            show-password
          />
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          class="login-btn"
          :loading="loading"
          @click="handleLogin"
        >
          登录
        </el-button>
      </el-form>
      <p class="login-tip">演示账号：13800000000 / 123456</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { login as loginApi, me } from '@/api/auth';
import { useUserStore } from '@/stores/user';

const router = useRouter();
const userStore = useUserStore();
const form = reactive({ phone: '13800000000', password: '123456' });
const loading = ref(false);

async function handleLogin() {
  if (!form.phone || !form.password) {
    ElMessage.warning('请输入手机号和密码');
    return;
  }
  loading.value = true;
  try {
    const vo = await loginApi({ phone: form.phone, password: form.password });
    userStore.setToken(vo.accessToken);
    const info = await me();
    userStore.setUserInfo(info as Record<string, unknown>);
    ElMessage.success('登录成功');
    router.push('/books');
  } catch {
    // 错误已由响应拦截器统一提示
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--moyue-paper);
}
.login-card {
  width: 360px;
  padding: 8px 12px;
  border-radius: var(--moyue-radius);
  border-top: 4px solid var(--moyue-crimson);
}
.login-title {
  margin: 8px 0 0;
  text-align: center;
  color: var(--moyue-crimson);
  font-size: 26px;
}
.login-sub {
  text-align: center;
  color: var(--moyue-ink);
  opacity: 0.6;
  margin: 4px 0 16px;
}
.login-btn {
  width: 100%;
  background: var(--moyue-crimson);
  border-color: var(--moyue-crimson);
}
.login-tip {
  text-align: center;
  font-size: 12px;
  color: var(--moyue-ink);
  opacity: 0.5;
  margin: 12px 0 0;
}
</style>
