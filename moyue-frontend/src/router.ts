// 墨阅小说网前端 · 极简 hash 路由（含 requireAuth 守卫）
import { clear } from './dom';
import { getSession } from './auth';

export type RouteHandler = (params: Record<string, string>, query: URLSearchParams) => void;

interface RouteMeta {
  /** 需要登录态，未登录重定向到 #/login */
  auth?: boolean;
}

interface Route {
  pattern: RegExp;
  keys: string[];
  handler: RouteHandler;
  meta: RouteMeta;
}

export class Router {
  private routes: Route[] = [];

  constructor(private outlet: HTMLElement) {}

  add(path: string, handler: RouteHandler, meta: RouteMeta = {}): this {
    const keys: string[] = [];
    const pattern = new RegExp(
      '^' +
        path.replace(/:[^/]+/g, (m) => {
          keys.push(m.slice(1));
          return '([^/]+)';
        }) +
        '$'
    );
    this.routes.push({ pattern, keys, handler, meta });
    return this;
  }

  start(): void {
    window.addEventListener('hashchange', () => this.resolve());
    this.resolve();
  }

  resolve(): void {
    const hash = location.hash.replace(/^#/, '') || '/';
    const [path, qs] = hash.split('?');
    const query = new URLSearchParams(qs ?? '');
    for (const r of this.routes) {
      const m = r.pattern.exec(path);
      if (m) {
        // P0-#9：受保护路由未登录 → 重定向登录页（避免未授权进入个人中心/作者工作台）
        if (r.meta.auth && !getSession()) {
          if (location.hash !== '#/login') location.hash = '#/login';
          return;
        }
        const params: Record<string, string> = {};
        r.keys.forEach((k, i) => {
          params[k] = decodeURIComponent(m[i + 1]);
        });
        clear(this.outlet);
        r.handler(params, query);
        return;
      }
    }
    clear(this.outlet);
    this.outlet.textContent = '页面不存在';
  }

  navigate(path: string): void {
    location.hash = path;
  }
}
