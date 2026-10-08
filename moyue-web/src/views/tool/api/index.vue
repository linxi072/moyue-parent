<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '@/utils/request'

/**
 * ⑪ 系统接口 —— springdoc-openapi 暴露的 OpenAPI 文档。
 *
 * <p>网关/单体的上下文路径不同（cloud 走 /api/v1/system，boot 走 /），
 * 所以地址做成可编辑输入框，默认取 .env 的 VITE_OPENAPI_URL / VITE_SWAGGER_URL，
 * 部署后若路径有差异直接在页面上改，不必重新打包。
 */
const activeTab = ref('list')
const openApiUrl = ref<string>(import.meta.env.VITE_OPENAPI_URL || '/api/v1/system/v3/api-docs')
const swaggerUrl = ref<string>(
  import.meta.env.VITE_SWAGGER_URL || '/api/v1/system/swagger-ui/index.html'
)
const loading = ref(false)
const apis = ref<
  { path: string; method: string; summary: string; tag: string }[]
>([])
const keyword = ref('')

async function loadApiDoc() {
  if (!openApiUrl.value) {
    ElMessage.warning('请填写 OpenAPI 文档地址')
    return
  }
  loading.value = true
  try {
    // 该地址返回的是原始 OpenAPI JSON，不是统一响应体包装，直接取响应体的 data
    const resp = await http.get(openApiUrl.value)
    const doc = typeof resp.data === 'string' ? JSON.parse(resp.data) : resp.data
    const list: { path: string; method: string; summary: string; tag: string }[] = []
    for (const [path, item] of Object.entries<any>(doc?.paths || {})) {
      for (const [method, op] of Object.entries<any>(item || {})) {
        if (!['get', 'post', 'put', 'delete', 'patch'].includes(method)) continue
        list.push({
          path,
          method: method.toUpperCase(),
          summary: op?.summary || '',
          tag: (op?.tags || [])[0] || 'default'
        })
      }
    }
    apis.value = list
    ElMessage.success(`已加载 ${list.length} 个接口`)
  } catch (e) {
    ElMessage.error('加载失败，请检查地址是否正确')
    apis.value = []
  } finally {
    loading.value = false
  }
}

const filtered = ref<typeof apis.value>([])
function filterApis() {
  const kw = keyword.value.trim().toLowerCase()
  filtered.value = kw
    ? apis.value.filter(
        (a) =>
          a.path.toLowerCase().includes(kw) ||
          a.summary.toLowerCase().includes(kw) ||
          a.tag.toLowerCase().includes(kw)
      )
    : apis.value
}

function methodTag(method: string) {
  return { GET: 'success', POST: 'primary', PUT: 'warning', DELETE: 'danger', PATCH: 'info' }[
    method
  ] as any
}

onMounted(loadApiDoc)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <el-tabs v-model="activeTab">
          <el-tab-pane label="接口清单" name="list" />
          <el-tab-pane label="Swagger UI" name="swagger" />
        </el-tabs>
      </template>

      <template v-if="activeTab === 'list'">
        <el-form :inline="true" @submit.prevent>
          <el-form-item label="OpenAPI 地址">
            <el-input v-model="openApiUrl" style="width: 380px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadApiDoc">加载</el-button>
          </el-form-item>
          <el-form-item label="关键字">
            <el-input v-model="keyword" clearable style="width: 220px" @input="filterApis" />
          </el-form-item>
        </el-form>

        <el-table v-loading="loading" :data="filtered" border stripe max-height="560">
          <el-table-column prop="tag" label="分组" width="160" />
          <el-table-column label="方法" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="methodTag(row.method)" size="small">{{ row.method }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="path" label="接口路径" min-width="340" show-overflow-tooltip />
          <el-table-column prop="summary" label="说明" min-width="240" show-overflow-tooltip />
        </el-table>
      </template>

      <template v-else>
        <el-form :inline="true" @submit.prevent>
          <el-form-item label="Swagger UI 地址">
            <el-input v-model="swaggerUrl" style="width: 420px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="activeTab = 'swagger'">打开</el-button>
          </el-form-item>
        </el-form>
        <iframe :src="swaggerUrl" class="swagger-frame" frameborder="0" />
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.swagger-frame {
  width: 100%;
  height: calc(100vh - 260px);
  border: 1px solid #ebeef5;
  border-radius: 4px;
}
</style>
