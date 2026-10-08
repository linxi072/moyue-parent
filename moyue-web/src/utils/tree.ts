/**
 * tree.ts —— 列表转树工具
 *
 * <p>后端部分接口返回扁平列表（menus / depts），部分返回已组装好的树；
 * 这里统一幂等处理：已经是树就原样返回，扁平则按 parentId 组装。
 */
export interface TreeNode {
  id?: number
  parentId?: number
  children?: any[]
}

export function buildTree<T extends TreeNode>(list: T[], rootId = 0): T[] {
  if (!Array.isArray(list) || list.length === 0) return []
  // 幂等：已经是树结构则直接返回
  if (list.some((item) => Array.isArray(item.children) && item.children.length > 0)) {
    return list
  }
  const map = new Map<number, T>()
  const roots: T[] = []
  for (const item of list) {
    map.set(item.id as number, { ...item, children: [] } as T)
  }
  for (const item of list) {
    const node = map.get(item.id as number) as T
    const parentId = item.parentId ?? rootId
    if (parentId === rootId || !map.has(parentId)) {
      roots.push(node)
    } else {
      const parent = map.get(parentId) as T
      if (!parent.children) parent.children = []
      parent.children.push(node)
    }
  }
  return roots
}

/** 树拍平为 id -> 名称映射（列表页显示父级名称用） */
export function flattenLabel<T extends TreeNode>(
  list: T[],
  labelKey: string,
  map: Record<number, string> = {}
): Record<number, string> {
  for (const item of list) {
    if (item.id !== undefined) {
      map[item.id] = (item as any)[labelKey]
    }
    if (item.children?.length) {
      flattenLabel(item.children, labelKey, map)
    }
  }
  return map
}
