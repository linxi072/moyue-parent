import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const Layout = () => import('@/layouts/admin/index.vue')

/**
 * 路由表。
 *
 * <p>菜单与路由的关系：后端 sys_menu 管的是「菜单与权限」，前端这份是「路由与组件」。
 * 两者通过 path 对应；侧边栏由后端菜单树驱动（动态菜单），这里保留全量路由，
 * 未授权的访问由后端 @RequiresPermissions 拦截 —— 前端隐藏只是体验优化，不是安全边界。
 */
export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', icon: 'Odometer' }
      }
    ]
  },
  {
    path: '/system',
    component: Layout,
    redirect: '/system/user',
    meta: { title: '组织权限', icon: 'Setting' },
    children: [
      {
        path: 'user',
        name: 'SystemUser',
        component: () => import('@/views/system/user/index.vue'),
        meta: { title: '用户管理', icon: 'User' }
      },
      {
        path: 'role',
        name: 'SystemRole',
        component: () => import('@/views/system/role/index.vue'),
        meta: { title: '角色管理', icon: 'UserFilled' }
      },
      {
        path: 'menu',
        name: 'SystemMenu',
        component: () => import('@/views/system/menu/index.vue'),
        meta: { title: '菜单管理', icon: 'Menu' }
      },
      {
        path: 'dept',
        name: 'SystemDept',
        component: () => import('@/views/system/dept/index.vue'),
        meta: { title: '部门管理', icon: 'OfficeBuilding' }
      },
      {
        path: 'dict/type',
        name: 'SystemDictType',
        component: () => import('@/views/system/dict/type.vue'),
        meta: { title: '字典类型', icon: 'Collection' }
      },
      {
        path: 'dict/data',
        name: 'SystemDictData',
        component: () => import('@/views/system/dict/data.vue'),
        meta: { title: '字典数据', icon: 'CollectionTag' }
      },
      {
        path: 'config',
        name: 'SystemConfig',
        component: () => import('@/views/system/config/index.vue'),
        meta: { title: '参数管理', icon: 'Tools' }
      },
      {
        path: 'profile',
        name: 'SystemProfile',
        component: () => import('@/views/system/profile/index.vue'),
        meta: { title: '个人中心', icon: 'Avatar', hidden: true }
      }
    ]
  },
  {
    path: '/monitor',
    component: Layout,
    redirect: '/monitor/operlog',
    meta: { title: '日志与监控', icon: 'Monitor' },
    children: [
      {
        path: 'operlog',
        name: 'MonitorOperLog',
        component: () => import('@/views/monitor/operlog/index.vue'),
        meta: { title: '操作日志', icon: 'Document' }
      },
      {
        path: 'loginlog',
        name: 'MonitorLoginLog',
        component: () => import('@/views/monitor/loginlog/index.vue'),
        meta: { title: '登录日志', icon: 'DocumentCopy' }
      },
      {
        path: 'online',
        name: 'MonitorOnline',
        component: () => import('@/views/monitor/online/index.vue'),
        meta: { title: '在线用户', icon: 'Connection' }
      },
      {
        path: 'server',
        name: 'MonitorServer',
        component: () => import('@/views/monitor/server/index.vue'),
        meta: { title: '服务监控', icon: 'Cpu' }
      },
      {
        path: 'server/jvm',
        name: 'MonitorJvm',
        component: () => import('@/views/monitor/server/jvm.vue'),
        meta: { title: 'JVM 信息', icon: 'Cpu', hidden: true }
      },
      {
        path: 'server/disk',
        name: 'MonitorDisk',
        component: () => import('@/views/monitor/server/disk.vue'),
        meta: { title: '磁盘状态', icon: 'FolderOpened', hidden: true }
      },
      {
        path: 'cache',
        name: 'MonitorCache',
        component: () => import('@/views/monitor/cache/index.vue'),
        meta: { title: '缓存监控', icon: 'Coin' }
      },
      {
        path: 'cache/key',
        name: 'MonitorCacheKey',
        component: () => import('@/views/monitor/cache/key.vue'),
        meta: { title: '缓存键浏览', icon: 'Key', hidden: true }
      },
      {
        path: 'pool',
        name: 'MonitorPool',
        component: () => import('@/views/monitor/pool/index.vue'),
        meta: { title: '连接池监视', icon: 'DataAnalysis' }
      },
      {
        path: 'pool/sql',
        name: 'MonitorSql',
        component: () => import('@/views/monitor/pool/sql.vue'),
        meta: { title: 'SQL 监控', icon: 'Tickets', hidden: true }
      }
    ]
  },
  {
    path: '/tool',
    component: Layout,
    redirect: '/tool/job',
    meta: { title: '运维工具', icon: 'Operation' },
    children: [
      {
        path: 'job',
        name: 'ToolJob',
        component: () => import('@/views/tool/job/index.vue'),
        meta: { title: '定时任务', icon: 'Timer' }
      },
      {
        path: 'job/log',
        name: 'ToolJobLog',
        component: () => import('@/views/tool/job/log.vue'),
        meta: { title: '任务日志', icon: 'Notebook', hidden: true }
      },
      {
        path: 'api',
        name: 'ToolApi',
        component: () => import('@/views/tool/api/index.vue'),
        meta: { title: '系统接口', icon: 'Link' }
      },
      {
        path: 'generator/config',
        name: 'ToolGenConfig',
        component: () => import('@/views/tool/generator/config.vue'),
        meta: { title: '代码生成', icon: 'MagicStick' }
      },
      {
        path: 'generator/import',
        name: 'ToolGenImport',
        component: () => import('@/views/tool/generator/import.vue'),
        meta: { title: '导入表结构', icon: 'Download', hidden: true }
      },
      {
        path: 'generator/preview',
        name: 'ToolGenPreview',
        component: () => import('@/views/tool/generator/preview.vue'),
        meta: { title: '代码预览', icon: 'View', hidden: true }
      }
    ]
  },
  {
    path: '/form',
    component: Layout,
    redirect: '/form/list',
    meta: { title: '在线构建器', icon: 'EditPen' },
    children: [
      {
        path: 'list',
        name: 'FormList',
        component: () => import('@/views/form/list.vue'),
        meta: { title: '表单管理', icon: 'Files' }
      },
      {
        path: 'designer',
        name: 'FormDesigner',
        component: () => import('@/views/form/designer.vue'),
        meta: { title: '表单设计器', icon: 'MagicStick', hidden: true }
      },
      {
        path: 'data',
        name: 'FormData',
        component: () => import('@/views/form/data.vue'),
        meta: { title: '收集数据', icon: 'Tickets', hidden: true }
      },
      {
        path: 'preview',
        name: 'FormPreview',
        component: () => import('@/views/form/preview.vue'),
        meta: { title: '表单预览', icon: 'View', hidden: true }
      }
    ]
  },
  {
    path: '/401',
    name: 'Error401',
    component: () => import('@/views/error/401.vue'),
    meta: { title: '无权限', public: true, hidden: true }
  },
  {
    path: '/404',
    name: 'Error404',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', public: true, hidden: true }
  },
  { path: '/:pathMatch(.*)*', redirect: '/404' },
  {
    path: '/content',
    component: Layout,
    redirect: '/content/chapter',
    meta: { title: '内容运营', icon: 'Notebook' },
    children: [
      {
        path: 'book',
        name: 'ContentBook',
        component: () => import('@/views/content/book/index.vue'),
        meta: { title: '作品管理', icon: 'Reading' }
      },
      {
        path: 'chapter',
        name: 'ContentChapter',
        component: () => import('@/views/content/chapter/index.vue'),
        meta: { title: '章节管理', icon: 'Document' }
      },
      {
        path: 'bookshelf',
        name: 'ContentBookshelf',
        component: () => import('@/views/content/bookshelf/index.vue'),
        meta: { title: '用户书架', icon: 'Collection' }
      }
    ]
  },
  {
    path: '/social',
    component: Layout,
    redirect: '/social/comment',
    meta: { title: '互动运营', icon: 'ChatDotRound' },
    children: [
      {
        path: 'comment',
        name: 'SocialComment',
        component: () => import('@/views/social/comment/index.vue'),
        meta: { title: '评论管理', icon: 'ChatLineRound' }
      },
      {
        path: 'im',
        name: 'SocialIm',
        component: () => import('@/views/social/im/index.vue'),
        meta: { title: '即时通讯', icon: 'ChatRound' }
      }
    ]
  },
  {
    path: '/commerce',
    component: Layout,
    redirect: '/commerce/order',
    meta: { title: '商业化', icon: 'Goods' },
    children: [
      {
        path: 'order',
        name: 'CommerceOrder',
        component: () => import('@/views/commerce/order/index.vue'),
        meta: { title: '付费订单', icon: 'List' }
      },
      {
        path: 'product',
        name: 'CommerceProduct',
        component: () => import('@/views/commerce/product/index.vue'),
        meta: { title: '兑换商品', icon: 'GoodsFilled' }
      },
      {
        path: 'points',
        name: 'CommercePoints',
        component: () => import('@/views/commerce/points/index.vue'),
        meta: { title: '积分管理', icon: 'Coin' }
      }
    ]
  },
  {
    path: '/search',
    component: Layout,
    redirect: '/search/hotword',
    meta: { title: '搜索运营', icon: 'Search' },
    children: [
      {
        path: 'hotword',
        name: 'SearchHotWord',
        component: () => import('@/views/search/hotword/index.vue'),
        meta: { title: '搜索热词', icon: 'Top' }
      },
      {
        path: 'blockword',
        name: 'SearchBlockWord',
        component: () => import('@/views/search/blockword/index.vue'),
        meta: { title: '屏蔽词', icon: 'CircleClose' }
      }
    ]
  },
  {
    path: '/message',
    component: Layout,
    redirect: '/message/index',
    meta: { title: '消息运营', icon: 'Bell' },
    children: [
      {
        path: 'index',
        name: 'MessageIndex',
        component: () => import('@/views/message/index.vue'),
        meta: { title: '站内信', icon: 'Message' }
      },
      {
        path: 'template',
        name: 'MessageTemplate',
        component: () => import('@/views/message/template/index.vue'),
        meta: { title: '消息模板', icon: 'Document' }
      }
    ]
  },
  {
    path: '/risk',
    component: Layout,
    redirect: '/risk/audit',
    meta: { title: '风控审核', icon: 'Warning' },
    children: [
      {
        path: 'audit',
        name: 'RiskAudit',
        component: () => import('@/views/risk/audit/index.vue'),
        meta: { title: '审核工单', icon: 'Stamp' }
      },
      {
        path: 'report',
        name: 'RiskReport',
        component: () => import('@/views/risk/report/index.vue'),
        meta: { title: '举报处理', icon: 'WarningFilled' }
      },
      {
        path: 'sensitive',
        name: 'RiskSensitive',
        component: () => import('@/views/risk/sensitive/index.vue'),
        meta: { title: '敏感词', icon: 'CircleClose' }
      }
    ]
  },
  {
    path: '/ai',
    component: Layout,
    redirect: '/ai/task',
    meta: { title: 'AI 创作', icon: 'MagicStick' },
    children: [
      {
        path: 'task',
        name: 'AiTask',
        component: () => import('@/views/ai/task/index.vue'),
        meta: { title: 'AI 任务', icon: 'Cpu' }
      },
      {
        path: 'quota',
        name: 'AiQuota',
        component: () => import('@/views/ai/quota/index.vue'),
        meta: { title: '配额管理', icon: 'Coin' }
      }
    ]
  },
  {
    // ============ C 端（读者中心，G-L 系列）============
    // 面向普通用户，独立于 admin 布局；meta.consumer 让守卫跳过 operator 校验（对齐后端 /api/v1/{module} 口径）。
    path: '/consumer',
    component: () => import('@/layouts/consumer/index.vue'),
    redirect: '/consumer/home',
    meta: { title: '读者中心', consumer: true },
    children: [
      {
        path: 'home',
        name: 'ConsumerHome',
        component: () => import('@/views/consumer/home/index.vue'),
        meta: { title: '我的', consumer: true }
      },
      {
        path: 'messages',
        name: 'ConsumerMessages',
        component: () => import('@/views/consumer/messages/index.vue'),
        meta: { title: '消息', consumer: true }
      },
      {
        path: 'points',
        name: 'ConsumerPoints',
        component: () => import('@/views/consumer/points/index.vue'),
        meta: { title: '积分', consumer: true }
      },
      {
        path: 'search',
        name: 'ConsumerSearch',
        component: () => import('@/views/consumer/search/index.vue'),
        meta: { title: '发现', consumer: true }
      },
      {
        path: 'ai',
        name: 'ConsumerAi',
        component: () => import('@/views/consumer/ai/index.vue'),
        meta: { title: '创作', consumer: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.meta.public) {
    return true
  }
  if (!userStore.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  // C 端（消费者）路由：任意已登录用户即可访问，无需运营角色（对齐后端 G-L 系列 /api/v1/{module}）
  if (to.meta.consumer) {
    return true
  }
  // 后台要求运营主体（与后端 AdminRoleInterceptor 口径一致）
  if (!userStore.isOperator && userStore.userId) {
    return '/401'
  }
  return true
})

export default router
