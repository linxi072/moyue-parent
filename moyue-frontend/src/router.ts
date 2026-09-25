// 墨阅小说网前端 · 极简 hash 路由
import { clear } from './dom';

export type RouteHandler = (params: Record<string, string>, query: URLSearchParams) => void;

interface Route {
  pattern: RegExp;
  keys: string[];
  handler: RouteHandler;
}

export class Router {
  private routes: Route[] = [];

  constructor(private outlet: HTMLElement) {}

  add(path: string, handler: RouteHandler): this {
    const keys: string[] = [];
    const pattern = new RegExp(
      '^' +
        path.replace(/:[^/]+/g, (m) => {
          keys.push(m.slice(1));
          return '([^/]+)';
        }) +
        '$'
    );
    this.routes.push({ pattern, keys, handler });
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
