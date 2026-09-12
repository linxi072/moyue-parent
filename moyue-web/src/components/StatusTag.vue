<template>
  <el-tag :type="tone" :size="size" effect="light" disable-transitions>
    {{ text }}
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = withDefaults(
  defineProps<{
    /** 状态值 */
    value: number | string | null | undefined;
    /** 值 → 文案的映射表（如 BOOK_STATUS_TEXT） */
    map: Record<number, string>;
    /** 值 → Element Plus 色调的映射；未命中用 info */
    tones?: Record<number, 'success' | 'warning' | 'danger' | 'info' | 'primary'>;
    /** 未知状态的前缀，默认「状态」 */
    unknownPrefix?: string;
    size?: 'large' | 'default' | 'small';
  }>(),
  { tones: () => ({}), unknownPrefix: '状态', size: 'small' }
);

const numeric = computed(() => Number(props.value));

const text = computed(() => {
  if (props.value === null || props.value === undefined || props.value === '') {
    return '-';
  }
  return props.map[numeric.value] || `${props.unknownPrefix} ${props.value}`;
});

const tone = computed(() => {
  if (props.value === null || props.value === undefined || props.value === '') {
    return 'info';
  }
  return props.tones[numeric.value] || 'info';
});
</script>
