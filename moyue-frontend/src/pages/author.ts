// 墨阅小说网前端 · 作者工作台（建书 / 写章 / 发布）
// 消费：GET /books/mine、POST /books、GET /chapters/drafts?bookId=、
//      GET /chapters?bookId=、POST /chapters、POST /chapters/{id}/publish
import { el } from '../dom';
import { apiGet, apiPost } from '../api/client';
import { getSession } from '../auth';
import type { BookSummaryDTO, ChapterDTO } from '../types';

/** 作品状态标签（对齐 BookSummaryDTO.status） */
function bookStatusLabel(s?: number): string {
  return s === 2 ? '已完结' : s === 3 ? '已下架' : '连载中';
}

/** 章节状态标签（对齐 ChapterDTO.status：0 草稿 / 1 审核中 / 2 已发布 / 3 已驳回） */
function chapterStatusLabel(s?: number): string {
  return s === 0 ? '草稿' : s === 1 ? '审核中' : s === 2 ? '已发布' : s === 3 ? '已驳回' : '未知';
}

export async function renderAuthor(root: HTMLElement): Promise<void> {
  root.replaceChildren();
  const session = getSession();
  if (!session) {
    root.appendChild(el('p', { class: 'msg', text: '请先登录后再进入作者工作台。' }));
    root.appendChild(el('a', { class: 'btn-link', href: '#/login', text: '去登录' }));
    return;
  }
  const uid = session.userId;

  root.appendChild(el('h2', { text: '作者工作台' }));

  // 加载分类（用于建书下拉）
  let categories: { id?: number; name?: string }[] = [];
  try {
    categories = await apiGet<{ id?: number; name?: string }[]>('/categories');
  } catch {
    categories = [];
  }

  // ---- 新建作品 ----
  const createSection = el('div', { class: 'author-section' });
  createSection.appendChild(el('h3', { text: '新建作品' }));
  const form = el('div', { class: 'author-form' });
  const titleInput = el('input', { class: 'input', placeholder: '作品名称' }) as HTMLInputElement;
  const catSelect = el('select', { class: 'input' }) as HTMLSelectElement;
  catSelect.appendChild(el('option', { value: '', text: '选择分类' }));
  categories.forEach((c) => catSelect.appendChild(el('option', { value: String(c.id), text: c.name ?? '' })));
  const introInput = el('textarea', { class: 'input', placeholder: '作品简介' }) as HTMLTextAreaElement;
  introInput.style.minHeight = '70px';
  const tagsInput = el('input', { class: 'input', placeholder: '标签（逗号分隔，可选）' }) as HTMLInputElement;
  const createMsg = el('span', { class: 'muted', style: 'margin-left:12px;font-size:13px;' });
  const createBtn = el('button', { class: 'btn primary', text: '创建作品' });
  createBtn.addEventListener('click', async () => {
    const title = titleInput.value.trim();
    if (!title) {
      createMsg.textContent = '请填写作品名称';
      return;
    }
    createBtn.disabled = true;
    createMsg.textContent = '创建中…';
    try {
      await apiPost<BookSummaryDTO>('/books', {
        title,
        categoryId: catSelect.value ? Number(catSelect.value) : null,
        intro: introInput.value.trim(),
        tags: tagsInput.value.trim(),
        coverUrl: '',
      });
      createMsg.textContent = '创建成功';
      titleInput.value = '';
      introInput.value = '';
      tagsInput.value = '';
      await loadMyBooks();
    } catch (e) {
      createMsg.textContent = (e as Error).message;
    } finally {
      createBtn.disabled = false;
    }
  });
  form.appendChild(el('label', { text: '作品名称' }));
  form.appendChild(titleInput);
  form.appendChild(el('label', { text: '分类' }));
  form.appendChild(catSelect);
  form.appendChild(el('label', { text: '简介' }));
  form.appendChild(introInput);
  form.appendChild(el('label', { text: '标签' }));
  form.appendChild(tagsInput);
  form.appendChild(el('div', {}, [createBtn, createMsg]));
  createSection.appendChild(form);
  root.appendChild(createSection);

  // ---- 我的作品 ----
  const mySection = el('div', { class: 'author-section' });
  mySection.appendChild(el('h3', { text: '我的作品' }));
  const bookList = el('div', { class: 'book-row-list' });
  mySection.appendChild(bookList);
  root.appendChild(mySection);

  // ---- 写作器（依赖选中的作品） ----
  const writeSection = el('div', { class: 'author-section' });
  writeSection.appendChild(el('h3', { text: '写作 / 发布' }));
  const writeHint = el('p', { class: 'muted', text: '从上方「我的作品」选择一本开始写作。' });
  writeSection.appendChild(writeHint);
  const writeBody = el('div', { class: 'write-body' });
  writeSection.appendChild(writeBody);
  root.appendChild(writeSection);

  let selectedBookId: number | null = null;
  let editingChapterId: number | null = null;

  async function loadMyBooks(): Promise<void> {
    bookList.replaceChildren();
    try {
      const page = await apiGet<{ records?: BookSummaryDTO[] }>('/books/mine?page=1&size=50');
      const books = page.records ?? [];
      if (books.length === 0) {
        bookList.appendChild(el('p', { class: 'muted', text: '还没有作品，先新建一本吧。' }));
        return;
      }
      books.forEach((b) => {
        const row = el('div', { class: 'book-row' });
        row.appendChild(el('span', { class: 'book-row-title', text: b.title ?? '' }));
        row.appendChild(el('span', { class: 'muted', text: `${bookStatusLabel(b.status)} · ${b.wordCount ?? 0} 字` }));
        const sel = el('button', { class: 'btn-link', text: '进入写作' });
        sel.addEventListener('click', () => {
          selectedBookId = b.bookId ?? null;
          editingChapterId = null;
          writeHint.textContent = `正在写作：《${b.title}》`;
          void openWriter();
        });
        row.appendChild(sel);
        bookList.appendChild(row);
      });
    } catch (e) {
      bookList.replaceChildren(el('p', { class: 'msg', text: (e as Error).message }));
    }
  }

  async function openWriter(): Promise<void> {
    if (!selectedBookId) return;
    writeBody.replaceChildren();
    const left = el('div', { class: 'toc-panel' });
    left.appendChild(el('div', { class: 'toc-title', text: '目录 / 草稿箱' }));
    const tocList = el('div', { class: 'toc-list' });
    left.appendChild(tocList);

    const right = el('div', { class: 'editor-panel' });
    const no = el('input', { class: 'input', placeholder: '章号（如 3）' }) as HTMLInputElement;
    const chTitle = el('input', { class: 'input', placeholder: '章节标题' }) as HTMLInputElement;
    const content = el('textarea', { class: 'input', placeholder: '正文…' }) as HTMLTextAreaElement;
    content.style.minHeight = '240px';
    const statusSel = el('select', { class: 'input' }) as HTMLSelectElement;
    statusSel.appendChild(el('option', { value: '0', text: '保存为草稿' }));
    statusSel.appendChild(el('option', { value: '1', text: '提交审核' }));
    const editorMsg = el('span', { class: 'muted', style: 'margin-left:12px;font-size:13px;' });

    const saveBtn = el('button', { class: 'btn primary', text: '保存' });
    saveBtn.addEventListener('click', async () => {
      if (!selectedBookId) return;
      const chapterNo = no.value.trim() ? Number(no.value.trim()) : undefined;
      saveBtn.disabled = true;
      editorMsg.textContent = '保存中…';
      try {
        const ch = await apiPost<ChapterDTO>('/chapters', {
          bookId: selectedBookId,
          title: chTitle.value.trim() || `第${chapterNo ?? '?'}章`,
          content: content.value,
          chapterNo,
          status: Number(statusSel.value),
        });
        editingChapterId = ch.id ?? null;
        editorMsg.textContent = '已保存';
        await loadToc();
      } catch (e) {
        editorMsg.textContent = (e as Error).message;
      } finally {
        saveBtn.disabled = false;
      }
    });

    const publishTime = el('input', { class: 'input', type: 'datetime-local', placeholder: '定时发布时间（可选）' }) as HTMLInputElement;
    const publishBtn = el('button', { class: 'btn', text: '发布选中章' });
    publishBtn.addEventListener('click', async () => {
      if (!editingChapterId) {
        editorMsg.textContent = '请先在右侧保存/选择一章';
        return;
      }
      publishBtn.disabled = true;
      editorMsg.textContent = '发布中…';
      try {
        const pt = publishTime.value ? new Date(publishTime.value).toISOString() : null;
        const ch = await apiPost<ChapterDTO>(`/chapters/${editingChapterId}/publish`, pt ? { publishTime: pt } : {});
        editorMsg.textContent = pt ? `已提交定时发布（${chapterStatusLabel(ch.status)}）` : '已发布';
        await loadToc();
      } catch (e) {
        editorMsg.textContent = (e as Error).message;
      } finally {
        publishBtn.disabled = false;
      }
    });

    right.appendChild(el('label', { text: '章号' }));
    right.appendChild(no);
    right.appendChild(el('label', { text: '标题' }));
    right.appendChild(chTitle);
    right.appendChild(el('label', { text: '正文' }));
    right.appendChild(content);
    right.appendChild(el('label', { text: '保存方式' }));
    right.appendChild(statusSel);
    right.appendChild(el('div', {}, [saveBtn, editorMsg]));
    right.appendChild(el('label', { text: '定时发布（留空立即发布）' }));
    right.appendChild(publishTime);
    right.appendChild(publishBtn);

    writeBody.appendChild(left);
    writeBody.appendChild(right);

    async function loadToc(): Promise<void> {
      tocList.replaceChildren();
      try {
        const page = await apiGet<{ records?: ChapterDTO[] }>(`/chapters?bookId=${selectedBookId}&page=1&size=100`);
        const all = page.records ?? [];
        const drafts = await apiGet<{ records?: ChapterDTO[] }>(`/chapters/drafts?bookId=${selectedBookId}&page=1&size=100`);
        const draftIds = new Set((drafts.records ?? []).map((d) => d.id));
        if (all.length === 0) {
          tocList.appendChild(el('p', { class: 'muted', text: '尚无章节，右侧写一章吧。' }));
          return;
        }
        all.forEach((c) => {
          const item = el('div', { class: 'toc-item' });
          const isDraft = draftIds.has(c.id);
          item.appendChild(el('span', {
            class: 'toc-no',
            text: `第${c.chapterNo}章`,
          }));
          item.appendChild(el('span', { class: 'toc-name', text: c.title ?? '' }));
          item.appendChild(el('span', {
            class: isDraft ? 'badge draft' : 'badge',
            text: chapterStatusLabel(c.status),
          }));
          const load = el('button', { class: 'btn-link', text: '载入' });
          load.addEventListener('click', () => {
            editingChapterId = c.id ?? null;
            no.value = String(c.chapterNo ?? '');
            chTitle.value = c.title ?? '';
            content.value = c.content ?? '';
            statusSel.value = String(c.status === 2 ? 1 : (c.status ?? 0));
            editorMsg.textContent = `已载入第${c.chapterNo}章`;
          });
          item.appendChild(load);
          tocList.appendChild(item);
        });
      } catch (e) {
        tocList.replaceChildren(el('p', { class: 'msg', text: (e as Error).message }));
      }
    }

    await loadToc();
  }

  await loadMyBooks();
}
