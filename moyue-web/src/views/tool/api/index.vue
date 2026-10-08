<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listApiGroups,
  listApisByTag,
  getApiDetail,
  getApiStats,
  getRawOpenApi,
  type ApiGroup,
  type ApiItem,
  type ApiStats,
  type ApiDetail
} from '@/api/tool'

/**
 * ⑪ 系统接口 —— 改走后端 SysApiController（/api/v1/admin/system/apis）。
 *
 * <p>不再直连原始 OpenAPI JSON 地址：后端已按 Tag 分组、标记鉴权、聚合统计，
 * 并预留与基线契约的差集校验能力。Swagger UI 仍作为可选原始入口保留。
 */
const activeTab = ref('browse')

// —— 分组 ——
const groups = ref<ApiGroup[]>([])
const activeGroup = ref<string>('')

// —— 接口清单 ——
const apiItems = ref<ApiItem[]>([])
const keyword = ref('')
const loadingList = ref(false)

// —— 详情 ——
const detail = ref<ApiDetail | null>(null)
const loadingDetail = ref(false)

// —— 统计 ——
const stats = ref<ApiStats | null>(null)

// —— 原始契约 / Swagger ——
const rawJson = ref('')
const loadingRaw = ref(false)
const swaggerUrl = ref<string>(
  import.meta.env.VITE_SWAGGER_URL || '/api/v1/system/swagger-ui/index.html'
)

async function loadGroups() {
  try {
    groups.value = await listApiGroups()
    if (groups.value.length && !activeGroup.value) {
      activeGroup.value = groups.value[0].tag
      await loadApis(activeGroup.value)
    }
  } catch {
    ElMessage.error('加载接口分组失败')
  }
}

async function loadApis(tag: string) {
  activeGroup.value = tag
  loadingList.value = true
  try {
    apiItems.value = await listApisByTag(tag)
    detail.value = null
  } catch {
    ElMessage.error('加载接口清单失败')
    apiItems.value = []
  } finally {
    loadingList.value = false
  }
}

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return apiItems.value
  return apiItems.value.filter(
    (a) =>
      a.path.toLowerCase().includes(kw) ||
      a.summary.toLowerCase().includes(kw) ||
      a.method.toLowerCase().includes(kw)
  )
})

async function showDetail(row: ApiItem) {
  loadingDetail.value = true
  try {
    // apiId 为完整接口路径（含斜杠），后端以请求参数接收
    detail.value = await getApiDetail(row.path)
  } catch {
    ElMessage.error('加载接口详情失败')
  } finally {
    loadingDetail.value = false
  }
}

async function loadStats() {
  try {
    stats.value = await getApiStats()
  } catch {
    ElMessage.error('加载接口统计失败')
  }
}

async function loadRaw() {
  loadingRaw.value = true
  try {
    const doc = await getRawOpenApi()
    rawJson.value = JSON.stringify(doc, null, 2)
  } catch {
    ElMessage.error('加载原始契约失败')
    rawJson.value = ''
  } finally {
    loadingRaw.value = false
  }
}

const byTagRows = computed(() => {
  if (!stats.value) return []
  return Object.entries(stats.value.byTag).map(([tag, count]) => ({ tag, count }))
})

function onTabChange(name: string | number) {
  if (name === 'stats' && !stats.value) loadStats()
  if (name === 'raw' && !rawJson.value) loadRaw()
}

const METHOD_TAG: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
  GET: 'success',
  POST: 'primary',
  PUT: 'warning',
  DELETE: 'danger',
  PATCH: 'info'
}

function methodTag(method: string) {
  return METHOD_TAG[method] || 'info'
}

onMounted(() => {
  loadGroups()
  loadStats()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <el-tabs v-model="activeTab" @tab-change="onTabChange">
          <el-tab-pane label="接口浏览" name="browse" />
          <el-tab-pane label="统计概览" name="stats" />
          <el-tab-pane label="原始契约" name="raw" />
          <el-tab-pane label="Swagger UI" name="swagger" />
        </el-tabs>
      </template>

      <!-- 接口浏览：分组 → 清单 → 详情 -->
      <el-row v-if="activeTab === 'browse'" :gutter="16">
        <el-col :span="5">
          <el-card shadow="never">
            <template #header>接口分组</template>
            <el-scrollbar max-height="560">
              <ul class="group-list">
                <li
                  v-for="g in groups"
                  :key="g.tag"
                  :class="['group-item', { active: g.tag === activeGroup }]"
                  @click="loadApis(g.tag)"
                >
                  <span class="group-tag">{{ g.tag }}</span>
                  <el-tag size="small" type="info">{{ g.count }}</el-tag>
                </li>
              </ul>
            </el-scrollbar>
          </el-card>
        </el-col>

        <el-col :span="9">
          <el-card shadow="never">
            <template #header>
              <div class="flex-between">
                <span>接口清单 · {{ activeGroup }}</span>
                <el-input
                  v-model="keyword"
                  clearable
                  placeholder="搜索路径/说明"
                  style="width: 200px"
                />
              </div>
            </template>
            <el-table
              v-loading="loadingList"
              :data="filtered"
              border
              stripe
              max-height="560"
              highlight-current-row
              @row-click="showDetail"
            >
              <el-table-column label="方法" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="methodTag(row.method)" size="small">{{ row.method }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="path" label="接口路径" min-width="200" show-overflow-tooltip />
              <el-table-column prop="summary" label="说明" min-width="120" show-overflow-tooltip />
              <el-table-column label="鉴权" width="70" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.auth ? 'danger' : 'success'" size="small">
                    {{ row.auth ? '鉴权' : '公开' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>

        <el-col :span="10">
          <el-card shadow="never">
            <template #header>接口详情</template>
            <el-scrollbar max-height="560" v-loading="loadingDetail">
              <template v-if="detail">
                <div class="detail-path">{{ detail.apiId }}</div>
                <el-tag :type="detail.found ? 'success' : 'info'" size="small">
                  {{ detail.found ? '已收录' : '未收录' }}
                </el-tag>
                <pre v-if="detail.pathItem" class="json-block">{{ JSON.stringify(detail.pathItem, null, 2) }}</pre>
                <el-empty v-else description="该路径未收录于契约" />
              </template>
              <el-empty v-else description="点击左侧接口查看详情" />
            </el-scrollbar>
          </el-card>
        </el-col>
      </el-row>

      <!-- 统计概览 -->
      <template v-else-if="activeTab === 'stats'">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-card shadow="never">
              <div class="stat-num">{{ stats?.total ?? '-' }}</div>
              <div class="stat-label">接口总数</div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never">
              <div class="stat-num">{{ stats?.authCount ?? '-' }}</div>
              <div class="stat-label">鉴权接口数</div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never">
              <div class="stat-num">{{ stats ? stats.authRatio + '%' : '-' }}</div>
              <div class="stat-label">鉴权接口占比</div>
            </el-card>
          </el-col>
        </el-row>
        <el-card shadow="never" class="mt-16">
          <template #header>按分组分布</template>
          <el-table :data="byTagRows" border stripe>
            <el-table-column prop="tag" label="分组" />
            <el-table-column prop="count" label="接口数" width="120" />
          </el-table>
        </el-card>
      </template>

      <!-- 原始契约 -->
      <template v-else-if="activeTab === 'raw'">
        <el-button type="primary" :loading="loadingRaw" @click="loadRaw">重新加载</el-button>
        <pre class="json-block mt-16" v-loading="loadingRaw">{{ rawJson || '（暂无数据）' }}</pre>
      </template>

      <!-- Swagger UI -->
      <template v-else>
        <el-form :inline="true" @submit.prevent>
          <el-form-item label="Swagger UI 地址">
            <el-input v-model="swaggerUrl" style="width: 420px" />
          </el-form-item>
        </el-form>
        <iframe :src="swaggerUrl" class="swagger-frame" frameborder="0" />
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.flex-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.group-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.group-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.2s;
}
.group-item:hover {
  background: #f5f7fa;
}
.group-item.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}
.group-tag {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.detail-path {
  font-family: monospace;
  font-size: 13px;
  word-break: break-all;
  margin-bottom: 8px;
}
.json-block {
  background: #0d1117;
  color: #c9d1d9;
  padding: 12px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
  max-height: 520px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.stat-num {
  font-size: 32px;
  font-weight: 700;
  color: var(--el-color-primary);
  text-align: center;
}
.stat-label {
  text-align: center;
  color: #909399;
  margin-top: 4px;
}
.mt-16 {
  margin-top: 16px;
}
.swagger-frame {
  width: 100%;
  height: calc(100vh - 280px);
  border: 1px solid #ebeef5;
  border-radius: 4px;
}
</style>
