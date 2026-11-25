import './index.css';
import './styles/operator.css';
import './styles/figma.css';
import './styles/operator-figma.css';
import { mountOperatorApi } from './features/operator-api.js';
import { mountMobileApi } from './features/mobile-api.js';
import { mobileApi } from './shared/api/mobile.js';
const root = document.querySelector('#root');
let dispose;
function render() {
  dispose?.();
  const path = location.hash.slice(1) || '/';
  dispose =
    path.startsWith('/operator') ||
    (path === '/settings' && mobileApi.user?.role === 'OPERATOR')
      ? mountOperatorApi(root, path)
      : mountMobileApi(root, path);
}
window.addEventListener('hashchange', render);
render();
