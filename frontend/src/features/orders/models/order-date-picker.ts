// 增量实施：[计划](../../../../../specs/003-select-all-filtered/plan.md)、[任务](../../../../../specs/003-select-all-filtered/tasks.md)；[测试](../__tests__/order-management.test.tsx)。
// 设计与验证：[计划](../../../../../specs/003-select-all-filtered/plan.md)，TC-310/311。
import { DatePicker } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import customParseFormat from 'dayjs/plugin/customParseFormat';
import utc from 'dayjs/plugin/utc';
import localeData from 'dayjs/plugin/localeData';
import weekOfYear from 'dayjs/plugin/weekOfYear';
import 'dayjs/locale/zh-cn';

dayjs.extend(customParseFormat);
dayjs.extend(utc);
dayjs.extend(localeData);
dayjs.extend(weekOfYear);
const FORMAT = 'YYYY-MM-DD HH:mm:ss';

/** UTC仅承载香港墙上时间组成，实际接口转换仍由toUtcInstant负责。 */
export function parsePickerLocal(value?: string): Dayjs | null {
  if (!value) return null;
  const parsed = dayjs.utc(value, FORMAT, true);
  return parsed.isValid() ? parsed : null;
}
/** 保留用户所见年月日时分秒，不能直接将载体序列化为接口时间戳。 */
export function formatPickerLocal(value: Dayjs | null): string | undefined {
  return value?.isValid() ? value.format(FORMAT) : undefined;
}

/** 使用AntD公开日期适配接口，所有解析和日历运算都隔离本机DST。 */
const wallClockConfig: Parameters<typeof DatePicker.generatePicker<Dayjs>>[0] = {
  getNow: () => dayjs.utc(Date.now() + 8 * 3600000),
  getFixedDate: value => dayjs.utc(value, 'YYYY-MM-DD', true),
  getEndDate: value => value.endOf('month'),
  getWeekDay: value => value.day(),
  getYear: value => value.year(), getMonth: value => value.month(), getDate: value => value.date(),
  getHour: value => value.hour(), getMinute: value => value.minute(), getSecond: value => value.second(),
  getMillisecond: value => value.millisecond(),
  addYear: (value, diff) => value.add(diff, 'year'), addMonth: (value, diff) => value.add(diff, 'month'),
  addDate: (value, diff) => value.add(diff, 'day'),
  setYear: (value, year) => value.year(year), setMonth: (value, month) => value.month(month),
  setDate: (value, date) => value.date(date), setHour: (value, hour) => value.hour(hour),
  setMinute: (value, minute) => value.minute(minute), setSecond: (value, second) => value.second(second),
  setMillisecond: (value, millisecond) => value.millisecond(millisecond),
  isAfter: (a, b) => a.isAfter(b), isValidate: value => value.isValid(),
  locale: {
    getWeekFirstDay: locale => dayjs.utc().locale(locale.replace('_', '-').toLowerCase()).localeData().firstDayOfWeek(),
    getWeekFirstDate: (locale, value) => value.locale(locale.replace('_', '-').toLowerCase()).startOf('week'),
    getWeek: (locale, value) => value.locale(locale.replace('_', '-').toLowerCase()).week(),
    getShortWeekDays: locale => dayjs.utc().locale(locale.replace('_', '-').toLowerCase()).localeData().weekdaysMin(),
    getShortMonths: locale => dayjs.utc().locale(locale.replace('_', '-').toLowerCase()).localeData().monthsShort(),
    format: (locale, value, format) => value.locale(locale.replace('_', '-').toLowerCase()).format(format),
    parse: (locale, text, formats) => {
      for (const format of formats) {
        const parsed = dayjs.utc(text, format, true).locale(locale.replace('_', '-').toLowerCase());
        if (parsed.isValid()) return parsed;
      }
      return null;
    },
  },
};
export const HongKongDatePicker: ReturnType<typeof DatePicker.generatePicker<Dayjs>> = DatePicker.generatePicker<Dayjs>(wallClockConfig);
