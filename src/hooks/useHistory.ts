import { useCallback, useEffect, useMemo, useState } from 'react';
import type { Recording, UseHistoryOptions, UseHistoryReturn } from '../types';
import { STORAGE_KEY, MAX_HISTORY_ITEMS } from '../constants';
import { loadFromStorage, saveToStorage } from '../utils/storage';
import { downloadFile } from '../utils/export';
import { formatDate, formatTimestamp, formatDuration } from '../utils/formatters';

export function useHistory(options?: UseHistoryOptions): UseHistoryReturn {
  const storageKey = options?.storageKey ?? STORAGE_KEY;
  const maxItems = options?.maxItems ?? MAX_HISTORY_ITEMS;

  const [recordings, setRecordings] = useState<Recording[]>(() =>
    loadFromStorage(storageKey),
  );
  const [searchQuery, setSearchQuery] = useState('');
  const [sortOrder, setSortOrder] = useState<'newest' | 'oldest'>('newest');

  // 持久化到 localStorage
  useEffect(() => {
    saveToStorage(storageKey, recordings);
  }, [recordings, storageKey]);

  const addRecording = useCallback(
    (rec: Omit<Recording, 'id'>): Recording => {
      const newRec: Recording = {
        ...rec,
        id:
          crypto.randomUUID?.() ??
          `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
      };
      setRecordings((prev) => {
        const next = [newRec, ...prev];
        return next.slice(0, maxItems);
      });
      return newRec;
    },
    [maxItems],
  );

  const deleteRecording = useCallback((id: string) => {
    setRecordings((prev) => prev.filter((r) => r.id !== id));
  }, []);

  const clearAll = useCallback(() => {
    setRecordings([]);
  }, []);

  const filteredRecordings = useMemo(() => {
    let result = recordings;

    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      result = result.filter((r) => r.text.toLowerCase().includes(q));
    }

    result = [...result].sort((a, b) => {
      return sortOrder === 'newest'
        ? b.timestamp - a.timestamp
        : a.timestamp - b.timestamp;
    });

    return result;
  }, [recordings, searchQuery, sortOrder]);

  const exportAsJSON = useCallback(() => {
    downloadFile(
      JSON.stringify(recordings, null, 2),
      `voice-history-${formatDate(new Date())}.json`,
      'application/json',
    );
  }, [recordings]);

  const exportAsTXT = useCallback(() => {
    const text = recordings
      .map(
        (r) =>
          `[${formatTimestamp(r.timestamp)}] (${formatDuration(r.duration)})\n${r.text}\n`,
      )
      .join('\n---\n\n');
    downloadFile(
      text,
      `voice-history-${formatDate(new Date())}.txt`,
      'text/plain',
    );
  }, [recordings]);

  const search = useCallback(
    (query: string): Recording[] => {
      return recordings.filter((r) => r.text.includes(query));
    },
    [recordings],
  );

  return {
    recordings,
    filteredRecordings,
    searchQuery,
    sortOrder,
    setSearchQuery,
    setSortOrder,
    addRecording,
    deleteRecording,
    clearAll,
    exportAsJSON,
    exportAsTXT,
    search,
  };
}
