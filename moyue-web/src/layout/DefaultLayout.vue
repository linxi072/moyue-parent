<template>
  <div class="default-layout">
    <header class="top-bar">
      <div class="brand" @click="goHome">墨阅小说网</div>
      <div class="user-area">
        <el-tag size="small" :type="roleTagType">{{ roleName }}</el-tag>
        <span class="user-name">{{ userName }}</span>
        <el-button text type="primary" @click="goProfile">个人中心</el-button>
        <el-button text @click="handleLogout">退出登录</el-button>
      </div>
    </header>

    <div class="body">
      <aside class="side-nav">
        <el-menu :default-active="activePath" router class="side-menu">
          <el-menu-item index="/books">
            <span>书城</span>
          </el-menu-item>

          <el-sub-menu index="mine">
            <template #title><span>我的</span></template>
            <el-menu-item index="/bookshelf">我的书架</el-menu-item>
            <el-menu-item index="/messages">消息中心</el-menu-item>
            <el-menu-item index="/points">积分商城</el-menu-item>
            <el-menu-item index="/rewards">打赏与稿酬</el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="author">
            <template #title><span>创作</span></template>
            <el-menu-item index="/author/works">我的作品</el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="community">
            <template #title><span>社区</span></template>
            <el-menu-item index="/blog">博客广场</el-menu-item>
            <el-menu-item index="/im">即时通讯</el-menu-item>
          </el-sub-menu>

          <el-sub-menu v-if="isAdmin" index="admin">
            <template #title><span>管理后台</span></template>
            <el-menu-item index="/admin/stats">数据概览</el-menu-item>
            <el-menu-item index="/admin/audit">内容审核</el-menu-item>
            <el-menu-item index="/admin/announcements">公告管理</el-menu-item>
            <el-menu-item index="/admin/orders">订单对账</el-menu-item>
            <el-menu-item index="/admin/users">用户管理</el-menu-item>
          </el-sub-menu>
        </el-menu>
      </aside>

      <main class="content">
        <!-- 统一内容宽度：各页面不必各自维护 max-width，阅读器等需要窄栏的页面自行再收窄 -->
        <div class="content-inner moyue-page">
          <router-view v-slot="{ Component }">
            <transition name="fade" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { ROLE_TEXT } from '@/api/types';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

const info = computed(() => (userStore.userInfo || {}) as Record<string, unknown>);

const userName = computed(() => (info.value.nickname as string) || '读者');

/** 角色：1 读者 / 2 作者 / 3 管理员；缺省按读者展示 */
const role = computed(() => Number(info.value.role || 1));

const roleName = computed(() => ROLE_TEXT[role.value] || '读者');

const roleTagType = computed(() => (role.value === 3 ? 'danger' : role.value === 2 ? 'warning' : 'info'));

const isAdmin = computed(() => role.value === 3);

/** 菜单高亮：详情页归属到其所属的一级栏目 */
const activePath = computed(() => {
  const p = route.path;
  if (p.startsWith('/books') || p.startsWith('/read/')) return '/books';
  if (p.startsWith('/author')) return '/author/works';
  if (p.startsWith('/blog')) return '/blog';
  if (p.startsWith('/admin')) return p;
  return p;
});

function goHome() {
  router.push('/books');
}

function goProfile() {
  router.push('/profile');
}

function handleLogout() {
  userStore.logout();
  router.push('/login');
}
</script>

<style scoped>
.default-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.top-bar {
  height: 56px;
  flex: 0 0 56px;
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
  cursor: pointer;
  user-select: none;
}
.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}
.user-name {
  font-size: 14px;
}
.body {
  flex: 1;
  display: flex;
  min-height: 0;
}
.side-nav {
  flex: 0 0 200px;
  background: #fff;
  border-right: 1px solid rgba(38, 34, 30, 0.08);
  overflow-y: auto;
}
.side-menu {
  border-right: none;
}
.content {
  flex: 1;
  padding: var(--moyue-gap-md);
  min-width: 0;
  background: var(--moyue-paper);
  overflow-y: auto;
}
.content-inner {
  width: 100%;
}
</style>
