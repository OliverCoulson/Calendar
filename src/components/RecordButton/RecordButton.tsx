import type { RecognitionStatus } from '../../types';
import styles from './RecordButton.module.css';

interface RecordButtonProps {
  status: RecognitionStatus;
  disabled?: boolean;
  onStart: () => void;
  onStop: () => void;
}

export function RecordButton({
  status,
  disabled = false,
  onStart,
  onStop,
}: RecordButtonProps) {
  const isListening = status === 'listening';
  const isProcessing = status === 'processing';
  const isUnsupported = status === 'unsupported';
  const isIdle = status === 'idle';
  const isError = status === 'error';

  const handleClick = () => {
    if (disabled || isProcessing || isUnsupported) return;
    if (isListening) {
      onStop();
    } else {
      onStart();
    }
  };

  const getAriaLabel = () => {
    switch (status) {
      case 'idle':
        return '开始录音';
      case 'listening':
        return '停止录音';
      case 'processing':
        return '正在处理';
      case 'error':
        return '重试录音';
      case 'unsupported':
        return '浏览器不支持语音识别';
    }
  };

  return (
    <button
      className={`${styles.button} ${isListening ? styles.listening : ''} ${isProcessing ? styles.processing : ''} ${isUnsupported ? styles.unsupported : ''} ${isError ? styles.error : ''} ${isIdle ? styles.idle : ''}`}
      onClick={handleClick}
      disabled={disabled || isProcessing || isUnsupported}
      aria-label={getAriaLabel()}
      type="button"
    >
      <div className={styles.ring} />
      <div className={styles.inner}>
        {isProcessing ? (
          <svg className={styles.spinner} viewBox="0 0 24 24" fill="none">
            <circle
              cx="12"
              cy="12"
              r="10"
              stroke="currentColor"
              strokeWidth="2"
              strokeDasharray="31.4 31.4"
              strokeLinecap="round"
            />
          </svg>
        ) : (
          <svg className={styles.icon} viewBox="0 0 24 24" fill="none">
            {isListening ? (
              // 停止图标（方形）
              <rect x="6" y="6" width="12" height="12" rx="2" fill="currentColor" />
            ) : (
              // 麦克风图标
              <>
                <rect x="9" y="1" width="6" height="11" rx="3" fill="currentColor" />
                <path
                  d="M5 11a7 7 0 0 0 14 0"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  fill="none"
                />
              </>
            )}
          </svg>
        )}
      </div>
    </button>
  );
}
