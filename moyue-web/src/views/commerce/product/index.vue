<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true" :model="filter">
        <el-form-item label="名称">
          <el-input v-model="filter.name" placeholder="名称模糊" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filter.status" clearable placeholder="全部" style="width: 120px">
            <el-option v-for="o in statusOpts" :key="o.v" :label="o.l" :value="o.v" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button type="success" @click="openCreate">新建商品</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">{{ row.type === 1 ? '书币' : '会员' }}</template>
      </el-table-column>
      <el-table-column prop="priceAmount" label="售价" width="100" />
      <el-table-column prop="points" label="积分" width="90" />
      <el-table-column prop="stock" label="库存" width="90" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '已上架' : '已下架' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="操作" min-width="270" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row as ProductVO)">编辑</el-button>
          <el-button link type="success" v-if="(row as ProductVO).status !== 1" @click="onOnline(row as ProductVO)">上架</el-button>
          <el-button link type="warning" v-if="(row as ProductVO).status === 1" @click="onOffline(row as ProductVO)">下架</el-button>
          <el-button link type="danger" @click="onDelete(row as ProductVO)">删除</el-button>
          <el-button link type="primary" v-if="(row as ProductVO).status === 1" @click="onExchange(row as ProductVO)">兑换</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="load"
      />
    </div>

    <el-dialog v-model="dialog" :title="form.id ? '编辑商品' : '新建商品'" width="min(560px, 94vw)">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" placeholder="商品名称" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width: 140px">
            <el-option :value="1" label="书币" />
            <el-option :value="2" label="会员" />
          </el-select>
        </el-form-item>
        <el-form-item label="售价"><el-input v-model="form.priceAmount" type="number" /></el-form-item>
        <el-form-item label="兑换积分"><el-input v-model="form.points" type="number" /></el-form-item>
        <el-form-item label="库存"><el-input v-model="form.stock" type="number" /></el-form-item>
        <el-form-item label="排序"><el-input v-model="form.sort" type="number" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageProducts,
  createProduct,
  updateProduct,
  deleteProduct,
  onlineProduct,
  offlineProduct,
  exchangeProduct,
  type ProductVO
} from '@/api/commerce'

const rows = ref<ProductVO[]>([])
const total = ref(0)
const loading = ref(false)
const dialog = ref(false)
const filter = reactive({ name: undefined as string | undefined, status: undefined as number | undefined })
const query = reactive({ page: 1, size: 10, name: undefined as string | undefined, status: undefined as number | undefined })
const form = reactive<Partial<ProductVO>>({})

const statusOpts = [
  { v: 0, l: '下架' },
  { v: 1, l: '上架' }
]

async function load() {
  loading.value = true
  try {
    const r = await pageProducts({ ...query, name: filter.name, status: filter.status })
    rows.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: undefined, name: '', type: 1, priceAmount: 0, points: 0, stock: 0, sort: 0 })
  dialog.value = true
}

function openEdit(row: ProductVO) {
  Object.assign(form, { ...row })
  dialog.value = true
}

async function submit() {
  if (!form.name) return ElMessage.warning('请填写名称')
  const payload = {
    name: form.name,
    type: form.type ?? 1,
    priceAmount: Number(form.priceAmount) || 0,
    points: Number(form.points) || 0,
    stock: Number(form.stock) || 0,
    sort: Number(form.sort) || 0
  }
  if (form.id) {
    await updateProduct(form.id, payload)
    ElMessage.success('已保存')
  } else {
    await createProduct(payload)
    ElMessage.success('已创建')
  }
  dialog.value = false
  load()
}

async function onOnline(row: ProductVO) {
  await onlineProduct(row.id)
  ElMessage.success('已上架')
  load()
}

async function onOffline(row: ProductVO) {
  await offlineProduct(row.id)
  ElMessage.success('已下架')
  load()
}

async function onDelete(row: ProductVO) {
  await ElMessageBox.confirm('确认删除该商品？', '提示', { type: 'warning' })
  await deleteProduct(row.id)
  ElMessage.success('已删除')
  load()
}

async function onExchange(row: ProductVO) {
  const uid = await ElMessageBox.prompt('兑换用户 ID', '积分兑换', { inputValue: '1' })
  const orderId = await exchangeProduct(row.id, Number(uid.value))
  ElMessage.success('兑换成功，订单 #' + orderId)
}

onMounted(load)
</script>
