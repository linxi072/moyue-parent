<template>
  <div class="cover" :style="boxStyle">
    <!-- 有封面且未加载失败：用 img 以拿到加载失败回调 -->
    <img
      v-if="src && !failed"
      class="cover-img"
      :src="src"
      :alt="title || '封面'"
      loading="lazy"
      decoding="async"
      @error="failed = true"
    />
    <!-- 无封面或加载失败：书名首字 + 渐变兜底，避免出现空白块 -->
    <div v-else class="cover-fallback">
      <span class="cover-initial">{{ initial }}</span>
    </div>
    <slot />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';

const props = withDefaults(
  defineProps<{
    src?: string;
    title?: string;
    /** 宽高比，如 '3 / 4'；与 height 二选一 */
    ratio?: string;
    height?: string;
    radius?: string;
  }>(),
  {
    src: '',
    title: '',
    ratio: '3 / 4',
    height: '',
    radius: 'var(--moyue-radius-sm)',
  }
);

const failed = ref(false);

// 封面地址变化时重置失败态，避免列表翻页后沿用上一次的兜底
watch(
  () => props.src,
  () => {
    failed.value = false;
  }
);

const boxStyle = computed(() => {
  const style: Record<string, string> = { borderRadius: props.radius };
  if (props.height) {
    style.height = props.height;
  } else {
    style.aspectRatio = props.ratio;
  }
  return style;
});

/** 兜底文字：取书名首字（去空白），为空则用一个书名号 */
const initial = computed(() => {
  const t = (props.title || '').trim();
  return t ? t.charAt(0) : '墨';
});
</script>

<style scoped>
.cover {
  position: relative;
  width: 100%;
  overflow: hidden;
  background: linear-gradient(135deg, var(--moyue-crimson-deep), var(--moyue-ink));
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-initial {
  font-size: 2.2em;
  font-weight: 700;
  color: rgba(247, 245, 241, 0.82);
  letter-spacing: 2px;
  user-select: none;
}
</style>
