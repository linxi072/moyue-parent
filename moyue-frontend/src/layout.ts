// 墨阅小说网前端 · 应用外壳（顶部导航 + 内容出口）
import { el } from './dom';
import { clearSession, getSession } from './auth';

/** 渲染外壳，返回内容出口元素 */
export function renderShell(app: HTMLElement): { outlet: HTMLElement } {
  app.replaceChildren();

  const nav = el('nav', { class: 'topnav' });
  nav.appendChild(el('a', { class: 'brand', href: '#/', text: '墨阅小说网' }));

  const links = el('div', { class: 'nav-links' });
  links.appendChild(el('a', { href: '#/', text: '书城' }));
  links.appendChild(el('a', { href: '#/ai', text: 'AI 客服' }));
  nav.appendChild(links);

  const right = el('div', { class: 'nav-right' });
  const session = getSession();
  if (session) {
    right.appendChild(el('span', { class: 'uid', text: `UID:${session.userId}` }));
    right.appendChild(
      el('button', {
        class: 'btn-link',
        text: '退出',
        onclick: () => {
          clearSession();
          location.hash = '#/';
          location.reload();
        },
      })
    );
  } else {
    right.appendChild(el('a', { href: '#/login', text: '登录' }));
  }
  nav.appendChild(right);
  app.appendChild(nav);

  const outlet = el('main', { class: 'outlet' });
  app.appendChild(outlet);
  return { outlet };
}
