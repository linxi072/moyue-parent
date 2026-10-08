import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAppStore = defineStore('app', () => {
  /** 桌面端：侧边栏是否折叠为 64px 图标栏 */
  const collapsed = ref<boolean>(false)
  /** 设备形态：desktop（常驻侧栏）/ mobile（抽屉侧栏） */
  const device = ref<'desktop' | 'mobile'>('desktop')
  /** 移动端：抽屉侧栏是否展开 */
  const mobileNavOpen = ref<boolean>(false)

  const isMobile = computed(() => device.value === 'mobile')

  /** 桌面态折叠 / 移动态开合抽屉，按设备分支 */
  function toggleSidebar() {
    if (isMobile.value) {
      mobileNavOpen.value = !mobileNavOpen.value
    } else {
      collapsed.value = !collapsed.value
    }
  }

  function closeMobileNav() {
    mobileNavOpen.value = false
  }

  function setDevice(d: 'desktop' | 'mobile') {
    device.value = d
  }

  return { collapsed, device, mobileNavOpen, isMobile, toggleSidebar, closeMobileNav, setDevice }
})
