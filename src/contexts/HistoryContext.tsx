import { createContext, useContext } from 'react';
import type { UseHistoryReturn } from '../types';

export const HistoryContext = createContext<UseHistoryReturn | null>(null);

export function useSharedHistory(): UseHistoryReturn {
  const ctx = useContext(HistoryContext);
  if (!ctx) {
    throw new Error('useSharedHistory 必须在 HistoryProvider 内部使用');
  }
  return ctx;
}
