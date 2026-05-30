import type { RecognitionError } from '../../types';
import { ERROR_MESSAGES } from '../../constants';
import styles from './ErrorDisplay.module.css';

interface ErrorDisplayProps {
  error: RecognitionError;
  onDismiss?: () => void;
  onRetry?: () => void;
}

export function ErrorDisplay({ error, onDismiss, onRetry }: ErrorDisplayProps) {
  const message = ERROR_MESSAGES[error.type] || error.message || '未知错误';

  if (!message) return null;

  return (
    <div className={styles.banner}>
      <svg className={styles.icon} viewBox="0 0 20 20" fill="currentColor">
        <path
          fillRule="evenodd"
          d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z"
          clipRule="evenodd"
        />
      </svg>
      <span className={styles.message}>{message}</span>
      <div className={styles.actions}>
        {onRetry && (
          <button className={styles.retryBtn} onClick={onRetry} type="button">
            重试
          </button>
        )}
        {onDismiss && (
          <button className={styles.dismissBtn} onClick={onDismiss} type="button">
            <svg viewBox="0 0 20 20" fill="currentColor" width="16" height="16">
              <path
                fillRule="evenodd"
                d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                clipRule="evenodd"
              />
            </svg>
          </button>
        )}
      </div>
    </div>
  );
}
