// 墨阅小说网前端 · AI 智能客服对话页
import { el } from '../dom';
import { apiPost } from '../api/client';
import { getSession } from '../auth';
import type { AiMessageEntity } from '../types';

export async function renderAiChat(root: HTMLElement): Promise<void> {
  root.replaceChildren();
  const session = getSession();
  if (!session) {
    root.appendChild(el('p', { class: 'msg', text: '请先登录后再使用 AI 客服。' }));
    root.appendChild(el('a', { class: 'btn-link', href: '#/login', text: '去登录' }));
    return;
  }
  const userId = session.userId;

  root.appendChild(el('h2', { text: 'AI 智能客服' }));

  const box = el('div', { class: 'chat-box' });
  const input = el('input', { class: 'input', type: 'text', placeholder: '输入你的问题，回车发送…' });
  const send = el('button', { class: 'btn primary', text: '发送' });

  const form = el('div', { class: 'chat-form' });
  form.appendChild(input);
  form.appendChild(send);
  root.appendChild(box);
  root.appendChild(form);

  const appendMsg = (role: number, text: string): void => {
    const bubble = el('div', { class: role === 1 ? 'bubble user' : 'bubble bot' });
    bubble.textContent = text;
    box.appendChild(bubble);
    box.scrollTop = box.scrollHeight;
  };

  const ask = async (): Promise<void> => {
    const text = input.value.trim();
    if (!text) return;
    input.value = '';
    appendMsg(1, text);
    try {
      const reply = await apiPost<AiMessageEntity>('/ai/chat', { userId, content: text });
      appendMsg(2, reply.content ?? '（无回复）');
    } catch (e) {
      appendMsg(2, '出错了：' + (e as Error).message);
    }
  };

  send.addEventListener('click', () => {
    void ask();
  });
  input.addEventListener('keydown', (e) => {
    if ((e as KeyboardEvent).key === 'Enter') void ask();
  });
}
