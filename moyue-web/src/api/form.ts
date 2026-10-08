import request from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { SysForm, FormSchema, SysFormData, FormItem, PageQuery } from './types'

export interface FormQuery extends PageQuery {
  formName?: string
  status?: number
}

/** ⑯ 在线构建器：17 个端点（其中提交端点不走 admin 前缀） */
export function pageForms(params: FormQuery) {
  return request.get<PageResult<SysForm>>('/admin/system/forms', { params })
}

export function createForm(data: Partial<SysForm>) {
  return request.post<number>('/admin/system/forms', data)
}

export function getForm(formId: number) {
  return request.get<SysForm>(`/admin/system/forms/${formId}`)
}

export function updateForm(formId: number, data: Partial<SysForm>) {
  return request.put<boolean>(`/admin/system/forms/${formId}`, data)
}

export function deleteForm(formId: number, force = false) {
  return request.delete<boolean>(`/admin/system/forms/${formId}`, { params: { force } })
}

export function getFormSchema(formId: number) {
  return request.get<FormSchema>(`/admin/system/forms/${formId}/schema`)
}

export function saveFormSchema(
  formId: number,
  data: { config?: Record<string, unknown>; items?: FormItem[] }
) {
  return request.put<boolean>(`/admin/system/forms/${formId}/schema`, data)
}

export function copyForm(formId: number) {
  return request.post<number>(`/admin/system/forms/${formId}/copy`)
}

export function changeFormStatus(formId: number, status: number) {
  return request.put<boolean>(`/admin/system/forms/${formId}/status`, null, { params: { status } })
}

export function previewForm(formId: number) {
  return request.get<FormSchema>(`/admin/system/forms/${formId}/preview`)
}

export function formHistory(formId: number) {
  return request.get<any[]>(`/admin/system/forms/${formId}/history`)
}

export function rollbackForm(formId: number, versionId: number) {
  return request.post<boolean>(`/admin/system/forms/${formId}/history/${versionId}/rollback`)
}

/** 渲染端提交（不走 admin 前缀） */
export function submitForm(formId: number, data: Record<string, unknown>) {
  return request.post<number>(`/system/forms/${formId}/submit`, { formId, data })
}

export function pageFormData(formId: number, params: PageQuery) {
  return request.get<PageResult<SysFormData>>(`/admin/system/forms/${formId}/data`, { params })
}

export function getFormData(formId: number, dataId: number) {
  return request.get<SysFormData>(`/admin/system/forms/${formId}/data/${dataId}`)
}

export function deleteFormData(formId: number, dataId: number) {
  return request.delete<boolean>(`/admin/system/forms/${formId}/data/${dataId}`)
}

/** 导出收集数据（CSV） */
export function exportFormDataUrl(formId: number) {
  return `/admin/system/forms/${formId}/data/export`
}

export function listFormItems(formId: number) {
  return request.get<{ data: FormItem[] }>(`/admin/system/forms/${formId}/schema`)
}
