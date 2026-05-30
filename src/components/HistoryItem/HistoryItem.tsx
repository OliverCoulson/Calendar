import type { Recording } from '../../types';
import { useClipboard } from '../../hooks/useClipboard';
import { formatTimestamp, formatDuration, truncate } from '../../utils/formatters';
import { downloadFile } from '../../utils/export';
import styles from './HistoryItem.module.css';

interface HistoryItemProps {
  recording: Recording;
  onDelete: (id: string) => void;
}

export function HistoryItem({ recording, onDelete }: HistoryItemProps) {
  const { copy, copied } = useClipboard();

  const handleCopy = async () => {
    await copy(recording.text);
  };

  const handleExportSingle = () => {
    const text = `[${formatTimestamp(recording.timestamp)}] (${formatDuration(recording.duration)})\n${recording.text}`;
    downloadFile(
      text,
      `voice-record-${formatTimestamp(recording.timestamp).replace(/[:\s]/g, '-')}.txt`,
      'text/plain',
    );
  };

  return (
    <div className={styles.item}>
      <div className={styles.header}>
        <span className={styles.timestamp}>
          {formatTimestamp(recording.timestamp)}
        </span>
        <span className={styles.duration}>
          {formatDuration(recording.duration)}
        </span>
      </div>
      <p className={styles.text}>{truncate(recording.text, 120)}</p>
      <div className={styles.actions}>
        <button
          className={`${styles.actionBtn} ${copied ? styles.copied : ''}`}
          onClick={handleCopy}
          title={copied ? '已复制' : '复制文本'}
          type="button"
        >
          {copied ? (
            <>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                <path d="M5 13l4 4L19 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
              已复制
            </>
          ) : (
            <>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                <rect x="9" y="9" width="13" height="13" rx="2" stroke="currentColor" strokeWidth="2" fill="none" />
                <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1" stroke="currentColor" strokeWidth="2" fill="none" />
              </svg>
              复制
            </>
          )}
        </button>
        <button
          className={styles.actionBtn}
          onClick={handleExportSingle}
          title="导出单条"
          type="button"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
            <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" fill="none" />
            <polyline points="7 10 12 15 17 10" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" fill="none" />
            <line x1="12" y1="15" x2="12" y2="3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
          </svg>
          导出
        </button>
        <button
          className={`${styles.actionBtn} ${styles.deleteBtn}`}
          onClick={() => onDelete(recording.id)}
          title="删除"
          type="button"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
            <polyline points="3 6 5 6 21 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" fill="none" />
            <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" fill="none" />
          </svg>
          删除
        </button>
      </div>
    </div>
  );
}
