// 墨阅小说网前端 · 书籍详情（封面 / 加入书架 / 目录 / 书评）
import { el } from '../dom';
import { apiGet, apiPost } from '../api/client';
import { getSession } from '../auth';
import { renderComments } from '../components/comments';
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
    if (book.coverUrl) {
      header.appendChild(el('img', { class: 'detail-cover', src: book.coverUrl, alt: book.title ?? '' }));
    }
    const meta = el('div', { class: 'book-meta' });
    meta.appendChild(el('h2', { text: book.title ?? '未命名' }));
    meta.appendChild(
      el('p', {
        class: 'muted',
        text: `作者：${book.author ?? '未知'}　分类：${book.category ?? '-'}　${statusText(book.status)}　字数：${book.wordCount ?? 0}　点击：${book.clickCount ?? 0}`,
      })
    );
    if (book.intro) meta.appendChild(el('p', { class: 'intro', text: book.intro }));

    // 加入书架（幂等，需登录）
    if (getSession()) {
      const add = el('button', {
        class: 'btn primary',
        text: '加入书架',
        onclick: async () => {
          try {
            await apiPost<void>('/read/bookshelf', { bookId });
            add.textContent = '已在书架 ✓';
            add.setAttribute('disabled', 'true');
          } catch (e) {
            meta.appendChild(el('span', { class: 'msg', text: (e as Error).message }));
          }
        },
      });
      meta.appendChild(add);
    }

    header.appendChild(meta);
    root.appendChild(header);

    // 目录
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
    } else {
      for (const c of chapters) {
        const li = el('li');
        li.appendChild(
          el('a', { href: `#/read/${c.id}`, text: `第${c.chapterNo ?? '?'}章 ${c.title ?? ''}` })
        );
        list.appendChild(li);
      }
    }

    // 书评
    const commentBox = el('div', { class: 'comment-box' });
    root.appendChild(commentBox);
    await renderComments(commentBox, bookId);
  } catch (e) {
    root.replaceChildren();
    root.appendChild(el('p', { class: 'msg', text: (e as Error).message }));
  }
}
