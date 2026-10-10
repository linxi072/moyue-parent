<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

// C 端（读者中心）外壳布局：移动端风格顶栏 + 底部 Tab 导航。
// 与 admin 布局解耦——C 端面向普通用户，不需要运营侧边栏与 RBAC 菜单。
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const tabs = [
  { path: '/consumer/home', label: '我的', icon: 'User' },
  { path: '/consumer/messages', label: '消息', icon: 'Bell' },
  { path: '/consumer/points', label: '积分', icon: 'Coin' },
  { path: '/consumer/search', label: '发现', icon: 'Compass' },
  { path: '/consumer/ai', label: '创作', icon: 'MagicStick' }
]

const active = computed(() => {
  const p = route.path
  const hit = tabs.find((t) => p === t.path || p.startsWith(t.path + '/'))
  return hit?.path || '/consumer/home'
})

function go(path: string) {
  if (path !== route.path) router.push(path)
}

function goAdmin() {
  router.push('/dashboard')
}

function onLogout() {
  ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' }).then(() => {
    userStore.logout()
    ElMessage.success('已退出')
    router.push('/login')
  })
}
</script>

<template>
  <div class="consumer-shell">
    <header class="topbar">
      <div class="brand">墨阅 · 读者中心</div>
      <div class="spacer" />
      <el-button v-if="userStore.isOperator" link size="small" @click="goAdmin">管理后台</el-button>
      <el-dropdown trigger="click" @command="onLogout">
        <span class="user" role="button" tabindex="0" aria-label="用户菜单">
          <el-avatar :size="28">{{ userStore.nickname?.charAt(0) || 'U' }}</el-avatar>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </header>

    <main class="content">
      <router-view v-slot="{ Component }">
        <component :is="Component" />
      </router-view>
    </main>

    <nav class="tabbar" aria-label="读者中心导航">
      <button
        v-for="t in tabs"
        :key="t.path"
        class="tab"
        :class="{ active: active === t.path }"
        :aria-current="active === t.path ? 'page' : undefined"
        @click="go(t.path)"
      >
        <el-icon class="tab-icon"><component :is="t.icon" /></el-icon>
        <span class="tab-label">{{ t.label }}</span>
      </button>
    </nav>
  </div>
</template>

<style scoped lang="scss">
.consumer-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--color-bg);
}

.topbar {
  height: 52px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);

  .brand {
    font-weight: 600;
    color: var(--color-text);
    letter-spacing: 1px;
  }
  .spacer { flex: 1; }
  .user {
    cursor: pointer;
    outline: none;
    display: inline-flex;
    min-height: var(--tap-min, 32px);
    align-items: center;
    border-radius: var(--radius-sm);
  }
}

.content {
  flex: 1;
  overflow: auto;
  padding: 14px;
}

.tabbar {
  flex-shrink: 0;
  height: 56px;
  display: flex;
  background: var(--color-surface);
  border-top: 1px solid var(--color-border);
  // 适配 iOS 底部安全区
  padding-bottom: env(safe-area-inset-bottom, 0);

  .tab {
    flex: 1;
    border: none;
    background: transparent;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    cursor: pointer;
    color: var(--color-text-muted);
    font-size: 12px;
    min-height: var(--tap-min, 44px);

    &.active { color: var(--color-primary); }
    .tab-icon { font-size: 20px; }
  }
}
</style>
