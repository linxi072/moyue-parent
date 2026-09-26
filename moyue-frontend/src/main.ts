// 墨阅小说网前端 · 应用入口
import './style.css';
import { Router } from './router';
import { renderShell } from './layout';
import { renderLogin } from './pages/login';
import { renderBookstore } from './pages/bookstore';
import { renderBookDetail } from './pages/bookDetail';
import { renderReader } from './pages/reader';
import { renderAiChat } from './pages/aiChat';
import { renderSearch } from './pages/search';
import { renderShelf } from './pages/shelf';
import { renderProfile } from './pages/profile';
import { renderAuthor } from './pages/author';
import { renderAuthorIncome } from './pages/authorIncome';

const app = document.getElementById('app');
if (!app) throw new Error('找不到 #app 挂载点');

const { outlet } = renderShell(app);

const router = new Router(outlet);
router
  .add('/', () => void renderBookstore(outlet))
  .add('/search', (_p, q) => void renderSearch(outlet, q.get('q') ?? ''))
  .add('/shelf', () => void renderShelf(outlet))
  .add('/book/:bookId', (p) => void renderBookDetail(outlet, Number(p.bookId)))
  .add('/read/:chapterId', (p) => void renderReader(outlet, Number(p.chapterId)))
  .add('/ai', () => void renderAiChat(outlet))
  .add('/profile', () => void renderProfile(outlet))
  .add('/author', () => void renderAuthor(outlet))
  .add('/author/income', () => void renderAuthorIncome(outlet))
  .add('/login', () => renderLogin(outlet));

router.start();
