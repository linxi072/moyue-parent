// 墨阅小说网前端 · 搜索页（/search/books + /search/corrected 纠错 + /search/recommend 推荐位）
// P1-#7：检索依赖 ES；ES 不可用时后端返回 40002（SERVICE_DEGRADED），前端展示非阻塞降级条并回退热门推荐。
import { el } from '../dom';
import { apiGet, ApiError } from '../api/client';
import type { BookDocument, BookSearchResult } from '../types';

/** 渲染/复用一条降级提示条（ES 不可用时展示，不阻断页面） */
function ensureDegradeBanner(root: HTMLElement): HTMLElement {
  let banner = root.querySelector('.degrade-banner') as HTMLElement | null;
  if (!banner) {
    banner = el('div', { class: 'degrade-banner' });
    root.insertBefore(banner, root.querySelector('.book-grid'));
  }
  banner.style.display = '';
  return banner;
}

function hideDegradeBanner(root: HTMLElement): void {
  const banner = root.querySelector('.degrade-banner') as HTMLElement | null;
  if (banner) banner.style.display = 'none';
}

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
    hideDegradeBanner(root);

    const loadRecommend = async (): Promise<void> => {
      const rec = await apiGet<BookDocument[]>('/search/recommend', { limit: 8, sort: 'hot' });
      renderCards(grid, rec);
    };

    try {
      if (!keyword) {
        hint.textContent = '推荐位（按热度）：';
        await loadRecommend();
      } else {
        hint.textContent = '搜索中…';
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
      }
    } catch (e) {
      // P1-#7：ES 不可用 → 非阻塞降级条 + 热门推荐兜底，不当成硬错误
      if (e instanceof ApiError && e.code === 40002) {
        ensureDegradeBanner(root).textContent = '检索服务暂时不可用（已进入降级模式），已为你展示热门推荐。';
        hint.textContent = '';
        try {
          await loadRecommend();
        } catch {
          /* 推荐也失败则留空，避免反复报错 */
        }
      } else {
        hint.textContent = '搜索失败：' + (e as Error).message;
      }
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
