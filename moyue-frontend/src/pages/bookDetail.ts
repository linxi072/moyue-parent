// 墨阅小说网前端 · 书籍详情 + 目录页
import { el } from '../dom';
import { apiGet } from '../api/client';
import type { BookSummaryDTO, ChapterDTO, PageResult } from '../types';

function statusText(s?: number): string {
  if (s === 1) return '连载中';
  if (s === 2) return '已完结';
  if (s === 3) return '已下架';
  return '';
}

export async function renderBookDetail(root: HTMLElement, bookId: number): Promise<void> {
  root.replaceChildren();
  root.appendChild(el('p', { class: 'muted', text: '加载中…' }));

  try {
    const book = await apiGet<BookSummaryDTO>(`/books/${bookId}`);
    root.replaceChildren();

    const header = el('div', { class: 'book-header' });
    header.appendChild(el('h2', { text: book.title ?? '未命名' }));
    header.appendChild(
      el('p', {
        class: 'muted',
        text: `作者：${book.author ?? '未知'}　分类：${book.category ?? '-'}　${statusText(book.status)}　点击：${book.clickCount ?? 0}`,
      })
    );
    if (book.intro) header.appendChild(el('p', { class: 'intro', text: book.intro }));
    root.appendChild(header);

    root.appendChild(el('h3', { text: '目录' }));
    const list = el('ul', { class: 'chapter-list' });
    root.appendChild(list);
    list.appendChild(el('li', { class: 'muted', text: '加载目录…' }));

    const page = await apiGet<PageResult<ChapterDTO>>('/chapters', {
      bookId,
      page: 1,
      size: 100,
    });
    list.replaceChildren();
    const chapters = page.records ?? [];
    if (chapters.length === 0) {
      list.appendChild(el('li', { class: 'muted', text: '暂无章节' }));
      return;
    }
    for (const c of chapters) {
      const li = el('li');
      li.appendChild(
        el('a', { href: `#/read/${c.id}`, text: `第${c.chapterNo ?? '?'}章 ${c.title ?? ''}` })
      );
      list.appendChild(li);
    }
  } catch (e) {
    root.replaceChildren();
    root.appendChild(el('p', { class: 'msg', text: (e as Error).message }));
  }
}
