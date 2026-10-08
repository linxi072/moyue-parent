<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="error-page">
    <h1>401</h1>
    <p>无权限访问：后台仅运营主体（user_type = 3）可进入</p>
    <el-button type="primary" @click="router.push('/login')">重新登录</el-button>
  </div>
</template>

<style scoped>
.error-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

h1 {
  font-size: 72px;
  margin: 0;
  color: #c0c4cc;
}
</style>
