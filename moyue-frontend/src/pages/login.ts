// 墨阅小说网前端 · 登录页
import { el } from '../dom';
import { apiPost } from '../api/client';
import { saveSession } from '../auth';
import type { LoginVO } from '../types';

export function renderLogin(root: HTMLElement): void {
  root.replaceChildren();

  const card = el('div', { class: 'card auth-card' });
  card.appendChild(el('h2', { text: '登录墨阅' }));

  const phone = el('input', { class: 'input', type: 'text', placeholder: '手机号' });
  const pwd = el('input', { class: 'input', type: 'password', placeholder: '密码' });
  const msg = el('p', { class: 'msg' });

  const submit = el('button', {
    class: 'btn primary',
    text: '登录',
    onclick: async () => {
      msg.textContent = '';
      submit.setAttribute('disabled', 'true');
      try {
        const vo = await apiPost<LoginVO>('/auth/login', {
          phone: phone.value,
          password: pwd.value,
        });
        saveSession(vo);
        location.hash = '#/';
        location.reload();
      } catch (e) {
        msg.textContent = (e as Error).message || '登录失败';
        submit.removeAttribute('disabled');
      }
    },
  });

  card.appendChild(el('label', { text: '手机号' }));
  card.appendChild(phone);
  card.appendChild(el('label', { text: '密码' }));
  card.appendChild(pwd);
  card.appendChild(submit);
  card.appendChild(msg);
  card.appendChild(
    el('p', { class: 'hint', text: '提示：单体无网关，前端直连 demo 在登录后解码 JWT 自带 X-User-Id。' })
  );

  root.appendChild(card);
}
