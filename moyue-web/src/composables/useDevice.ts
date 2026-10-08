/**
 * useDevice.ts —— 设备形态检测
 *
 * <p>以 992px 为布局断点（笔记本/桌面 vs 平板/手机）：
 * ≥992px 为 desktop（侧边栏常驻、可折叠）；&lt;992px 为 mobile（侧边栏改为抽屉）。
 * 通过 store 单一事实来源驱动布局，避免各页面各自判断视口。
 */
import { onMounted, onUnmounted } from 'vue'
import { useAppStore } from '@/stores/app'

const LAYOUT_BREAKPOINT = 992

function detect(): 'desktop' | 'mobile' {
  if (typeof window === 'undefined') return 'desktop'
  return window.innerWidth < LAYOUT_BREAKPOINT ? 'mobile' : 'desktop'
}

export function useDevice() {
  const app = useAppStore()

  function onResize() {
    const next = detect()
    // 进入桌面形态时自动收起移动抽屉，避免状态残留
    if (next === 'desktop' && app.device === 'mobile') {
      app.closeMobileNav()
    }
    app.setDevice(next)
  }

  onMounted(() => {
    app.setDevice(detect())
    window.addEventListener('resize', onResize)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', onResize)
  })
}
