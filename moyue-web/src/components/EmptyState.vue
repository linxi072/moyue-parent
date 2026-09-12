<template>
  <div class="empty-state">
    <div class="empty-mark">{{ mark }}</div>
    <p class="empty-title">{{ title }}</p>
    <p v-if="description" class="empty-desc">{{ description }}</p>
    <div v-if="$slots.action" class="empty-action">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string;
    description?: string;
    /** 顶部装饰字，用一个汉字做视觉锚点，避免引入图标依赖 */
    mark?: string;
  }>(),
  { title: '暂无数据', description: '', mark: '空' }
);
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--moyue-gap-xl) var(--moyue-gap-md);
  text-align: center;
}

.empty-mark {
  width: 56px;
  height: 56px;
  border-radius: var(--moyue-radius-pill);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
  color: rgba(201, 56, 46, 0.55);
  background: rgba(201, 56, 46, 0.08);
  margin-bottom: var(--moyue-gap);
  user-select: none;
}

.empty-title {
  margin: 0;
  font-size: var(--moyue-font);
  color: var(--moyue-text-secondary);
}

.empty-desc {
  margin: var(--moyue-gap-xs) 0 0;
  font-size: var(--moyue-font-sm);
  color: var(--moyue-text-muted);
  line-height: 1.7;
  max-width: 420px;
}

.empty-action {
  margin-top: var(--moyue-gap-md);
}
</style>
