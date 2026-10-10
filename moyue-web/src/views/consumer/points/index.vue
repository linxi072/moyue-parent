<template>
  <div class="consumer-page">
    <div class="balance-card">
      <div class="balance-num">{{ balance }}</div>
      <div class="balance-lbl">积分余额</div>
      <el-button type="primary" :loading="signing" :disabled="signedToday" @click="onSign">
        {{ signedToday ? '今日已签到' : '签到 +10' }}
      </el-button>
    </div>

    <div class="bar"><span class="title">积分明细</span></div>

    <div v-loading="loading" class="list">
      <div v-for="l in logs" :key="l.id" class="log">
        <div class="log-main">
          <span class="log-type">{{ bizText(l.bizType) }}</span>
          <span class="log-remark">{{ l.remark || '' }}</span>
        </div>
        <div class="log-side">
          <span class="log-amount" :class="(l.changeAmount ?? 0) >= 0 ? 'plus' : 'minus'">
            {{ (l.changeAmount ?? 0) >= 0 ? '+' : '' }}{{ l.changeAmount }}
          </span>
          <span class="log-time">{{ l.createTime }}</span>
        </div>
      </div>
      <el-empty v-if="!loading && !logs.length" description="暂无记录" />
    </div>

    <div class="pager">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20]"
        layout="total, prev, pager, next"
        @current-change="load"
        @size-change="load"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { myPointsBalance, pageMyPointsLogs, signIn, type PointsLogVO } from '@/api/commerce'

// 积分钱包：余额 + 每日签到 + 流水。签到幂等（后端按当日 BIZ_SIGN 流水去重）。
const balance = ref(0)
const signedToday = ref(false)
const signing = ref(false)
const logs = ref<PointsLogVO[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ page: 1, size: 10 })

const bizText = (t?: number) =>
  ({ 1: '充值', 2: '打赏', 3: '消费', 4: '退款', 5: '签到' }[t ?? 0] ?? '其他')

async function load() {
  loading.value = true
  try {
    const [b, r] = await Promise.all([myPointsBalance(), pageMyPointsLogs(query)])
    balance.value = b ?? 0
    logs.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

async function checkSigned() {
  // 通过今日是否存在 BIZ_SIGN(5) 流水判断今日是否已签到
  try {
    const r = await pageMyPointsLogs({ page: 1, size: 10, bizType: 5 })
    const today = new Date().toISOString().slice(0, 10)
    signedToday.value = (r.records || []).some((l) => (l.createTime || '').slice(0, 10) === today)
  } catch {
    /* 忽略：仅影响按钮态 */
  }
}

async function onSign() {
  signing.value = true
  try {
    const gained = await signIn()
    ElMessage.success(`签到成功，获得 ${gained} 积分`)
    signedToday.value = true
    load()
  } finally {
    signing.value = false
  }
}

onMounted(() => {
  load()
  checkSigned()
})
</script>

<style scoped lang="scss">
.balance-card {
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-active));
  color: #fff;
  border-radius: var(--radius-sm);
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  .balance-num { font-size: 32px; font-weight: 700; }
  .balance-lbl { font-size: 13px; opacity: 0.9; }
  .el-button { margin-top: 10px; }
}
.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
  .title { font-size: 16px; font-weight: 600; color: var(--color-text); }
}
.list { display: flex; flex-direction: column; gap: 10px; margin-top: 6px; }
.log {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  .log-main { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
  .log-type { font-weight: 600; color: var(--color-text); }
  .log-remark { font-size: 12px; color: var(--color-text-muted); }
  .log-side { text-align: right; flex-shrink: 0; }
  .log-amount { font-weight: 600; display: block; }
  .log-amount.plus { color: var(--color-success); }
  .log-amount.minus { color: var(--color-danger); }
  .log-time { font-size: 12px; color: var(--color-text-muted); }
}
.pager { display: flex; justify-content: center; margin-top: 12px; }
</style>
