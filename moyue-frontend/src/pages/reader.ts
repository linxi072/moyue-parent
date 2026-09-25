// 墨阅小说网前端 · 章节阅读器
import { el } from '../dom';
import { apiGet } from '../api/client';
import type { ChapterDTO } from '../types';

export async function renderReader(root: HTMLElement, chapterId: number): Promise<void> {
  root.replaceChildren();
  root.appendChild(el('p', { class: 'muted', text: '加载中…' }));

  try {
    const ch = await apiGet<ChapterDTO>(`/chapters/${chapterId}`);
    root.replaceChildren();

    root.appendChild(
      el('h2', { class: 'chapter-title', text: `第${ch.chapterNo ?? '?'}章 ${ch.title ?? ''}` })
    );

    const content = el('article', { class: 'chapter-content' });
    content.textContent = ch.content && ch.content.length > 0 ? ch.content : '（本章暂无正文）';
    root.appendChild(content);

    root.appendChild(el('a', { class: 'btn-link', href: '#/', text: '← 返回书城' }));
  } catch (e) {
    root.replaceChildren();
    root.appendChild(el('p', { class: 'msg', text: (e as Error).message }));
  }
}
