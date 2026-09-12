<template>
  <header class="moyue-head">
    <div class="head-main">
      <button v-if="back" class="back-btn" type="button" @click="handleBack">
        <span class="back-arrow">‹</span>
        <span>返回</span>
      </button>
      <h2 class="moyue-title">{{ title }}</h2>
      <p v-if="subtitle" class="moyue-subtitle">{{ subtitle }}</p>
    </div>
    <div class="head-extra">
      <slot name="extra" />
    </div>
  </header>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';

const props = withDefaults(
  defineProps<{
    title: string;
    subtitle?: string;
    /** 是否显示返回按钮 */
    back?: boolean;
    /** 自定义返回目标，留空则 router.back() */
    backTo?: string;
  }>(),
  { subtitle: '', back: false, backTo: '' }
);

const router = useRouter();

function handleBack() {
  if (props.backTo) {
    router.push(props.backTo);
    return;
  }
  // 无历史记录（例如直接打开详情页）时兜底回首页
  if (window.history.length > 1) {
    router.back();
  } else {
    router.push('/books');
  }
}
</script>

<style scoped>
.head-main {
  min-width: 0;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 0;
  margin-bottom: var(--moyue-gap-xs);
  border: none;
  background: none;
  color: var(--moyue-text-secondary);
  font-size: var(--moyue-font-sm);
  cursor: pointer;
  transition: color 0.18s ease;
}

.back-btn:hover {
  color: var(--moyue-crimson);
}

.back-arrow {
  font-size: 16px;
  line-height: 1;
}

.head-extra {
  display: flex;
  align-items: center;
  gap: var(--moyue-gap-sm);
  flex-shrink: 0;
}
</style>
