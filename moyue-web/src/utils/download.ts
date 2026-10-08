import { ElMessage } from 'element-plus'
import { requestRaw } from './request'

/**
 * 下载二进制响应（CSV / Zip）。
 *
 * <p>后端导出接口返回的不是统一响应体包装（是文件流），所以走 requestRaw 绕过解包。
 */
export async function downloadFile(url: string, filename: string, method: 'get' | 'post' = 'get') {
  try {
    const blob = await requestRaw({ url, method })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(link.href)
  } catch (e) {
    ElMessage.error('下载失败')
  }
}
