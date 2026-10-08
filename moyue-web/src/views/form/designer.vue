<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getFormSchema, saveFormSchema } from '@/api/form'
import type { FormSchema, FormItem } from '@/api/types'
import { FORM_ITEM_TYPES } from '@/utils/enums'

/**
 * ⑯-a 表单设计器 —— 轻量 Schema 编辑器。
 *
 * <p>刻意不做「拖拽画布」这种重交互：Schema 是 JSON 整包存储（sys_form_history 快照），
 * 拖拽带来的收益不足以覆盖维护成本。这里用「控件列表 + 属性面板」覆盖了同一份数据模型，
 * 后续若要上拖拽，只需替换中间画布，items 结构不变。
 */
const route = useRoute()
const router = useRouter()
const formId = ref<number>(Number(route.query.formId) || 0)
const formName = ref<string>((route.query.formName as string) || '未命名表单')

const loading = ref(false)
const schema = reactive<FormSchema>({ config: {}, items: [] })
const activeIndex = ref(-1)

const activeItem = computed<FormItem | null>(() =>
  activeIndex.value >= 0 ? (schema.items?.[activeIndex.value] ?? null) : null
)

const palette = FORM_ITEM_TYPES

let seed = 0
function nextKey(type: string) {
  seed += 1
  return `${type}_${Date.now().toString(36)}_${seed}`
}

function addItem(type: string, label: string) {
  const item: FormItem = {
    itemKey: nextKey(type),
    itemName: label,
    itemType: type,
    placeholder: '',
    defaultValue: '',
    required: 0,
    sort: (schema.items?.length ?? 0) + 1,
    options: ['select', 'radio', 'checkbox'].includes(type) ? ['选项一', '选项二'] : undefined
  }
  schema.items = schema.items || []
  schema.items.push(item)
  activeIndex.value = schema.items.length - 1
}

function removeItem(index: number) {
  schema.items?.splice(index, 1)
  activeIndex.value = -1
}

function moveItem(index: number, offset: number) {
  const items = schema.items
  if (!items) return
  const target = index + offset
  if (target < 0 || target >= items.length) return
  const tmp = items[index]
  items[index] = items[target]
  items[target] = tmp
  activeIndex.value = target
}

const optionText = computed({
  get: () => (activeItem.value?.options as string[] | undefined)?.join('\n') || '',
  set: (val: string) => {
    if (activeItem.value) {
      activeItem.value.options = val.split('\n').filter((s) => s.trim() !== '')
    }
  }
})

async function load() {
  if (!formId.value) return
  loading.value = true
  try {
    const data = await getFormSchema(formId.value)
    Object.assign(schema, {
      formId: data.formId,
      formName: data.formName,
      formKey: data.formKey,
      formDesc: data.formDesc,
      status: data.status,
      version: data.version,
      config: data.config || {},
      items: data.items || []
    })
    formName.value = data.formName || formName.value
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!formId.value) {
    ElMessage.warning('缺少表单 ID')
    return
  }
  const items = (schema.items || []).map((it, idx) => ({ ...it, sort: idx + 1 }))
  await saveFormSchema(formId.value, { config: schema.config, items })
  ElMessage.success('Schema 已保存')
}

function goPreview() {
  router.push({ path: '/form/preview', query: { formId: formId.value } })
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <el-card v-loading="loading" shadow="never">
      <template #header>
        <div class="card-header">
          <span>表单设计器 · {{ formName }}</span>
          <div>
            <el-button link @click="goPreview">预览</el-button>
            <el-button type="primary" @click="save">保存 Schema</el-button>
          </div>
        </div>
      </template>

      <el-row :gutter="16">
        <!-- 控件面板 -->
        <el-col :span="5">
          <el-card shadow="never">
            <template #header>控件</template>
            <div class="palette">
              <el-button
                v-for="p in palette"
                :key="p.value"
                class="palette-item"
                plain
                @click="addItem(p.value as string, p.label)"
              >
                + {{ p.label }}
              </el-button>
            </div>
          </el-card>
        </el-col>

        <!-- 画布 -->
        <el-col :span="13">
          <el-card shadow="never">
            <template #header>表单结构（{{ schema.items?.length || 0 }} 个字段）</template>
            <el-empty v-if="!schema.items?.length" description="从左侧添加控件" />
            <div
              v-for="(item, index) in schema.items || []"
              :key="item.itemKey"
              class="canvas-item"
              :class="{ active: index === activeIndex }"
              @click="activeIndex = index"
            >
              <div class="canvas-item-head">
                <span class="field-name">{{ item.itemName }}</span>
                <el-tag size="small" type="info">{{ item.itemType }}</el-tag>
                <span class="flex-1" />
                <el-button link size="small" @click.stop="moveItem(index, -1)">上移</el-button>
                <el-button link size="small" @click.stop="moveItem(index, 1)">下移</el-button>
                <el-button link size="small" type="danger" @click.stop="removeItem(index)">
                  删除
                </el-button>
              </div>
              <div class="field-key">key: {{ item.itemKey }}</div>
            </div>
          </el-card>
        </el-col>

        <!-- 属性面板 -->
        <el-col :span="6">
          <el-card shadow="never">
            <template #header>属性</template>
            <el-empty v-if="!activeItem" description="选择左侧字段" />
            <el-form v-else label-width="76px" size="small">
              <el-form-item label="标题">
                <el-input v-model="activeItem.itemName" />
              </el-form-item>
              <el-form-item label="字段 key">
                <el-input v-model="activeItem.itemKey" />
              </el-form-item>
              <el-form-item label="控件类型">
                <el-select v-model="activeItem.itemType" style="width: 100%">
                  <el-option
                    v-for="p in palette"
                    :key="p.value"
                    :label="p.label"
                    :value="p.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="占位提示">
                <el-input v-model="activeItem.placeholder" />
              </el-form-item>
              <el-form-item label="默认值">
                <el-input v-model="activeItem.defaultValue" />
              </el-form-item>
              <el-form-item label="必填">
                <el-switch
                  v-model="activeItem.required"
                  :active-value="1"
                  :inactive-value="0"
                />
              </el-form-item>
              <el-form-item
                v-if="['select', 'radio', 'checkbox'].includes(activeItem.itemType || '')"
                label="选项"
              >
                <el-input
                  v-model="optionText"
                  type="textarea"
                  :rows="5"
                  placeholder="每行一个选项"
                />
              </el-form-item>
            </el-form>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.palette {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.palette-item {
  width: 100%;
  margin-left: 0;
}

.canvas-item {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 10px 12px;
  margin-bottom: 8px;
  cursor: pointer;
}

.canvas-item.active {
  border-color: #5b7fff;
  background: #f0f4ff;
}

.canvas-item-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.field-name {
  font-weight: 600;
}

.field-key {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.flex-1 {
  flex: 1;
}
</style>
