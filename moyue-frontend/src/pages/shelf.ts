// 墨阅小说网前端 · 书架页（read 域 /read/bookshelf）
import { el } from '../dom';
import { apiGet, apiDelete } from '../api/client';
import { getSession } from '../auth';
import type { BookshelfItem, BookSummaryDTO } from '../types';

export async function renderShelf(root: HTMLElement): Promise<void> {
  root.replaceChildren();
  const session = getSession();
  if (!session) {
    root.appendChild(el('p', { class: 'msg', text: '请先登录后再查看书架。' }));
    root.appendChild(el('a', { class: 'btn-link', href: '#/login', text: '去登录' }));
    return;
  }
  const userId = session.userId;

  root.appendChild(el('h2', { text: '我的书架' }));
  const list = el('div', { class: 'shelf-list' });
  root.appendChild(list);
  list.appendChild(el('p', { class: 'muted', text: '加载中…' }));

  const load = async (): Promise<void> => {
    list.replaceChildren();
    try {
      const items = await apiGet<BookshelfItem[]>(`/read/bookshelf/${userId}`);
      if (!items || items.length === 0) {
        list.appendChild(el('p', { class: 'muted', text: '书架还是空的，去书城挑几本吧。' }));
        return;
      }
      // 书架条目只含 bookId，书名需逐个拉详情（失败单本降级，不中断整体）
      const details = await Promise.all(
        items.map((it) =>
          apiGet<BookSummaryDTO>(`/books/${it.bookId}`).catch(() => undefined)
        )
      );
      items.forEach((it, i) => {
        const book = details[i];
        const row = el('div', { class: 'shelf-row' });

        const title = el('a', {
          class: 'shelf-title',
          href: `#/book/${it.bookId}`,
          text: book?.title ?? `作品 #${it.bookId}`,
        });
        row.appendChild(title);

        if (it.lastChapterId) {
          row.appendChild(
            el('a', { class: 'btn-link', href: `#/read/${it.lastChapterId}`, text: '继续阅读' })
          );
        } else {
          row.appendChild(el('span', { class: 'muted', text: '未开始阅读' }));
        }

        const del = el('button', {
          class: 'btn-link danger',
          text: '移出书架',
          onclick: async () => {
            try {
              await apiDelete<void>(`/read/bookshelf/${it.bookId}`);
              await load();
            } catch (e) {
              row.appendChild(el('span', { class: 'msg', text: (e as Error).message }));
            }
          },
        });
        row.appendChild(del);
        list.appendChild(row);
      });
    } catch (e) {
      list.replaceChildren();
      list.appendChild(el('p', { class: 'msg', text: (e as Error).message }));
    }
  };

  await load();
}
