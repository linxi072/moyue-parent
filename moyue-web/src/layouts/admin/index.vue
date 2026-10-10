<script setup lang="ts">
import { computed, ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { routes } from '@/router'
import { getCurrentMenus } from '@/api/menu'
import type { SysMenu } from '@/api/types'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

interface MenuNode {
  path: string
  title: string
  icon?: string
  children?: MenuNode[]
}

// 静态兜底：由路由表推导（菜单迁移未跑 / 接口异常时保证侧边栏不空）
const fallbackMenus = computed<MenuNode[]>(() => {
  const result: MenuNode[] = []
  for (const r of routes) {
    if (!r.meta?.title || r.meta.hidden) continue
    const children = (r.children || [])
      .filter((c) => c.meta?.title && !c.meta?.hidden)
      .map((c) => ({
        path: r.path === '/' ? '/' + c.path : `${r.path}/${c.path}`,
        title: c.meta!.title as string,
        icon: c.meta?.icon as string
      }))
    if (children.length === 1) {
      result.push(children[0])
    } else if (children.length > 1) {
      result.push({
        path: r.path,
        title: r.meta.title as string,
        icon: r.meta.icon as string,
        children
      })
    }
  }
  return result
})

// 后端动态菜单：/menus/current 返回当前角色可见菜单树（RBAC，G-H）
const dynamicMenus = ref<MenuNode[] | null>(null)

function toNodes(list: SysMenu[]): MenuNode[] {
  return (list || [])
    .filter((m) => m.menuName && m.path)
    .map((m) => ({
      path: m.path as string,
      title: m.menuName as string,
      icon: m.icon || undefined,
      children: m.children && m.children.length ? toNodes(m.children) : undefined
    }))
}

onMounted(() => {
  getCurrentMenus()
    .then((list) => {
      const nodes = toNodes(list || [])
      if (nodes.length) dynamicMenus.value = nodes
    })
    .catch(() => {
      // 接口异常：保留静态兜底，侧边栏不空
    })
})

// 优先用后端动态菜单，未就绪时回退静态路由推导
const menus = computed<MenuNode[]>(() => dynamicMenus.value ?? fallbackMenus.value)

const breadcrumbs = computed(() =>
  route.matched.filter((m) => m.meta?.title).map((m) => m.meta.title as string)
)

// 移动端：路由切换后自动收起抽屉
watch(
  () => route.path,
  () => appStore.closeMobileNav()
)

function handleCommand(cmd: string) {
  if (cmd === 'profile') {
    router.push('/system/profile')
  } else if (cmd === 'logout') {
    ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' }).then(() => {
      userStore.logout()
      ElMessage.success('已退出')
      router.push('/login')
    })
  }
}
</script>

<template>
  <div class="admin-layout" :class="{ 'mobile-nav-open': appStore.mobileNavOpen }">
    <aside
      class="sidebar"
      :class="{ collapsed: appStore.collapsed }"
      :aria-hidden="appStore.isMobile && !appStore.mobileNavOpen"
    >
      <div class="logo">
        <span v-if="!appStore.collapsed">墨阅小说网</span>
        <span v-else>墨</span>
      </div>
      <el-scrollbar>
        <el-menu
          :default-active="route.path"
          :collapse="!appStore.isMobile && appStore.collapsed"
          :collapse-transition="false"
          background-color="#1f2430"
          text-color="#bfcbd9"
          active-text-color="#5b7fff"
          router
        >
          <template v-for="menu in menus" :key="menu.path">
            <el-sub-menu v-if="menu.children" :index="menu.path">
              <template #title>
                <el-icon><component :is="menu.icon" /></el-icon>
                <span>{{ menu.title }}</span>
              </template>
              <el-menu-item v-for="child in menu.children" :key="child.path" :index="child.path">
                <el-icon><component :is="child.icon" /></el-icon>
                <span>{{ child.title }}</span>
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="menu.path">
              <el-icon><component :is="menu.icon" /></el-icon>
              <template #title>{{ menu.title }}</template>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>
    </aside>

    <!-- 移动端抽屉遮罩 -->
    <Teleport to="body">
      <transition name="fade">
        <div
          v-if="appStore.isMobile && appStore.mobileNavOpen"
          class="mobile-mask"
          @click="appStore.closeMobileNav()"
        />
      </transition>
    </Teleport>

    <section class="main">
      <header class="header">
        <el-icon
          class="fold-btn"
          :aria-label="appStore.isMobile ? '打开菜单' : '折叠侧边栏'"
          role="button"
          tabindex="0"
          @click="appStore.toggleSidebar()"
          @keyup.enter="appStore.toggleSidebar()"
        >
          <component :is="appStore.collapsed ? 'Expand' : 'Fold'" />
        </el-icon>
        <el-breadcrumb separator="/" class="crumbs">
          <el-breadcrumb-item v-for="(b, i) in breadcrumbs" :key="i">{{ b }}</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="spacer" />
        <el-dropdown @command="handleCommand">
          <span class="user-entry">
            <el-avatar :size="28">{{ userStore.nickname?.charAt(0) || 'U' }}</el-avatar>
            <span class="ml-2 nickname">{{ userStore.nickname }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <main class="content">
        <router-view v-slot="{ Component }">
          <keep-alive :max="8">
            <component :is="Component" :key="route.path" />
          </keep-alive>
        </router-view>
      </main>
    </section>
  </div>
</template>

<style scoped lang="scss">
.admin-layout {
  display: flex;
  height: 100%;
}

.sidebar {
  width: 220px;
  background: var(--color-bg-sidebar);
  transition: width 0.2s, transform 0.25s ease;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  z-index: 1001;

  &.collapsed {
    width: 64px;
  }

  .logo {
    height: 56px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-weight: 600;
    letter-spacing: 2px;
  }
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.header {
  height: 56px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 0 var(--space-4);

  .fold-btn {
    font-size: 20px;
    cursor: pointer;
    min-width: var(--tap-min);
    min-height: var(--tap-min);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--radius-sm);
  }

  .spacer {
    flex: 1;
  }

  .user-entry {
    display: flex;
    align-items: center;
    cursor: pointer;
    outline: none;
    min-height: var(--tap-min);
    padding: 0 var(--space-1);
    border-radius: var(--radius-sm);
  }
}

.content {
  flex: 1;
  overflow: auto;
  background: var(--color-bg);
}

// ---------- 移动端（< 992px）：侧边栏改为 off-canvas 抽屉 ----------
@media (max-width: 991.98px) {
  .sidebar {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    width: 220px !important;
    transform: translateX(-100%);
    box-shadow: var(--shadow-pop);
  }

  .admin-layout.mobile-nav-open .sidebar {
    transform: translateX(0);
  }

  // 移动端始终展示完整菜单文字（不折叠为图标）
  :deep(.el-menu:not(.el-menu--collapse)) {
    width: 220px;
  }

  .header {
    padding: 0 var(--space-3);
  }

  // 手机（< 576px）隐藏面包屑，节省空间
  .crumbs {
    display: none;
  }
}

@media (max-width: 575.98px) {
  .header .nickname {
    display: none;
  }
}

.mobile-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 1000;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
