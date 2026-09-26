// 墨阅小说网前端 · 个人中心 / 积分签到
// 消费：GET /users/me、GET /points/accounts/{userId}、POST /points/check-in、
//      GET /points/flows?userId=、GET /points/products、POST /points/orders、GET /points/orders?userId=
import { el } from '../dom';
import { apiGet, apiPost } from '../api/client';
import { getSession } from '../auth';
import type { UserInfoVO, PointsAccountDTO, PointsFlowEntity, PointsProductDTO, PointsOrderDTO } from '../types';

/** 角色中文标签（对齐 UserInfoVO：1 读者 / 2 作者 / 3 管理员） */
function roleLabel(role?: number): string {
  return role === 2 ? '作者' : role === 3 ? '管理员' : '读者';
}

/** 积分流水业务类型标签（对齐 PointsFlowEntity.bizType） */
function flowLabel(bizType?: number): string {
  switch (bizType) {
    case 1: return '签到';
    case 2: return '阅读时长';
    case 3: return '评论奖励';
    case 4: return '系统发放';
    case 5: return '兑换消费';
    default: return '其他';
  }
}

function maskPhone(p?: string): string {
  if (!p) return '—';
  return p.length === 11 ? `${p.slice(0, 3)}****${p.slice(7)}` : p;
}

export async function renderProfile(root: HTMLElement): Promise<void> {
  root.replaceChildren();
  const session = getSession();
  if (!session) {
    root.appendChild(el('p', { class: 'msg', text: '请先登录后再查看个人中心。' }));
    root.appendChild(el('a', { class: 'btn-link', href: '#/login', text: '去登录' }));
    return;
  }
  const uid = session.userId;

  root.appendChild(el('h2', { text: '个人中心' }));
  const wrap = el('div', { class: 'profile-wrap' });
  root.appendChild(wrap);
  wrap.appendChild(el('p', { class: 'muted', text: '加载中…' }));

  // 资料 + 积分账户并行加载
  let me: UserInfoVO;
  let account: PointsAccountDTO;
  try {
    const [m, a] = await Promise.all([
      apiGet<UserInfoVO>('/users/me'),
      apiGet<PointsAccountDTO>(`/points/accounts/${uid}`),
    ]);
    me = m;
    account = a;
  } catch (e) {
    wrap.replaceChildren(el('p', { class: 'msg', text: (e as Error).message }));
    return;
  }

  // 顶部：资料卡 + 积分卡
  wrap.replaceChildren();
  const top = el('div', { class: 'profile-top' });

  const profileCard = el('div', { class: 'card profile-card' });
  profileCard.appendChild(el('div', { class: 'avatar', text: (me.nickname ?? '墨')[0] }));
  profileCard.appendChild(el('div', { class: 'profile-name', text: me.nickname ?? '墨阅用户' }));
  profileCard.appendChild(el('div', { class: 'muted', text: `角色：${roleLabel(me.role)}` }));
  profileCard.appendChild(el('div', { class: 'muted', text: `手机号：${maskPhone(me.phone)}` }));
  profileCard.appendChild(el('div', { class: 'muted', text: `状态：${me.status === 1 ? '正常' : '已禁用'}` }));
  top.appendChild(profileCard);

  const pointsCard = el('div', { class: 'card points-card' });
  const balanceEl = el('div', { class: 'points-balance', text: String(account.balance ?? 0) });
  pointsCard.appendChild(el('div', { class: 'muted', text: '当前积分' }));
  pointsCard.appendChild(balanceEl);
  const sub = el('div', { class: 'points-sub muted' });
  sub.textContent = `累计获得 ${account.totalEarned ?? 0} · 累计消费 ${account.totalSpent ?? 0}`;
  pointsCard.appendChild(sub);

  const checkinBtn = el('button', { class: 'btn primary', text: '每日签到 +10' });
  const checkinHint = el('span', { class: 'muted', style: 'margin-left:12px;font-size:13px;' });
  checkinBtn.addEventListener('click', async () => {
    checkinBtn.disabled = true;
    checkinHint.textContent = '签到中…';
    try {
      const earned = await apiPost<number>('/points/check-in', { userId: uid });
      checkinHint.textContent = `已签到，获得 ${earned} 积分`;
      // 刷新账户与流水
      const a2 = await apiGet<PointsAccountDTO>(`/points/accounts/${uid}`);
      balanceEl.textContent = String(a2.balance ?? 0);
      sub.textContent = `累计获得 ${a2.totalEarned ?? 0} · 累计消费 ${a2.totalSpent ?? 0}`;
      await loadFlows();
    } catch (e) {
      checkinHint.textContent = (e as Error).message;
    } finally {
      checkinBtn.disabled = false;
    }
  });
  const checkinRow = el('div', { class: 'points-checkin' }, [checkinBtn, checkinHint]);
  pointsCard.appendChild(checkinRow);
  top.appendChild(pointsCard);

  wrap.appendChild(top);

  // 积分流水
  const flowSection = el('div', { class: 'profile-section' });
  flowSection.appendChild(el('h3', { text: '积分流水' }));
  const flowList = el('div', { class: 'flow-list' });
  flowSection.appendChild(flowList);
  wrap.appendChild(flowSection);

  // 积分商城
  const mallSection = el('div', { class: 'profile-section' });
  mallSection.appendChild(el('h3', { text: '积分商城' }));
  const mallGrid = el('div', { class: 'mall-grid' });
  mallSection.appendChild(mallGrid);
  wrap.appendChild(mallSection);

  async function loadFlows(): Promise<void> {
    flowList.replaceChildren();
    try {
      const page = await apiGet<{ records?: PointsFlowEntity[] }>(`/points/flows?userId=${uid}&page=1&size=20`);
      const records = page.records ?? [];
      if (records.length === 0) {
        flowList.appendChild(el('p', { class: 'muted', text: '暂无积分流水。' }));
        return;
      }
      records.forEach((f) => {
        const row = el('div', { class: 'flow-row' });
        row.appendChild(el('span', { class: 'flow-tag', text: flowLabel(f.bizType) }));
        row.appendChild(el('span', { class: 'flow-remark', text: f.remark ?? '' }));
        const delta = el('span', {
          class: f.points != null && f.points >= 0 ? 'flow-points plus' : 'flow-points minus',
          text: `${f.points != null && f.points >= 0 ? '+' : ''}${f.points ?? 0}`,
        });
        row.appendChild(delta);
        flowList.appendChild(row);
      });
    } catch (e) {
      flowList.replaceChildren(el('p', { class: 'msg', text: (e as Error).message }));
    }
  }

  async function loadMall(): Promise<void> {
    mallGrid.replaceChildren();
    try {
      const page = await apiGet<{ records?: PointsProductDTO[] }>('/points/products?page=1&size=50');
      const products = page.records ?? [];
      if (products.length === 0) {
        mallGrid.appendChild(el('p', { class: 'muted', text: '商城暂无上架商品。' }));
        return;
      }
      products.forEach((prod) => {
        const card = el('div', { class: 'mall-card' });
        card.appendChild(el('div', { class: 'mall-name', text: prod.name ?? '' }));
        if (prod.description) card.appendChild(el('div', { class: 'muted', text: prod.description }));
        card.appendChild(el('div', { class: 'mall-cost', text: `需 ${prod.costPoints ?? 0} 积分` }));
        const btn = el('button', { class: 'btn primary', text: '兑换' });
        btn.addEventListener('click', async () => {
          btn.disabled = true;
          try {
            const order = await apiPost<PointsOrderDTO>('/points/orders', { userId: uid, productId: prod.id });
            checkinHint.textContent = `已兑换《${order.productName}》`;
            const a2 = await apiGet<PointsAccountDTO>(`/points/accounts/${uid}`);
            balanceEl.textContent = String(a2.balance ?? 0);
            sub.textContent = `累计获得 ${a2.totalEarned ?? 0} · 累计消费 ${a2.totalSpent ?? 0}`;
            await loadFlows();
          } catch (e) {
            checkinHint.textContent = (e as Error).message;
          } finally {
            btn.disabled = false;
          }
        });
        card.appendChild(btn);
        mallGrid.appendChild(card);
      });
    } catch (e) {
      mallGrid.replaceChildren(el('p', { class: 'msg', text: (e as Error).message }));
    }
  }

  await Promise.all([loadFlows(), loadMall()]);
}
