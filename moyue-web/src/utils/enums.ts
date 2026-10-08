/**
 * enums.ts —— 后端字典/枚举的前端镜像
 *
 * <p>来源：common-migration 的建表 COMMENT（V12 / V14 / V15 / V16），
 * 与 sys_dict_type 中 sys_user_type 等保持一致；标签与 Tag 配色在此集中定义，
 * 避免各页面各写一套导致同一字段显示不一致。
 */
export interface EnumOption {
  value: number | string
  label: string
  tag?: 'success' | 'info' | 'warning' | 'danger' | 'primary'
}

/** sys_user.user_type（V15：1 读者 / 2 作者 / 3 运营） */
export const USER_TYPE_OPTIONS: EnumOption[] = [
  { value: 1, label: '读者', tag: 'info' },
  { value: 2, label: '作者', tag: 'warning' },
  { value: 3, label: '运营', tag: 'success' }
]

/** 通用启停状态 */
export const STATUS_OPTIONS: EnumOption[] = [
  { value: 1, label: '正常', tag: 'success' },
  { value: 0, label: '停用', tag: 'danger' }
]

/** 通用显示/隐藏 */
export const VISIBLE_OPTIONS: EnumOption[] = [
  { value: 1, label: '显示', tag: 'success' },
  { value: 0, label: '隐藏', tag: 'info' }
]

/** sys_role.data_scope（V12：1 全部 / 2 自定义 / 3 本部门 / 4 本部门及以下 / 5 仅本人） */
export const DATA_SCOPE_OPTIONS: EnumOption[] = [
  { value: 1, label: '全部数据' },
  { value: 2, label: '自定义数据' },
  { value: 3, label: '本部门数据' },
  { value: 4, label: '本部门及以下' },
  { value: 5, label: '仅本人数据' }
]

/** sys_menu.menu_type（V12：M 目录 / C 菜单 / F 按钮） */
export const MENU_TYPE_OPTIONS: EnumOption[] = [
  { value: 'M', label: '目录', tag: 'info' },
  { value: 'C', label: '菜单', tag: 'success' },
  { value: 'F', label: '按钮', tag: 'warning' }
]

/** sys_config.config_type（V14：0 自定义 / 1 系统内置） */
export const CONFIG_TYPE_OPTIONS: EnumOption[] = [
  { value: 1, label: '系统内置', tag: 'danger' },
  { value: 0, label: '自定义', tag: 'info' }
]

/** sys_oper_log.business_type（V16：0 其它 / 1 新增 ... / 11 上下线） */
export const BUSINESS_TYPE_OPTIONS: EnumOption[] = [
  { value: 0, label: '其它', tag: 'info' },
  { value: 1, label: '新增', tag: 'success' },
  { value: 2, label: '修改', tag: 'primary' },
  { value: 3, label: '删除', tag: 'danger' },
  { value: 4, label: '授权', tag: 'warning' },
  { value: 5, label: '导出', tag: 'primary' },
  { value: 6, label: '导入', tag: 'primary' },
  { value: 7, label: '强退', tag: 'danger' },
  { value: 8, label: '生成代码', tag: 'warning' },
  { value: 9, label: '清空', tag: 'danger' },
  { value: 10, label: '审核', tag: 'warning' },
  { value: 11, label: '上下线', tag: 'warning' }
]

/** sys_oper_log.operator_type（V16） */
export const OPERATOR_TYPE_OPTIONS: EnumOption[] = [
  { value: 0, label: '其它' },
  { value: 1, label: '读者' },
  { value: 2, label: '作者' },
  { value: 3, label: '后台运营' },
  { value: 4, label: '系统内部' }
]

/** 表单控件类型（在线构建器） */
export const FORM_ITEM_TYPES: EnumOption[] = [
  { value: 'input', label: '单行文本' },
  { value: 'textarea', label: '多行文本' },
  { value: 'number', label: '数字' },
  { value: 'select', label: '下拉选择' },
  { value: 'radio', label: '单选' },
  { value: 'checkbox', label: '多选' },
  { value: 'date', label: '日期' },
  { value: 'switch', label: '开关' }
]

export function labelOf(options: EnumOption[], value: unknown): string {
  const hit = options.find((o) => o.value === value)
  return hit ? hit.label : String(value ?? '-')
}

export function tagOf(options: EnumOption[], value: unknown): EnumOption['tag'] {
  const hit = options.find((o) => o.value === value)
  return hit?.tag ?? 'info'
}
