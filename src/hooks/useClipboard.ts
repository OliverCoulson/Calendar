import { useCallback, useState } from 'react';
import type { UseClipboardReturn } from '../types';

export function useClipboard(): UseClipboardReturn {
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const copy = useCallback(async (text: string): Promise<boolean> => {
    setError(null);
    setCopied(false);

    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(text);
      } else {
        // 降级方案：execCommand
        const textarea = document.createElement('textarea');
        textarea.value = text;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        textarea.style.pointerEvents = 'none';
        document.body.appendChild(textarea);
        textarea.select();
        const success = document.execCommand('copy');
        document.body.removeChild(textarea);
        if (!success) throw new Error('execCommand failed');
      }

      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
      return true;
    } catch (err: any) {
      setError('复制失败，请尝试手动复制');
      return false;
    }
  }, []);

  return { copy, copied, error };
}
