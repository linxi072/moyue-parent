// 后端契约类型（与 moyue-cloud/moyue-boot 的 DTO / VO 对齐）

export interface PageQuery {
  page: number
  size: number
}

/** 用户 */
export interface SysUser {
  id?: number
  username?: string
  nickname?: string
  userType?: number
  phone?: string
  email?: string
  status?: number
  deptId?: number
  avatar?: string
  loginIp?: string
  loginDate?: string
  createTime?: string
  remark?: string
  roleIds?: number[]
  password?: string
}

/** 角色 */
export interface SysRole {
  id?: number
  roleName?: string
  roleKey?: string
  sort?: number
  status?: number
  dataScope?: number
  remark?: string
  createTime?: string
  menuIds?: number[]
  deptIds?: number[]
}

/** 菜单 */
export interface SysMenu {
  id?: number
  menuName?: string
  parentId?: number
  path?: string
  component?: string
  perms?: string
  icon?: string
  /** 菜单类型：M 目录 / C 菜单 / F 按钮（后端 char(1) / String） */
  menuType?: string
  sort?: number
  visible?: number
  status?: number
  children?: SysMenu[]
}

/** 部门 */
export interface SysDept {
  id?: number
  deptName?: string
  parentId?: number
  sort?: number
  leader?: string
  phone?: string
  status?: number
  children?: SysDept[]
}

/** 字典类型 */
export interface SysDictType {
  id?: number
  dictName?: string
  dictType?: string
  status?: number
  remark?: string
  createTime?: string
}

/** 字典数据 */
export interface SysDictData {
  id?: number
  dictType?: string
  dictLabel?: string
  dictValue?: string
  sort?: number
  status?: number
  remark?: string
}

/** 系统参数 */
export interface SysConfig {
  id?: number
  configName?: string
  configKey?: string
  configValue?: string
  configType?: number
  remark?: string
}

/** 操作日志 */
export interface SysOperLog {
  id?: number
  title?: string
  businessType?: number
  method?: string
  requestMethod?: string
  operatorType?: number
  operName?: string
  operUrl?: string
  operIp?: string
  operTime?: string
  status?: number
  errorMsg?: string
  costTime?: number
}

/** 登录日志 */
export interface SysLoginLog {
  id?: number
  username?: string
  ip?: string
  location?: string
  browser?: string
  os?: string
  status?: number
  message?: string
  loginTime?: string
}

/** 在线用户 */
export interface SysUserOnline {
  tokenId?: string
  userId?: number
  username?: string
  nickname?: string
  ip?: string
  browser?: string
  os?: string
  loginTime?: string
  lastAccessTime?: string
}

/** 定时任务（XXL-Job） */
export interface JobInfo {
  id?: number
  jobGroup?: number
  jobDesc?: string
  author?: string
  scheduleType?: string
  scheduleConf?: string
  executorHandler?: string
  executorParam?: string
  triggerStatus?: number
  glueType?: string
  createTime?: string
  updateTime?: string
}

export interface JobLog {
  id?: number
  jobId?: number
  triggerTime?: string
  triggerCode?: number
  triggerMsg?: string
  handleTime?: string
  handleCode?: number
  handleMsg?: string
}

/** 服务监控 */
export interface ServerVO {
  cpu?: { cpuNum?: number; total?: number; sys?: number; used?: number; wait?: number; free?: number }
  mem?: { total?: number; used?: number; free?: number; usage?: number }
  jvm?: { name?: string; version?: string; home?: string; total?: number; used?: number; free?: number; usage?: number; startTime?: string; runTime?: string }
  sys?: { computerName?: string; osName?: string; osArch?: string; computerIp?: string; userDir?: string }
  disk?: { name?: string; total?: string; free?: string; used?: string; usage?: number }[]
}

/** 缓存监控 */
export interface RedisInfoVO {
  version?: string
  mode?: string
  connectedClients?: number
  usedMemory?: string
  usedMemoryHuman?: string
  peakMemoryHuman?: string
  uptimeInDays?: number
  qps?: number
  keyCount?: number
  hitRate?: string
  commandStats?: { name: string; value: string }[]
  dbSize?: Record<string, number>
  info?: string
}

/** 连接池 */
export interface DruidPoolVO {
  name?: string
  activeCount?: number
  poolingCount?: number
  maxActive?: number
  connectCount?: number
  closeCount?: number
  waitThreadCount?: number
  logicConnectErrorCount?: number
}

/** 代码生成配置 */
export interface GenTable {
  id?: number
  tableName?: string
  tableComment?: string
  className?: string
  packageName?: string
  moduleName?: string
  businessName?: string
  functionName?: string
  functionAuthor?: string
  tplCategory?: string
  genType?: number
  genPath?: string
  createTime?: string
}

export interface GenTableColumn {
  id?: number
  tableId?: number
  columnName?: string
  columnComment?: string
  columnType?: string
  javaType?: string
  javaField?: string
  isPk?: number
  isRequired?: number
  isInsert?: number
  isEdit?: number
  isList?: number
  isQuery?: number
  queryType?: string
  htmlType?: string
  dictType?: string
  sort?: number
}

/** 在线构建器 */
export interface SysForm {
  id?: number
  formName?: string
  formKey?: string
  formDesc?: string
  status?: number
  version?: number
  createTime?: string
}

export interface FormItem {
  itemId?: number
  itemName?: string
  itemKey?: string
  itemType?: string
  defaultValue?: string
  placeholder?: string
  options?: unknown
  required?: number
  sort?: number
}

export interface FormSchema {
  formId?: number
  formName?: string
  formKey?: string
  formDesc?: string
  status?: number
  version?: number
  config?: Record<string, unknown>
  items?: FormItem[]
}

export interface SysFormData {
  id?: number
  formId?: number
  formKey?: string
  dataJson?: string
  submitName?: string
  submitIp?: string
  submitTime?: string
}

/** 登录响应 */
export interface TokenVO {
  accessToken: string
  refreshToken: string
  expiresIn: number
  userId: number
  nickname: string
  userType: number
  roles: string[]
}
