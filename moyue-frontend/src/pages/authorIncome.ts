// 墨阅小说网前端 · 作者稿酬 / 结算单
// 消费：GET /author/income/{authorId}、GET /author/settlements、GET /author/settlements/{id}
import { el } from '../dom';
import { apiGet } from '../api/client';
import { getSession } from '../auth';
import type { AuthorIncomeDTO, SettlementDTO } from '../types';

/** 稿酬类型标签（对齐 AuthorIncomeDTO.incomeType） */
function incomeTypeLabel(t?: number): string {
  return t === 1 ? '订阅' : t === 2 ? '打赏分成' : t === 3 ? '全勤奖' : t === 4 ? '买断分成' : '其他';
}

/** 结算单状态标签（对齐 SettlementDTO.status） */
function settleStatusLabel(s?: number): string {
  return s === 0 ? '待结算' : s === 1 ? '已结算待打款' : s === 2 ? '已打款' : s === 3 ? '打款失败' : '未知';
}

export async function renderAuthorIncome(root: HTMLElement): Promise<void> {
  root.replaceChildren();
  const session = getSession();
  if (!session) {
    root.appendChild(el('p', { class: 'msg', text: '请先登录后再查看稿酬。' }));
    root.appendChild(el('a', { class: 'btn-link', href: '#/login', text: '去登录' }));
    return;
  }
  const uid = session.userId;

  root.appendChild(el('h2', { text: '作者稿酬' }));
  root.appendChild(el('a', { class: 'btn-link', href: '#/author', text: '← 返回作者工作台' }));
  const wrap = el('div', { class: 'income-wrap' });
  root.appendChild(wrap);
  wrap.appendChild(el('p', { class: 'muted', text: '加载中…' }));

  try {
    const [incomeList, settlements] = await Promise.all([
      apiGet<AuthorIncomeDTO[]>(`/author/income/${uid}`),
      apiGet<SettlementDTO[]>('/author/settlements'),
    ]);
    wrap.replaceChildren();

    // 稿酬汇总
    const total = (incomeList ?? []).reduce((s, x) => s + (x.amount ?? 0), 0);
    const summary = el('div', { class: 'card income-summary' });
    summary.appendChild(el('div', { class: 'muted', text: '累计稿酬（元）' }));
    summary.appendChild(el('div', { class: 'income-total', text: total.toFixed(2) }));
    const byMonth = new Map<string, number>();
    (incomeList ?? []).forEach((x) => {
      const m = x.settleMonth ?? '未结算';
      byMonth.set(m, (byMonth.get(m) ?? 0) + (x.amount ?? 0));
    });
    const monthText = [...byMonth.entries()].map(([m, v]) => `${m}: ${v.toFixed(2)}`).join('  ·  ');
    summary.appendChild(el('div', { class: 'muted', text: monthText || '暂无明细' }));
    wrap.appendChild(summary);

    // 稿酬流水
    const incSection = el('div', { class: 'profile-section' });
    incSection.appendChild(el('h3', { text: '稿酬明细' }));
    const incList = el('div', { class: 'income-list' });
    if ((incomeList ?? []).length === 0) {
      incList.appendChild(el('p', { class: 'muted', text: '暂无稿酬流水。' }));
    } else {
      (incomeList ?? []).forEach((x) => {
        const row = el('div', { class: 'income-row' });
        row.appendChild(el('span', { class: 'flow-tag', text: incomeTypeLabel(x.incomeType) }));
        row.appendChild(el('span', { class: 'flow-remark', text: `作品 #${x.bookId ?? '-'} · ${x.settleMonth ?? ''}` }));
        row.appendChild(el('span', { class: 'flow-points plus', text: `+¥${(x.amount ?? 0).toFixed(2)}` }));
        incList.appendChild(row);
      });
    }
    incSection.appendChild(incList);
    wrap.appendChild(incSection);

    // 结算单
    const setSection = el('div', { class: 'profile-section' });
    setSection.appendChild(el('h3', { text: '结算单' }));
    const setList = el('div', { class: 'income-list' });
    if ((settlements ?? []).length === 0) {
      setList.appendChild(el('p', { class: 'muted', text: '暂无结算单。' }));
    } else {
      (settlements ?? []).forEach((s) => {
        const row = el('div', { class: 'income-row' });
        row.appendChild(el('span', { class: 'flow-tag', text: s.period ?? '' }));
        row.appendChild(el('span', { class: 'flow-remark', text: `渠道 ${s.payChannel ?? '—'} · ${s.remark ?? ''}` }));
        const badge = el('span', { class: 'badge', text: settleStatusLabel(s.status) });
        row.appendChild(badge);
        row.appendChild(el('span', { class: 'flow-points plus', text: `¥${(s.totalAmount ?? 0).toFixed(2)}` }));
        setList.appendChild(row);
      });
    }
    setSection.appendChild(setList);
    wrap.appendChild(setSection);
  } catch (e) {
    wrap.replaceChildren(el('p', { class: 'msg', text: (e as Error).message }));
  }
}
