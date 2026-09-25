// 墨阅小说网前端 · AI 智能客服（多轮会话：历史列表 / 切换 / 删除 / 新建）
import { el } from '../dom';
import { apiGet, apiPost, apiDelete } from '../api/client';
import { getSession } from '../auth';
import type { AiMessageEntity, AiSession, PageResult } from '../types';

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

  const layout = el('div', { class: 'chat-layout' });
  const aside = el('aside', { class: 'session-panel' });
  const main = el('div', { class: 'chat-main' });
  layout.appendChild(aside);
  layout.appendChild(main);
  root.appendChild(layout);

  const box = el('div', { class: 'chat-box' });
  const input = el('input', { class: 'input', type: 'text', placeholder: '输入你的问题，回车发送…' });
  const send = el('button', { class: 'btn primary', text: '发送' });
  const form = el('div', { class: 'chat-form' });
  form.appendChild(input);
  form.appendChild(send);
  main.appendChild(box);
  main.appendChild(form);

  let currentSessionId: number | undefined;

  const appendMsg = (role: number, text: string): void => {
    const bubble = el('div', { class: role === 1 ? 'bubble user' : 'bubble bot' });
    bubble.textContent = text;
    box.appendChild(bubble);
    box.scrollTop = box.scrollHeight;
  };

  // ---------- 会话历史 ----------
  const items = el('div', { class: 'session-items' });
  const newBtn = el('button', {
    class: 'btn',
    text: '+ 新建会话',
    onclick: () => {
      currentSessionId = undefined;
      box.replaceChildren();
      void loadSessions();
    },
  });
  aside.appendChild(newBtn);
  aside.appendChild(items);

  const loadSessions = async (): Promise<void> => {
    try {
      const page = await apiGet<PageResult<AiSession>>('/ai/sessions', {
        userId,
        page: 1,
        size: 20,
      });
      items.replaceChildren();
      const list = page.records ?? [];
      if (list.length === 0) {
        items.appendChild(el('p', { class: 'muted', text: '暂无历史会话' }));
        return;
      }
      for (const s of list) {
        const row = el('div', { class: 'session-row' });
        if (currentSessionId != null && currentSessionId === s.id) {
          row.className = 'session-row active';
        }
        row.appendChild(
          el('button', {
            class: 'btn-link',
            text: s.title ?? `会话 ${s.id}`,
            onclick: () => void openSession(s.id),
          })
        );
        row.appendChild(
          el('button', {
            class: 'btn-link danger',
            text: '×',
            onclick: async () => {
              try {
                await apiDelete<void>(`/ai/sessions/${s.id}`, { userId });
                if (currentSessionId === s.id) {
                  currentSessionId = undefined;
                  box.replaceChildren();
                }
                await loadSessions();
              } catch (e) {
                row.appendChild(el('span', { class: 'msg', text: (e as Error).message }));
              }
            },
          })
        );
        items.appendChild(row);
      }
    } catch (e) {
      items.replaceChildren();
      items.appendChild(el('p', { class: 'msg', text: (e as Error).message }));
    }
  };

  const openSession = async (id?: number): Promise<void> => {
    currentSessionId = id;
    box.replaceChildren();
    if (id == null) return;
    try {
      const msgs = await apiGet<AiMessageEntity[]>(`/ai/sessions/${id}/messages`, { userId });
      for (const m of msgs) appendMsg(m.role ?? 2, m.content ?? '');
      await loadSessions();
    } catch (e) {
      appendMsg(2, '加载历史失败：' + (e as Error).message);
    }
  };

  // ---------- 对话 ----------
  const ask = async (): Promise<void> => {
    const text = input.value.trim();
    if (!text) return;
    input.value = '';
    appendMsg(1, text);
    try {
      const reply = await apiPost<AiMessageEntity>('/ai/chat', {
        userId,
        sessionId: currentSessionId,
        content: text,
      });
      appendMsg(2, reply.content ?? '（无回复）');
      // 首轮自动建会话：拿到 sessionId 后同步历史列表
      if (currentSessionId == null && reply.sessionId != null) {
        currentSessionId = reply.sessionId;
        await loadSessions();
      }
    } catch (e) {
      appendMsg(2, '出错了：' + (e as Error).message);
    }
  };

  send.addEventListener('click', () => void ask());
  input.addEventListener('keydown', (e) => {
    if ((e as KeyboardEvent).key === 'Enter') void ask();
  });

  await loadSessions();
}
