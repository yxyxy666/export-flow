// 设计与进度：[任务](../../../../../specs/003-select-all-filtered/tasks.md#测试进度与证据)。
// [AC-080](../../../../../docs/product/acceptance-criteria.md#ac-080)
// [AC-005](../../../../../docs/product/acceptance-criteria.md#ac-005)
import { expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppProviders } from '../../../app/app-providers';
import { OrderFilterForm } from '../components/order-filter-form';

it('TC-310 无效日期草稿阻止查询，不能提交旧合法值或空条件', async () => {
  const query = vi.fn();
  render(<AppProviders><OrderFilterForm values={{ createdFromLocal: '2026-02-20 08:00:00' }}
    isDisabled={false} onQuery={query} onReset={vi.fn()} /></AppProviders>);
  const user = userEvent.setup(); const input = screen.getByLabelText('下单开始时间');
  await user.click(input); await user.clear(input); await user.paste('2026-02-30 08:00:00');
  await user.click(screen.getByRole('button', { name: '查询' }));
  expect(await screen.findByRole('alert')).toHaveTextContent(/时间|日期/);
  expect(input).toHaveAttribute('aria-invalid', 'true'); expect(query).not.toHaveBeenCalled();
});
