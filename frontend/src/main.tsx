// 设计与验证：specs/001-project-bootstrap/plan.md、tasks.md；本期仅静态骨架。
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { App } from './app/App';
import './shared/styles/global.css';

/** 将静态应用挂载到指定根节点，入口不承担业务职责。 */
export function mountApplication(container: HTMLElement): Root {
  const root = createRoot(container);
  root.render(<StrictMode><App /></StrictMode>);
  return root;
}

const container = document.getElementById('root');
if (!container) throw new Error('应用启动失败：缺少 root 根节点。');
mountApplication(container);
