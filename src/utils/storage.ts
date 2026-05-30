import type { Recording } from '../types';

function isValidRecording(item: unknown): item is Recording {
  if (!item || typeof item !== 'object') return false;
  const r = item as Record<string, unknown>;
  return (
    typeof r.id === 'string' &&
    typeof r.text === 'string' &&
    typeof r.timestamp === 'number' &&
    typeof r.duration === 'number'
  );
}

export function loadFromStorage(key: string): Recording[] {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) throw new Error('Invalid format');
    return parsed.filter(isValidRecording);
  } catch {
    console.warn(`[VoiceInput] localStorage key="${key}" 数据损坏，已重置。`);
    try {
      localStorage.removeItem(key);
    } catch {
      /* ignore */
    }
    return [];
  }
}

export function saveToStorage(key: string, recordings: Recording[]): void {
  try {
    localStorage.setItem(key, JSON.stringify(recordings));
  } catch (err) {
    if (
      err instanceof DOMException &&
      (err.name === 'QuotaExceededError' || err.code === 22)
    ) {
      const trimmed = recordings.slice(0, Math.floor(recordings.length * 0.75));
      try {
        localStorage.setItem(key, JSON.stringify(trimmed));
        console.warn('[VoiceInput] localStorage 配额不足，已裁剪旧记录。');
      } catch {
        console.error('[VoiceInput] localStorage 配额严重不足，历史未保存。');
      }
    }
  }
}
