// 墨阅小说网前端 · 书城列表页
import { el } from '../dom';
import { apiGet } from '../api/client';
import type { BookSummaryDTO, PageResult } from '../types';

export async function renderBookstore(root: HTMLElement): Promise<void> {
  root.replaceChildren();
  root.appendChild(el('h2', { text: '书城' }));

  const grid = el('div', { class: 'book-grid' });
  root.appendChild(grid);
  grid.appendChild(el('p', { class: 'muted', text: '加载中…' }));

  try {
    const page = await apiGet<PageResult<BookSummaryDTO>>('/books', { page: 1, size: 24 });
    grid.replaceChildren();
    const list = page.records ?? [];
    if (list.length === 0) {
      grid.appendChild(el('p', { class: 'muted', text: '暂无书籍' }));
      return;
    }
    for (const b of list) {
      const item = el('a', { class: 'book-card', href: `#/book/${b.bookId}` });
      if (b.coverUrl) {
        item.appendChild(el('img', { class: 'cover-img', src: b.coverUrl, alt: b.title ?? '' }));
      } else {
        item.appendChild(el('div', { class: 'cover', text: (b.title ?? '?').slice(0, 1) }));
      }
      item.appendChild(el('div', { class: 'book-title', text: b.title ?? '未命名' }));
      item.appendChild(el('div', { class: 'book-author', text: b.author ?? '' }));
      item.appendChild(el('div', { class: 'book-cat', text: b.category ?? '' }));
      grid.appendChild(item);
    }
  } catch (e) {
    grid.replaceChildren();
    grid.appendChild(el('p', { class: 'msg', text: (e as Error).message }));
  }
}
