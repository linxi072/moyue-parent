import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { useUserStore } from '@/stores/user';

/**
 * 路由表。
 * 所有业务页均懒加载；管理后台页面对非管理员同样放开访问，
 * 由后端 AdminRoleInterceptor 做最终断言（前端隐藏只是体验优化，不是安全边界）。
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('@/layout/DefaultLayout.vue'),
    children: [
      { path: '', redirect: '/books' },

      // ---------------- 书城与阅读 ----------------
      {
        path: 'books',
        name: 'books',
        component: () => import('@/views/BookList.vue'),
        meta: { title: '书城' },
      },
      {
        path: 'books/:id',
        name: 'book-detail',
        component: () => import('@/views/BookDetail.vue'),
        meta: { title: '书籍详情' },
      },
      {
        path: 'read/:bookId/:chapterId',
        name: 'chapter-reader',
        component: () => import('@/views/ChapterReader.vue'),
        meta: { title: '阅读' },
      },
      {
        path: 'bookshelf',
        name: 'bookshelf',
        component: () => import('@/views/Bookshelf.vue'),
        meta: { title: '我的书架' },
      },

      // ---------------- 作者创作 ----------------
      {
        path: 'author/works',
        name: 'author-works',
        component: () => import('@/views/AuthorWorks.vue'),
        meta: { title: '我的作品' },
      },
      {
        path: 'author/works/:bookId/chapters',
        name: 'chapter-editor',
        component: () => import('@/views/ChapterEditor.vue'),
        meta: { title: '章节管理' },
      },

      // ---------------- 社区 ----------------
      {
        path: 'blog',
        name: 'blog-list',
        component: () => import('@/views/BlogList.vue'),
        meta: { title: '博客广场' },
      },
      {
        path: 'blog/:id',
        name: 'blog-detail',
        component: () => import('@/views/BlogDetail.vue'),
        meta: { title: '帖子详情' },
      },
      {
        path: 'im',
        name: 'chat',
        component: () => import('@/views/Chat.vue'),
        meta: { title: '即时通讯' },
      },

      // ---------------- 账户与积分 ----------------
      {
        path: 'points',
        name: 'points-mall',
        component: () => import('@/views/PointsMall.vue'),
        meta: { title: '积分商城' },
      },
      {
        path: 'rewards',
        name: 'my-rewards',
        component: () => import('@/views/MyRewards.vue'),
        meta: { title: '打赏与稿酬' },
      },
      {
        path: 'messages',
        name: 'messages',
        component: () => import('@/views/Messages.vue'),
        meta: { title: '消息中心' },
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/Profile.vue'),
        meta: { title: '个人中心' },
      },

      // ---------------- 管理后台 ----------------
      {
        path: 'admin/stats',
        name: 'admin-stats',
        component: () => import('@/views/admin/Stats.vue'),
        meta: { title: '数据概览', admin: true },
      },
      {
        path: 'admin/audit',
        name: 'admin-audit',
        component: () => import('@/views/admin/AuditTasks.vue'),
        meta: { title: '内容审核', admin: true },
      },
      {
        path: 'admin/announcements',
        name: 'admin-announcements',
        component: () => import('@/views/admin/Announcements.vue'),
        meta: { title: '公告管理', admin: true },
      },
      {
        path: 'admin/orders',
        name: 'admin-orders',
        component: () => import('@/views/admin/Orders.vue'),
        meta: { title: '订单对账', admin: true },
      },
      {
        path: 'admin/users',
        name: 'admin-users',
        component: () => import('@/views/admin/Users.vue'),
        meta: { title: '用户管理', admin: true },
      },
    ],
  },
  // 兜底：未匹配地址回书城
  { path: '/:pathMatch(.*)*', redirect: '/books' },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  // 切页后回到顶部：否则从长列表点进详情页会停在中间的滚动位置
  scrollBehavior(_to, _from, savedPosition) {
    return savedPosition || { top: 0 };
  },
});

/**
 * 路由守卫：未登录跳 /login，已登录访问 /login 跳 /books。
 * 角色只影响菜单展示，不做前端拦截——真实权限由后端断言。
 */
router.beforeEach((to) => {
  const userStore = useUserStore();
  if (to.path !== '/login' && !userStore.token) {
    return '/login';
  }
  if (to.path === '/login' && userStore.token) {
    return '/books';
  }
  return true;
});

export default router;
