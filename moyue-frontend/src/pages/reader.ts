// 墨阅小说网前端 · 章节阅读器（正文 + 上下章导航 + 加入书架 + 阅读进度上报）
import { el } from '../dom';
import { apiGet, apiPost, apiPut } from '../api/client';
import { getSession } from '../auth';
import type { ChapterDTO, PageResult } from '../types';

export async function renderReader(root: HTMLElement, chapterId: number): Promise<void> {
  root.replaceChildren();
  root.appendChild(el('p', { class: 'muted', text: '加载中…' }));

  try {
    const ch = await apiGet<ChapterDTO>(`/chapters/${chapterId}`);
    root.replaceChildren();
    const bookId = ch.bookId;

    root.appendChild(
      el('h2', { class: 'chapter-title', text: `第${ch.chapterNo ?? '?'}章 ${ch.title ?? ''}` })
    );

    const content = el('article', { class: 'chapter-content' });
    content.textContent = ch.content && ch.content.length > 0 ? ch.content : '（本章暂无正文）';
    root.appendChild(content);

    // 阅读进度上报（需登录；失败仅降级，不影响阅读）
    if (getSession() && bookId != null) {
      void apiPut<void>(`/read/bookshelf/${bookId}/progress`, { chapterId }).catch(() => undefined);
    }

    // 定位上下章：目录接口取全量章节 ID 序列
    let prevId: number | undefined;
    let nextId: number | undefined;
    if (bookId != null) {
      try {
        const page = await apiGet<PageResult<ChapterDTO>>('/chapters', {
          bookId,
          page: 1,
          size: 200,
        });
        const ids = (page.records ?? [])
          .map((c) => c.id)
          .filter((x): x is number => typeof x === 'number');
        const i = ids.indexOf(chapterId);
        if (i > 0) prevId = ids[i - 1];
        if (i >= 0 && i < ids.length - 1) nextId = ids[i + 1];
      } catch {
        // 目录拉取失败不阻断正文阅读
      }
    }

    const nav = el('div', { class: 'reader-nav' });
    if (prevId != null) {
      const pid = prevId;
      nav.appendChild(
        el('button', { class: 'btn', text: '← 上一章', onclick: () => go(`#/read/${pid}`) })
      );
    }
    if (nextId != null) {
      const nid = nextId;
      nav.appendChild(
        el('button', { class: 'btn', text: '下一章 →', onclick: () => go(`#/read/${nid}`) })
      );
    }
    nav.appendChild(el('a', { class: 'btn-link', href: '#/', text: '返回书城' }));
    root.appendChild(nav);

    // 加入书架（幂等）
    if (getSession() && bookId != null) {
      const bid = bookId;
      const bar = el('div', { class: 'reader-actions' });
      const add = el('button', {
        class: 'btn',
        text: '加入书架',
        onclick: async () => {
          try {
            await apiPost<void>('/read/bookshelf', { bookId: bid });
            add.textContent = '已在书架 ✓';
            add.setAttribute('disabled', 'true');
          } catch (e) {
            bar.appendChild(el('span', { class: 'msg', text: (e as Error).message }));
          }
        },
      });
      bar.appendChild(add);
      root.appendChild(bar);
    }
  } catch (e) {
    root.replaceChildren();
    root.appendChild(el('p', { class: 'msg', text: (e as Error).message }));
  }
}

/** 跳转：hash 相同时手动重载，避免 router 不触发导致页面不刷新 */
function go(hash: string): void {
  if ((location.hash || '#/') === hash) {
    location.reload();
    return;
  }
  location.hash = hash;
}
