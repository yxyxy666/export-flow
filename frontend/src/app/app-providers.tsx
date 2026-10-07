// 设计与验证：specs/001-project-bootstrap/plan.md、tasks.md；本期仅静态骨架。
import type { ReactElement, ReactNode } from 'react';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';

export interface AppProvidersProps { children: ReactNode }

/** 统一简体中文和基础主题，不创建数据缓存或后台连接。 */
export function AppProviders({ children }: AppProvidersProps): ReactElement {
  return (
    <ConfigProvider locale={zhCN} theme={{ token: { colorPrimary: '#1677ff', borderRadius: 8 } }}>
      {children}
    </ConfigProvider>
  );
}
