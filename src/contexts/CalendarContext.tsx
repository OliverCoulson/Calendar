import { createContext, useContext } from 'react';
import type { UseCalendarReturn } from '../hooks/useCalendar';

export const CalendarContext = createContext<UseCalendarReturn | null>(null);

export function useSharedCalendar(): UseCalendarReturn {
  const ctx = useContext(CalendarContext);
  if (!ctx) {
    throw new Error('useSharedCalendar 必须在 CalendarProvider 内部使用');
  }
  return ctx;
}
