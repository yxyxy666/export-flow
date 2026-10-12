// 设计：[应用装配](../../../specs/002-order-management/plan.md)。测试：TC-201/211。
import { useState } from 'react';
import type { ReactElement, ReactNode } from 'react';
import { App as AntApp, ConfigProvider } from 'antd';
import { QueryClientProvider } from '@tanstack/react-query';
import { createQueryClient } from './query-client';
import zhCN from 'antd/locale/zh_CN';

export interface AppProvidersProps { children: ReactNode }

/** 每次应用挂载建立独立查询缓存，并统一语言、按钮名称和消息上下文。 */
export function AppProviders({ children }: AppProvidersProps): ReactElement {
  const [queryClient] = useState(createQueryClient);
  return (
    <ConfigProvider locale={zhCN} button={{ autoInsertSpace: false }} theme={{ token: { colorPrimary: '#1677ff', borderRadius: 8 } }}>
      <QueryClientProvider client={queryClient}><AntApp>{children}</AntApp></QueryClientProvider>
    </ConfigProvider>
  );
}
