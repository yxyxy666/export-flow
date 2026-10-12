// 增量实施：[计划](../../../../../specs/003-select-all-filtered/plan.md)、[任务](../../../../../specs/003-select-all-filtered/tasks.md)；[测试](../__tests__/order-management.test.tsx)。
// 设计：[函数清单](../../../../../specs/002-order-management/function-design-reference.md)。测试：TC-202/205/206。
import { useEffect } from 'react';
import { Button, Input, Select } from 'antd';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import type { OrderFilterFormValues } from '../types/order.types';
import { normalizeOrderFilter } from '../models/order-query';
import { CHANNEL_LABELS, STATUS_LABELS } from '../models/order-view-model';
import { HongKongDatePicker, parsePickerLocal, formatPickerLocal } from '../models/order-date-picker';

const formSchema = z.object({
  keyword: z.string().max(100).optional(), orderNo: z.string().max(64).optional(),
  status: z.enum(['PENDING','PAID','SHIPPED','COMPLETED','CANCELED']).optional(),
  channel: z.enum(['WEB','APP','STORE','PARTNER']).optional(),
  createdFromLocal: z.string().optional(), createdToLocal: z.string().optional(),
  minAmount: z.string().optional(), maxAmount: z.string().optional(),
}).superRefine((value, context) => {
  try { normalizeOrderFilter(value); }
  catch (error) {
    const message = error instanceof z.ZodError ? error.issues[0].message : error instanceof Error ? error.message : '筛选条件无效';
    const isAmount = /金额/.test(message);
    context.addIssue({ code: 'custom', message,
      path: [isAmount ? 'minAmount' : /时间|日期/.test(message) ? 'createdFromLocal' : 'orderNo'] });
  }
});
export interface OrderFilterFormProps {
  values: OrderFilterFormValues; isDisabled: boolean; onQuery(values: OrderFilterFormValues): void; onReset(): void;
}
/** 输入草稿只由RHF管理；合法提交才交给已提交查询状态。 */
export function OrderFilterForm({ values, isDisabled, onQuery, onReset }: OrderFilterFormProps) {
  const { control, reset, handleSubmit, formState: { errors } } = useForm<OrderFilterFormValues>({
    defaultValues: { createdFromLocal: '', createdToLocal: '', ...values }, resolver: zodResolver(formSchema),
  });
  useEffect(() => reset({ createdFromLocal: '', createdToLocal: '', ...values }), [values, reset]);
  const inputs = [ ['orderNo','订单号'], ['keyword','关键字'], ['minAmount','最小金额'], ['maxAmount','最大金额'] ] as const;
  return <form onSubmit={handleSubmit(onQuery)} className="order-filter-form">
    {inputs.map(([name, label]) => <div key={name} className="filter-field">
      <label htmlFor={name}>{label}</label>
      <Controller name={name} control={control} render={({ field }) => <Input {...field} value={field.value ?? ''}
        id={name} disabled={isDisabled} aria-invalid={!!errors[name]}
        aria-describedby={errors[name] ? `${name}-error` : undefined} />} />
      {errors[name] && <span id={`${name}-error`} role="alert">{errors[name]?.message}</span>}
    </div>)}
    {([['createdFromLocal', '下单开始时间'], ['createdToLocal', '下单结束时间']] as const).map(([name, label]) =>
      <div key={name} className="filter-field"><label htmlFor={name}>{label}</label>
        <Controller name={name} control={control} render={({ field }) => <HongKongDatePicker
          ref={field.ref} id={name} aria-label={label} value={parsePickerLocal(field.value)}
          onChange={value => field.onChange(formatPickerLocal(value) ?? '')}
          onBlur={event => {
            // 无效文本也进入表单校验，不能以旧合法值悄悄查询。
            if (event.target instanceof HTMLInputElement) field.onChange(event.target.value);
            field.onBlur();
          }}
          showTime showNow={false} allowClear preserveInvalidOnBlur format="YYYY-MM-DD HH:mm:ss"
          placeholder="yyyy-MM-dd HH:mm:ss（香港）" disabled={isDisabled}
          status={errors[name] ? 'error' : undefined} aria-invalid={!!errors[name]}
          aria-describedby={errors[name] ? `${name}-error` : undefined} />}/>
        {errors[name] && <span id={`${name}-error`} role="alert">{errors[name]?.message}</span>}
      </div>)}
    {(['status','channel'] as const).map(name => <div key={name} className="filter-field">
      <label htmlFor={name}>{name === 'status' ? '状态' : '渠道'}</label>
      <Controller name={name} control={control} render={({ field }) => <Select {...field} id={name}
        aria-label={name === 'status' ? '状态' : '渠道'} disabled={isDisabled} allowClear virtual={false}
        onChange={value => field.onChange(value)} options={Object.entries(name === 'status' ? STATUS_LABELS : CHANNEL_LABELS)
          .map(([value, label]) => ({ value, label }))} />} />
    </div>)}
    <div className="filter-actions"><Button type="primary" htmlType="submit" disabled={isDisabled}>查询</Button>
      <Button onClick={onReset} disabled={isDisabled}>重置</Button></div>
  </form>;
}
