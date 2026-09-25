// 墨阅小说网前端 · 搜索页（/search/books + /search/corrected 纠错 + /search/recommend 推荐位）
import { el } from '../dom';
import { apiGet } from '../api/client';
import type { BookDocument, BookSearchResult } from '../types';

export async function renderSearch(root: HTMLElement, q: string): Promise<void> {
  root.replaceChildren();

  root.appendChild(el('h2', { text: '搜索' }));

  const input = el('input', {
    class: 'input',
    type: 'text',
    placeholder: '书名 / 作者 / 分类',
  });
  input.value = q;

  const sort = el('select', { class: 'input search-sort' });
  for (const [v, label] of [
    ['relevance', '按相关度'],
    ['hot', '按热度'],
    ['latest', '按最新'],
  ] as [string, string][]) {
    sort.appendChild(el('option', { value: v, text: label }));
  }

  const btn = el('button', { class: 'btn primary', text: '搜索' });
  const form = el('div', { class: 'search-form' });
  form.appendChild(input);
  form.appendChild(sort);
  form.appendChild(btn);

  const hint = el('p', { class: 'muted' });
  const grid = el('div', { class: 'book-grid' });
  root.appendChild(form);
  root.appendChild(hint);
  root.appendChild(grid);

  // 关键词只从 URL 读取（#/search?q=），不回写地址，避免 hash 变更触发路由重渲染导致重复请求
  const run = async (): Promise<void> => {
    const keyword = input.value.trim();
    grid.replaceChildren();
    hint.textContent = '';

    if (!keyword) {
      hint.textContent = '推荐位（按热度）：';
      try {
        const rec = await apiGet<BookDocument[]>('/search/recommend', { limit: 8, sort: 'hot' });
        renderCards(grid, rec);
      } catch (e) {
        hint.textContent = '推荐加载失败：' + (e as Error).message;
      }
      return;
    }

    hint.textContent = '搜索中…';
    try {
      const res = await apiGet<BookSearchResult>('/search/corrected', {
        keyword,
        sort: sort.value,
        page: 1,
        size: 20,
      });
      const list = res.records ?? [];
      hint.textContent =
        (res.correctedKeyword ? `已按「${res.correctedKeyword}」召回；` : '') +
        `共 ${res.total ?? list.length} 条结果`;
      if (list.length === 0) {
        grid.appendChild(el('p', { class: 'muted', text: '没有找到相关书籍' }));
        return;
      }
      renderCards(grid, list);
    } catch (e) {
      hint.textContent = '搜索失败：' + (e as Error).message;
    }
  };

  btn.addEventListener('click', () => void run());
  input.addEventListener('keydown', (e) => {
    if ((e as KeyboardEvent).key === 'Enter') void run();
  });

  await run();
}

function renderCards(grid: HTMLElement, list: BookDocument[]): void {
  for (const b of list) {
    const card = el('a', { class: 'book-card', href: `#/book/${b.bookId}` });
    if (b.coverUrl) {
      card.appendChild(el('img', { class: 'cover-img', src: b.coverUrl, alt: b.title ?? '' }));
    } else {
      card.appendChild(el('div', { class: 'cover', text: (b.title ?? '?').slice(0, 1) }));
    }
    card.appendChild(el('div', { class: 'book-title', text: b.title ?? '未命名' }));
    card.appendChild(el('div', { class: 'book-author', text: b.authorName ?? '' }));
    card.appendChild(el('div', { class: 'book-cat', text: b.categoryName ?? '' }));
    grid.appendChild(card);
  }
}
