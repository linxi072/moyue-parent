import { createRouter, createWebHistory } from 'vue-router';
import { useUserStore } from '@/stores/user';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue'),
    },
    {
      path: '/',
      component: () => import('@/layout/DefaultLayout.vue'),
      children: [
        { path: '', redirect: '/books' },
        {
          path: 'books',
          name: 'books',
          component: () => import('@/views/BookList.vue'),
        },
      ],
    },
  ],
});

// 路由守卫：未登录跳 /login，已登录访问 /login 跳 /books
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
