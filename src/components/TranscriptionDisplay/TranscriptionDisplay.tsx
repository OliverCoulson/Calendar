import { useEffect, useRef } from 'react';
import type { RecognitionStatus } from '../../types';
import styles from './TranscriptionDisplay.module.css';

interface TranscriptionDisplayProps {
  finalText: string;
  interimText: string;
  status: RecognitionStatus;
}

export function TranscriptionDisplay({
  finalText,
  interimText,
  status,
}: TranscriptionDisplayProps) {
  const scrollRef = useRef<HTMLDivElement>(null);

  // 自动滚动到底部
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [finalText, interimText]);

  const isEmpty = !finalText && !interimText;
  const isListening = status === 'listening';
  const isIdle = status === 'idle';
  const isUnsupported = status === 'unsupported';

  return (
    <div className={styles.container}>
      <div className={styles.scrollArea} ref={scrollRef}>
        {isEmpty ? (
          <div className={styles.placeholder}>
            {isUnsupported ? (
              <span className={styles.mutedText}>
                您的浏览器不支持语音识别，请使用 Chrome 或 Edge 浏览器
              </span>
            ) : isListening ? (
              <span className={styles.listeningHint}>
                正在聆听
                <span className={styles.dots}>
                  <span>.</span>
                  <span>.</span>
                  <span>.</span>
                </span>
              </span>
            ) : (
              <span className={styles.mutedText}>点击麦克风按钮开始语音输入</span>
            )}
          </div>
        ) : (
          <div className={styles.textContent}>
            {finalText && <span className={styles.final}>{finalText}</span>}
            {interimText && (
              <span className={styles.interim}>
                {finalText ? ' ' : ''}
                {interimText}
                {isListening && <span className={styles.cursor} />}
              </span>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
