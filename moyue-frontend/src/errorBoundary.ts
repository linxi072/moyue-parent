// 墨阅小说网前端 · 全局错误边界
// 捕获未处理的同步异常与未兑现的 Promise rejection，渲染友好错误页，避免整页白屏。
// 页面内部已就近 try/catch 的业务错误不走此处；本模块是最后兜底。
import { el } from './dom';

let lastErrorTs = 0;

/** 在内容出口渲染致命错误页（带重试 / 回首页） */
export function showFatalError(outlet: HTMLElement, message: string, detail?: string): void {
  outlet.replaceChildren();
  const box = el('div', { class: 'error-page' });
  box.appendChild(el('h2', { text: '页面出错了' }));
  box.appendChild(el('p', { class: 'msg', text: message }));
  if (detail) {
    box.appendChild(el('pre', { class: 'error-detail', text: detail }));
  }
  const retry = el('button', { class: 'btn primary', text: '重试' });
  retry.addEventListener('click', () => location.reload());
  const home = el('a', { class: 'btn-link', href: '#/', text: '返回书城' });
  box.appendChild(el('div', { class: 'error-actions' }, [retry, home]));
  outlet.appendChild(box);
}

/** 安装全局兜底监听；getOutlet 在错误发生时取当前内容出口（路由切换后仍为最新） */
export function installErrorBoundary(getOutlet: () => HTMLElement | null): void {
  const handle = (message: string, detail?: string): void => {
    const now = Date.now();
    if (now - lastErrorTs < 1000) return; // 防抖，避免错误风暴刷屏
    lastErrorTs = now;
    const outlet = getOutlet();
    if (outlet) showFatalError(outlet, message, detail);
  };
  window.addEventListener('error', (e) => handle('页面运行异常', e.message));
  window.addEventListener('unhandledrejection', (e) => {
    const reason = e.reason instanceof Error ? e.reason.message : String(e.reason);
    handle('请求或异步任务失败', reason);
  });
}
