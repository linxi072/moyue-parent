<template>
  <div class="default-layout">
    <header class="top-bar">
      <div class="brand">墨阅小说网</div>
      <div class="user-area">
        <span class="user-name">{{ userName }}</span>
        <el-button text type="primary" @click="handleLogout">退出登录</el-button>
      </div>
    </header>
    <main class="content">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';

const router = useRouter();
const userStore = useUserStore();

const userName = computed(() => {
  const info = userStore.userInfo as Record<string, unknown> | null;
  return (info && (info.nickname as string)) || '读者';
});

function handleLogout() {
  userStore.logout();
  router.push('/login');
}
</script>

<style scoped>
.default-layout {
  min-height: 100vh;
}
.top-bar {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: var(--moyue-ink);
  color: var(--moyue-paper);
}
.brand {
  font-size: 20px;
  font-weight: 700;
  color: var(--moyue-crimson);
}
.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}
.user-name {
  font-size: 14px;
}
.content {
  padding: 8px 16px;
}
</style>
