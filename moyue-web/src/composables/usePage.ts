/**
 * usePage.ts —— 批量页面共用的分页查询状态机
 *
 * <p>说明：所有列表页的形态高度一致（加载态 / 分页参数 / 结果集 / 查询重置），
 * 抽出来避免 20+ 个页面各写一遍。fetcher 由页面传入，返回后端的 PageResult。
 */
import { ref, reactive, type Ref } from 'vue'
import type { PageResult } from '@/utils/request'

export interface PageQueryBase {
  page: number
  size: number
  [key: string]: any
}

export function usePage<T>(
  fetcher: (params: any) => Promise<PageResult<T>>,
  defaultQuery: Record<string, any> = {}
) {
  const loading = ref(false)
  const total = ref(0)
  const records: Ref<T[]> = ref<T[]>([]) as Ref<T[]>
  const query = reactive<PageQueryBase>({ page: 1, size: 10, ...defaultQuery })

  /** 查询：自动吸收后端空结果的边界（total=0 时清空列表） */
  async function load() {
    loading.value = true
    try {
      const res = await fetcher({ ...query })
      records.value = res?.records || []
      total.value = res?.total || 0
    } catch {
      records.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  /** 条件查询（回到第一页） */
  function search() {
    query.page = 1
    return load()
  }

  /** 重置条件 */
  function reset(extra: Record<string, any> = {}) {
    Object.assign(query, defaultQuery, extra, { page: 1, size: query.size })
    return load()
  }

  function handleSizeChange(size: number) {
    query.size = size
    query.page = 1
    return load()
  }

  function handleCurrentChange(page: number) {
    query.page = page
    return load()
  }

  return {
    loading,
    total,
    records,
    query,
    load,
    search,
    reset,
    handleSizeChange,
    handleCurrentChange
  }
}
