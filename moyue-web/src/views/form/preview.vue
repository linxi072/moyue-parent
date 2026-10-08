<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'
import { previewForm, submitForm } from '@/api/form'
import type { FormSchema, FormItem } from '@/api/types'

/**
 * ⑯-c 渲染端预览 —— 与 C 端渲染同一份 Schema。
 * 注意：提交端点不走 admin 前缀（/system/forms/{id}/submit），读者/作者也能提交。
 */
const route = useRoute()
const formId = ref<number>(Number(route.query.formId) || 0)

const loading = ref(false)
const schema = ref<FormSchema>({})
const items = ref<FormItem[]>([])
const formRef = ref<FormInstance>()
const values = reactive<Record<string, any>>({})

async function load() {
  if (!formId.value) return
  loading.value = true
  try {
    schema.value = await previewForm(formId.value)
    items.value = schema.value.items || []
    for (const it of items.value) {
      values[it.itemKey!] = it.defaultValue ?? ''
    }
  } finally {
    loading.value = false
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  await submitForm(formId.value, values)
  ElMessage.success('提交成功')
  for (const it of items.value) {
    values[it.itemKey!] = it.defaultValue ?? ''
  }
}

function optionsOf(item: FormItem): string[] {
  return (item.options as string[] | undefined) || []
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <el-card v-loading="loading" shadow="never" style="max-width: 640px">
      <template #header>
        <div>
          <div class="form-title">{{ schema.formName || '表单预览' }}</div>
          <el-text type="info" size="small">{{ schema.formDesc }}</el-text>
        </div>
      </template>

      <el-empty v-if="!items.length" description="该表单尚无字段，请先在设计器中配置" />

      <el-form v-else ref="formRef" :model="values" label-width="100px">
        <el-form-item
          v-for="item in items"
          :key="item.itemKey"
          :label="item.itemName"
          :prop="item.itemKey"
          :required="item.required === 1"
        >
          <el-input
            v-if="item.itemType === 'input'"
            v-model="values[item.itemKey!]"
            :placeholder="item.placeholder"
          />
          <el-input
            v-else-if="item.itemType === 'textarea'"
            v-model="values[item.itemKey!]"
            type="textarea"
            :rows="3"
            :placeholder="item.placeholder"
          />
          <el-input-number
            v-else-if="item.itemType === 'number'"
            v-model="values[item.itemKey!]"
          />
          <el-select
            v-else-if="item.itemType === 'select'"
            v-model="values[item.itemKey!]"
            :placeholder="item.placeholder"
            style="width: 100%"
          >
            <el-option v-for="o in optionsOf(item)" :key="o" :label="o" :value="o" />
          </el-select>
          <el-radio-group v-else-if="item.itemType === 'radio'" v-model="values[item.itemKey!]">
            <el-radio v-for="o in optionsOf(item)" :key="o" :value="o">{{ o }}</el-radio>
          </el-radio-group>
          <el-checkbox-group
            v-else-if="item.itemType === 'checkbox'"
            v-model="values[item.itemKey!]"
          >
            <el-checkbox v-for="o in optionsOf(item)" :key="o" :value="o">{{ o }}</el-checkbox>
          </el-checkbox-group>
          <el-date-picker
            v-else-if="item.itemType === 'date'"
            v-model="values[item.itemKey!]"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
          <el-switch
            v-else-if="item.itemType === 'switch'"
            v-model="values[item.itemKey!]"
            :active-value="1"
            :inactive-value="0"
          />
          <el-input v-else v-model="values[item.itemKey!]" :placeholder="item.placeholder" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="submit">提交</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.form-title {
  font-size: 16px;
  font-weight: 600;
}
</style>
