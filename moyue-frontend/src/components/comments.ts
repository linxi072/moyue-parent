// 墨阅小说网前端 · 评论模块（comment 域 /comments：列表 / 发表 / 点赞 / 删除）
import { el } from '../dom';
import { apiGet, apiPost, apiDelete } from '../api/client';
import { getSession } from '../auth';
import type { CommentDTO, PageResult } from '../types';

/** 在给定容器内渲染评论模块并加载数据 */
export async function renderComments(root: HTMLElement, bookId: number): Promise<void> {
  root.replaceChildren();

  root.appendChild(el('h3', { text: '书评' }));

  const session = getSession();
  const list = el('div', { class: 'comment-list' });
  root.appendChild(list);

  if (!session) {
    root.appendChild(el('p', { class: 'muted', text: '登录后可发表评论。' }));
  } else {
    const box = el('textarea', { class: 'input', placeholder: '写下你的看法…' }) as HTMLTextAreaElement;
    const submit = el('button', { class: 'btn primary', text: '发表评论' });
    submit.addEventListener('click', async () => {
      const content = box.value.trim();
      if (!content) return;
      submit.setAttribute('disabled', 'true');
      try {
        await apiPost<unknown>('/comments', { bookId, content });
        box.value = '';
        await load();
      } catch (e) {
        root.appendChild(el('p', { class: 'msg', text: (e as Error).message }));
      } finally {
        submit.removeAttribute('disabled');
      }
    });
    const form = el('div', { class: 'comment-form' });
    form.appendChild(box);
    form.appendChild(submit);
    root.appendChild(form);
  }

  const load = async (): Promise<void> => {
    list.replaceChildren();
    list.appendChild(el('p', { class: 'muted', text: '加载评论…' }));
    try {
      const page = await apiGet<PageResult<CommentDTO>>('/comments', {
        bookId,
        page: 1,
        size: 20,
      });
      const items = page.records ?? [];
      list.replaceChildren();
      if (items.length === 0) {
        list.appendChild(el('p', { class: 'muted', text: '还没有评论，来说两句吧。' }));
        return;
      }
      for (const c of items) {
        list.appendChild(commentRow(c, session?.userId, load));
      }
    } catch (e) {
      list.replaceChildren();
      list.appendChild(el('p', { class: 'msg', text: (e as Error).message }));
    }
  };

  await load();
}

function commentRow(c: CommentDTO, myUserId: number | undefined, reload: () => Promise<void>): HTMLElement {
  const row = el('div', { class: 'comment-item' });
  row.appendChild(el('div', { class: 'comment-meta', text: `用户 ${c.userId ?? '-'} · ${c.createTime ?? ''}` }));
  row.appendChild(el('div', { class: 'comment-body', text: c.content ?? '' }));

  const actions = el('div', { class: 'comment-actions' });

  const like = el('button', {
    class: 'btn-link',
    text: `👍 ${c.likeCount ?? 0}`,
    onclick: async () => {
      try {
        const n = await apiPost<number>(`/comments/${c.id}/like`);
        like.textContent = `👍 ${n}`;
      } catch (e) {
        actions.appendChild(el('span', { class: 'msg', text: (e as Error).message }));
      }
    },
  });
  actions.appendChild(like);

  if (myUserId != null && c.userId != null && myUserId === c.userId) {
    actions.appendChild(
      el('button', {
        class: 'btn-link danger',
        text: '删除',
        onclick: async () => {
          try {
            await apiDelete<void>(`/comments/${c.id}`);
            await reload();
          } catch (e) {
            actions.appendChild(el('span', { class: 'msg', text: (e as Error).message }));
          }
        },
      })
    );
  }
  row.appendChild(actions);
  return row;
}
