<template>
  <div class="my-rewards">
    <el-card shadow="never" class="panel">
      <el-tabs v-model="activeTab">
        <!-- 我的打赏 -->
        <el-tab-pane label="我的打赏" name="reward">
          <div class="toolbar">
            <el-input
              v-model="searchOrderNo"
              class="search-input"
              placeholder="输入订单号查询详情"
              clearable
              @keyup.enter="searchDetail"
            />
            <el-button type="primary" size="small" :loading="detailLoading" @click="searchDetail">
              查询
            </el-button>
            <el-button size="small" :loading="rewardLoading" @click="loadRewards">刷新</el-button>
          </div>

          <el-alert
            v-if="rewardError"
            class="tip"
            type="error"
            show-icon
            :closable="false"
            :title="rewardError"
          />

          <div v-loading="rewardLoading">
            <EmptyState v-if="!rewardLoading && !rewardError && rewards.length === 0" mark="赏" title="还没有打赏过任何作品" />

            <el-table v-else :data="rewards" border stripe size="small">
              <el-table-column prop="orderNo" label="订单号" min-width="180" show-overflow-tooltip />
              <el-table-column label="书籍 ID" min-width="140">
                <template #default="scope">{{ bookText(scope.row) }}</template>
              </el-table-column>
              <el-table-column label="金额" width="110" align="right">
                <template #default="scope">{{ amountText(scope.row.amount) }}</template>
              </el-table-column>
              <el-table-column label="渠道" width="100">
                <template #default="scope">{{ channelText(scope.row.payChannel) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="scope">
                  <el-tag :type="statusTagType(scope.row.status)" size="small">
                    {{ statusText(scope.row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="时间" width="180">
                <template #default="scope">{{ formatTime(scope.row.createTime) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="90" fixed="right">
                <template #default="scope">
                  <el-button link type="primary" size="small" @click="openDetail(scope.row.orderNo)">
                    详情
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <p v-if="!rewardError && rewards.length > 0" class="note">
              最多展示最近 100 条打赏记录。
            </p>
          </div>
        </el-tab-pane>

        <!-- 我的稿酬 -->
        <el-tab-pane label="我的稿酬" name="income">
          <div class="toolbar">
            <span class="toolbar-tip">稿酬流水按打赏分成入账，按月结算。</span>
            <el-button size="small" :loading="incomeLoading" @click="loadIncome(1)">刷新</el-button>
          </div>

          <el-alert
            v-if="incomeError"
            class="tip"
            type="error"
            show-icon
            :closable="false"
            :title="incomeError"
          />

          <div v-loading="incomeLoading">
            <EmptyState v-if="!incomeLoading && !incomeError && incomes.length === 0" mark="赏" title="暂无稿酬流水，成为作者并发布作品后即可获得收益" />

            <template v-else>
              <el-table :data="incomes" border stripe size="small">
                <el-table-column label="类型" min-width="140">
                  <template #default="scope">
                    <el-tag :type="incomeTagType(scope.row.incomeType)" size="small">
                      {{ incomeTypeText(scope.row.incomeType) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="金额" width="140" align="right">
                  <template #default="scope">
                    <span class="income-amount">{{ amountText(scope.row.amount) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="时间" min-width="180">
                  <template #default="scope">{{ formatTime(scope.row.createTime) }}</template>
                </el-table-column>
              </el-table>

              <p v-if="!incomeError && incomes.length > 0" class="note">
                当前页合计
                <span class="income-amount">{{ amountText(incomePageSum) }}</span>
                （仅当前页合计，跨页累计汇总待后端提供稿酬汇总端点后支持）。
              </p>

              <div v-if="incomeTotal > incomeSize" class="pager">
                <el-pagination
                  layout="prev, pager, next"
                  :total="incomeTotal"
                  :page-size="incomeSize"
                  :current-page="incomePage"
                  @current-change="loadIncome"
                />
              </div>
            </template>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 订单详情弹窗 -->
    <el-dialog v-model="detailVisible" title="打赏订单详情" width="480px">
      <div v-loading="detailLoading">
        <EmptyState v-if="!detailLoading && !detail" mark="赏" title="未查询到该订单" />
        <el-descriptions v-else :column="1" border size="small">
          <el-descriptions-item label="订单号">{{ detail?.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="作品">{{ bookText(detail) }}</el-descriptions-item>
          <el-descriptions-item label="章节">{{ chapterText(detail) }}</el-descriptions-item>
          <el-descriptions-item label="打赏金额">{{ amountText(detail?.amount) }}</el-descriptions-item>
          <el-descriptions-item label="支付渠道">{{ channelText(detail?.payChannel) }}</el-descriptions-item>
          <el-descriptions-item label="订单状态">
            <el-tag :type="statusTagType(detail?.status)" size="small">
              {{ statusText(detail?.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ formatTime(detail?.payTime) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(detail?.createTime) }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { myIncome, myRewards, rewardDetail } from '@/api/reward';
import type { AuthorIncome, RewardOrder } from '@/api/reward';
import { formatTime } from '@/api/types';

import EmptyState from '@/components/EmptyState.vue';
type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger';

const activeTab = ref<'reward' | 'income'>('reward');

const rewards = ref<RewardOrder[]>([]);
const rewardLoading = ref(false);
const rewardError = ref('');

const incomes = ref<AuthorIncome[]>([]);
const incomeLoading = ref(false);
const incomeError = ref('');
const incomePage = ref(1);
const incomeSize = ref(10);
const incomeTotal = ref(0);

const searchOrderNo = ref('');
const detailVisible = ref(false);
const detailLoading = ref(false);
const detail = ref<RewardOrder | null>(null);

/** 当前页稿酬合计（后端暂无跨页汇总端点，只能按页聚合） */
const incomePageSum = computed<number>(() =>
  incomes.value.reduce((sum, item) => sum + Number(item.amount || 0), 0)
);

async function loadRewards() {
  rewardLoading.value = true;
  rewardError.value = '';
  try {
    rewards.value = await myRewards();
  } catch {
    // request.ts 已统一提示业务错误，这里仅兜底防止未处理异常
    rewards.value = [];
    rewardError.value = '打赏记录加载失败，请稍后重试。';
  } finally {
    rewardLoading.value = false;
  }
}

async function loadIncome(nextPage = incomePage.value) {
  incomeLoading.value = true;
  incomeError.value = '';
  try {
    const res = await myIncome(nextPage, incomeSize.value);
    incomes.value = (res && res.records) || [];
    incomeTotal.value = Number(res?.total || 0);
    incomePage.value = nextPage;
  } catch {
    // request.ts 已统一提示业务错误，这里仅兜底防止未处理异常
    incomes.value = [];
    incomeTotal.value = 0;
    incomeError.value = '稿酬流水加载失败，请稍后重试。';
  } finally {
    incomeLoading.value = false;
  }
}

function searchDetail() {
  const orderNo = searchOrderNo.value.trim();
  if (!orderNo) {
    ElMessage.warning('请输入要查询的订单号');
    return;
  }
  openDetail(orderNo);
}

async function openDetail(orderNo: string) {
  detailVisible.value = true;
  detailLoading.value = true;
  detail.value = null;
  try {
    detail.value = await rewardDetail(orderNo);
  } catch {
    // request.ts 已统一提示业务错误，这里仅兜底防止未处理异常
    detail.value = null;
  } finally {
    detailLoading.value = false;
  }
}

function bookText(row: RewardOrder | null | undefined): string {
  if (!row || row.bookId === undefined || row.bookId === null) {
    return '全站打赏';
  }
  return `作品 #${row.bookId}`;
}

function chapterText(row: RewardOrder | null | undefined): string {
  if (!row || row.chapterId === undefined || row.chapterId === null) {
    return '整本打赏';
  }
  return `章节 #${row.chapterId}`;
}

function amountText(amount: number | undefined | null): string {
  return `¥${Number(amount || 0).toFixed(2)}`;
}

function channelText(channel: number | undefined | null): string {
  return Number(channel) === 2 ? '支付宝' : '微信';
}

function statusText(status: number | undefined | null): string {
  if (Number(status) === 1) return '已支付';
  if (Number(status) === 2) return '已关闭';
  return '待支付';
}

function statusTagType(status: number | undefined | null): TagType {
  if (Number(status) === 1) return 'success';
  if (Number(status) === 2) return 'info';
  return 'warning';
}

function incomeTypeText(incomeType: number | undefined | null): string {
  if (Number(incomeType) === 1) return '订阅';
  if (Number(incomeType) === 2) return '打赏分成';
  if (Number(incomeType) === 3) return '全勤奖';
  return '其他';
}

function incomeTagType(incomeType: number | undefined | null): TagType {
  if (Number(incomeType) === 2) return 'danger';
  if (Number(incomeType) === 3) return 'warning';
  return 'primary';
}

onMounted(() => {
  loadRewards();
  loadIncome(1);
});
</script>

<style scoped>
.my-rewards {
  padding: 16px;
}
.panel {
  border-radius: var(--moyue-radius);
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.search-input {
  width: 260px;
}
.toolbar-tip {
  flex: 1;
  font-size: 12px;
  color: var(--moyue-ink);
  opacity: 0.6;
}
.tip {
  margin-bottom: 12px;
}
.income-amount {
  font-weight: 600;
  color: var(--moyue-crimson);
}
.note {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--moyue-ink);
  opacity: 0.55;
}
.pager {
  display: flex;
  justify-content: center;
  margin-top: 12px;
}
</style>
