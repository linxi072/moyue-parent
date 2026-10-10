<template>
  <div class="consumer-page home">
    <section class="profile-card">
      <el-avatar :size="56">{{ userStore.nickname?.charAt(0) || 'U' }}</el-avatar>
      <div class="profile-meta">
        <div class="name">{{ userStore.nickname || userStore.username }}</div>
        <div class="sub">UID {{ userStore.userId }} · {{ userStore.isOperator ? '运营' : '读者' }}</div>
      </div>
    </section>

    <section class="stat-row">
      <div class="stat" @click="go('/consumer/messages')">
        <div class="num">{{ unread }}</div>
        <div class="lbl">未读消息</div>
      </div>
      <div class="stat" @click="go('/consumer/points')">
        <div class="num">{{ balance }}</div>
        <div class="lbl">积分余额</div>
      </div>
      <div class="stat" @click="go('/consumer/ai')">
        <div class="num">{{ quotaRemain }}</div>
        <div class="lbl">AI 余量</div>
      </div>
    </section>

    <section class="entry-list">
      <div v-for="e in entries" :key="e.path" class="entry" @click="go(e.path)">
        <el-icon class="entry-icon"><component :is="e.icon" /></el-icon>
        <span class="entry-label">{{ e.label }}</span>
        <el-icon class="entry-arrow"><ArrowRight /></el-icon>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { myUnreadCount } from '@/api/message'
import { myPointsBalance } from '@/api/commerce'
import { myQuota } from '@/api/ai'

// 读者中心首页：聚合各 C 端模块的关键数字，作为进入各功能的入口。
const router = useRouter()
const userStore = useUserStore()
const unread = ref(0)
const balance = ref(0)
const quotaRemain = ref(0)

const entries = [
  { path: '/consumer/messages', label: '我的消息', icon: 'Bell' },
  { path: '/consumer/points', label: '积分钱包', icon: 'Coin' },
  { path: '/consumer/search', label: '搜索发现', icon: 'Compass' },
  { path: '/consumer/ai', label: 'AI 创作', icon: 'MagicStick' }
]

function go(p: string) {
  router.push(p)
}

async function loadSummary() {
  // 单点失败不影响首页其余展示
  try {
    const [u, b, q] = await Promise.all([myUnreadCount(), myPointsBalance(), myQuota()])
    unread.value = u ?? 0
    balance.value = b ?? 0
    quotaRemain.value = q?.remain ?? 0
  } catch {
    /* 忽略：首页摘要非关键路径 */
  }
}

onMounted(loadSummary)
</script>

<style scoped lang="scss">
.home {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.profile-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 16px;
  .profile-meta .name { font-size: 16px; font-weight: 600; color: var(--color-text); }
  .profile-meta .sub { font-size: 12px; color: var(--color-text-muted); margin-top: 4px; }
}
.stat-row {
  display: flex;
  gap: 10px;
  .stat {
    flex: 1;
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-sm);
    padding: 14px 8px;
    text-align: center;
    cursor: pointer;
    .num { font-size: 20px; font-weight: 700; color: var(--color-primary); }
    .lbl { font-size: 12px; color: var(--color-text-muted); margin-top: 4px; }
  }
}
.entry-list {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
  .entry {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 14px 12px;
    cursor: pointer;
    border-bottom: 1px solid var(--color-border);
    &:last-child { border-bottom: none; }
    .entry-icon { font-size: 18px; color: var(--color-primary); }
    .entry-label { flex: 1; color: var(--color-text); }
    .entry-arrow { color: var(--color-text-muted); }
  }
}
</style>
