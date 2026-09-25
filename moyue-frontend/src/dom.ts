// 墨阅小说网前端 · 轻量 DOM 构建助手（零 UI 框架，原生 TS 渲染）

export interface ElProps {
  class?: string;
  text?: string;
  html?: string;
  href?: string;
  src?: string;
  alt?: string;
  type?: string;
  value?: string;
  placeholder?: string;
  onclick?: (e: MouseEvent) => void;
  oninput?: (e: Event) => void;
  [key: string]: unknown;
}

/** 创建元素：props 设置属性/文本/事件，children 追加子节点 */
export function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  props: ElProps = {},
  children: (Node | string)[] = []
): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (v == null) continue;
    if (k === 'class') node.className = String(v);
    else if (k === 'text') node.textContent = String(v);
    else if (k === 'html') node.innerHTML = String(v);
    else if (k === 'onclick') node.addEventListener('click', v as EventListener);
    else if (k === 'oninput') node.addEventListener('input', v as EventListener);
    else node.setAttribute(k, String(v));
  }
  for (const c of children) {
    node.appendChild(typeof c === 'string' ? document.createTextNode(c) : c);
  }
  return node;
}

/** 清空容器 */
export function clear(node: HTMLElement): void {
  node.replaceChildren();
}
