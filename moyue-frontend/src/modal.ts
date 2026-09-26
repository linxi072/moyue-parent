// 墨阅小说网前端 · 通用模态框（替代原生 confirm/alert，统一视觉与交互）
import { el } from './dom';

export interface ModalOptions {
  title?: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  /** 确认按钮危险样式（删除等不可逆操作） */
  danger?: boolean;
}

let modalRoot: HTMLElement | null = null;

function getRoot(): HTMLElement {
  if (!modalRoot) {
    modalRoot = el('div', { class: 'modal-root', id: 'modal-root' });
    document.body.appendChild(modalRoot);
  }
  return modalRoot;
}

/** 确认框：返回 Promise<boolean>（确定 true / 取消或点击遮罩 false） */
export function confirmModal(opts: ModalOptions): Promise<boolean> {
  return new Promise((resolve) => {
    const root = getRoot();
    const overlay = el('div', { class: 'modal-overlay' });
    const card = el('div', { class: 'modal-card' });
    if (opts.title) card.appendChild(el('h3', { class: 'modal-title', text: opts.title }));
    card.appendChild(el('p', { class: 'modal-message', text: opts.message }));
    const actions = el('div', { class: 'modal-actions' });
    const cancel = el('button', { class: 'btn', text: opts.cancelText ?? '取消' });
    const confirm = el('button', { class: opts.danger ? 'btn danger' : 'btn primary', text: opts.confirmText ?? '确定' });
    const close = (result: boolean): void => {
      overlay.remove();
      resolve(result);
    };
    cancel.addEventListener('click', () => close(false));
    confirm.addEventListener('click', () => close(true));
    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) close(false);
    });
    actions.appendChild(cancel);
    actions.appendChild(confirm);
    card.appendChild(actions);
    overlay.appendChild(card);
    root.appendChild(overlay);
  });
}

/** 提示框：返回 Promise<void>（点击确定后 resolve） */
export function alertModal(opts: Omit<ModalOptions, 'cancelText' | 'confirmText'> & { okText?: string }): Promise<void> {
  return new Promise((resolve) => {
    const root = getRoot();
    const overlay = el('div', { class: 'modal-overlay' });
    const card = el('div', { class: 'modal-card' });
    if (opts.title) card.appendChild(el('h3', { class: 'modal-title', text: opts.title }));
    card.appendChild(el('p', { class: 'modal-message', text: opts.message }));
    const actions = el('div', { class: 'modal-actions' });
    const ok = el('button', { class: 'btn primary', text: opts.okText ?? '知道了' });
    ok.addEventListener('click', () => {
      overlay.remove();
      resolve();
    });
    actions.appendChild(ok);
    card.appendChild(actions);
    overlay.appendChild(card);
    root.appendChild(overlay);
  });
}
