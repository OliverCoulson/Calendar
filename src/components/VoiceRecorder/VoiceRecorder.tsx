import { useState, useCallback, useRef, useEffect } from 'react';
import { useSpeechRecognition } from '../../hooks/useSpeechRecognition';
import { useAudioVisualizer } from '../../hooks/useAudioVisualizer';
import { useSharedHistory } from '../../contexts/HistoryContext';
import { useClipboard } from '../../hooks/useClipboard';
import { useSharedCalendar } from '../../contexts/CalendarContext';
import { RecordButton } from '../RecordButton';
import { AudioVisualizer } from '../AudioVisualizer';
import { ErrorDisplay } from '../ErrorDisplay';
import styles from './VoiceRecorder.module.css';

export function VoiceRecorder() {
  const {
    status,
    transcript,
    interimTranscript,
    error,
    isSupported,
    start: startRecognition,
    stop: stopRecognition,
    reset,
  } = useSpeechRecognition({ lang: 'zh-CN', continuous: true });

  const {
    frequencyData,
    isActive: isVisualizerActive,
    error: visualizerError,
    start: startVisualizer,
    stop: stopVisualizer,
  } = useAudioVisualizer({ fftSize: 128 });

  const { addRecording } = useSharedHistory();
  const { copy, copied } = useClipboard();
  const { processVoice, fetchEvents } = useSharedCalendar();

  // 用户可编辑的文本
  const [editedText, setEditedText] = useState('');
  const [showSavePrompt, setShowSavePrompt] = useState(false);
  const [nlpFeedback, setNlpFeedback] = useState<string | null>(null);
  const startTimeRef = useRef<number>(0);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const isFocusedRef = useRef(false);

  // 将 transcript 实时合并进 editedText
  // 只有当 textarea 没有焦点时才自动更新（避免打断用户编辑）
  useEffect(() => {
    if (isFocusedRef.current) return;

    const text = transcript + (interimTranscript ? (transcript ? ' ' : '') + interimTranscript : '');

    // 只在有内容或之前已有内容时才更新
    if (text || editedText) {
      setEditedText(text);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [transcript, interimTranscript]);

  // 识别从 processing → idle 时弹出保存提示
  const prevStatusRef = useRef(status);
  useEffect(() => {
    if (prevStatusRef.current === 'processing' && status === 'idle') {
      if (editedText.trim()) {
        setShowSavePrompt(true);
      }
    }
    prevStatusRef.current = status;
  }, [status, editedText]);

  const handleStart = useCallback(async () => {
    setShowSavePrompt(false);
    setEditedText('');
    startTimeRef.current = Date.now();
    startRecognition();
    startVisualizer().catch(() => {
      /* 静默处理 */
    });
  }, [startRecognition, startVisualizer]);

  const handleStop = useCallback(() => {
    stopRecognition();
    stopVisualizer();
  }, [stopRecognition, stopVisualizer]);

  const handleSave = useCallback(async () => {
    const textToSave = editedText.trim();
    if (!textToSave) return;

    const duration = Math.round((Date.now() - startTimeRef.current) / 1000);
    const recording = {
      text: textToSave,
      timestamp: Date.now(),
      duration: Math.max(1, duration),
      language: 'zh-CN',
    };

    // 保存到本地历史
    addRecording(recording);

    // 发送到后端 NLP 处理（日历操作），处理完自动刷新日历
    const result = await processVoice(textToSave);
    if (result) {
      setNlpFeedback(result.message);
      setTimeout(() => setNlpFeedback(null), 5000);
    }
    // 无论后端是否成功，都刷新日历确保数据同步
    fetchEvents();

    setShowSavePrompt(false);
    setEditedText('');
  }, [editedText, addRecording, processVoice]);

  const handleDiscard = useCallback(() => {
    setShowSavePrompt(false);
    setEditedText('');
  }, []);

  const handleClear = useCallback(() => {
    reset();
    setEditedText('');
    setShowSavePrompt(false);
  }, [reset]);

  const handleCopy = useCallback(async () => {
    await copy(editedText);
  }, [copy, editedText]);

  const handleTextChange = useCallback(
    (e: React.ChangeEvent<HTMLTextAreaElement>) => {
      setEditedText(e.target.value);
    },
    [],
  );

  const handleFocus = useCallback(() => {
    isFocusedRef.current = true;
  }, []);

  const handleBlur = useCallback(() => {
    isFocusedRef.current = false;
  }, []);

  const handleDismissError = useCallback(() => {
    reset();
  }, [reset]);

  const isListening = status === 'listening';
  const hasContent = !!editedText;
  const showVisualizer = isVisualizerActive && frequencyData.length > 0;
  const showPlaceholder = !hasContent && (status === 'idle' || status === 'unsupported');

  const displayError = visualizerError
    ? { type: 'audio-capture' as const, message: visualizerError }
    : error;

  return (
    <section className={styles.recorder}>
      <div className={styles.topArea}>
        <RecordButton
          status={status}
          disabled={!isSupported}
          onStart={handleStart}
          onStop={handleStop}
        />

        <div className={styles.centerArea}>
          {showVisualizer && (
            <AudioVisualizer
              frequencyData={frequencyData}
              isActive={isVisualizerActive}
            />
          )}

          {/* 可编辑文本域 — 始终显示，用户可以随时编辑 */}
          <div className={`${styles.editContainer} ${isListening ? styles.listeningEdit : ''}`}>
            <textarea
              ref={textareaRef}
              className={styles.editTextarea}
              value={editedText}
              onChange={handleTextChange}
              onFocus={handleFocus}
              onBlur={handleBlur}
              placeholder={
                isListening
                  ? '正在聆听...语音识别中'
                  : '点击麦克风按钮开始语音输入，您也可以直接在此输入文字'
              }
              rows={4}
            />
            {isListening && (
              <div className={styles.listeningBadge}>
                <span className={styles.dot} />
                实时识别中
              </div>
            )}
          </div>

          {/* 占位提示 — 仅在完全没有内容且非聆听时显示 */}
          {showPlaceholder && status === 'unsupported' && (
            <div className={styles.placeholderHint}>
              <span className={styles.placeholderText}>
                您的浏览器不支持语音识别，请使用 Chrome 或 Edge 浏览器
              </span>
            </div>
          )}

          {/* 工具栏：清空 + 复制 — 有内容即显示，包括聆听中 */}
          {hasContent && (
            <div className={styles.toolbar}>
              <button
                className={styles.toolBtn}
                onClick={handleClear}
                title="清空内容"
                type="button"
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none">
                  <polyline
                    points="3 6 5 6 21 6"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    fill="none"
                  />
                  <path
                    d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    fill="none"
                  />
                </svg>
                清空内容
              </button>
              <button
                className={`${styles.toolBtn} ${copied ? styles.copiedToolBtn : ''}`}
                onClick={handleCopy}
                title="复制文本"
                type="button"
              >
                {copied ? (
                  <>
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none">
                      <path
                        d="M5 13l4 4L19 7"
                        stroke="currentColor"
                        strokeWidth="2"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                    </svg>
                    已复制
                  </>
                ) : (
                  <>
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none">
                      <rect
                        x="9"
                        y="9"
                        width="13"
                        height="13"
                        rx="2"
                        stroke="currentColor"
                        strokeWidth="2"
                        fill="none"
                      />
                      <path
                        d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"
                        stroke="currentColor"
                        strokeWidth="2"
                        fill="none"
                      />
                    </svg>
                    复制
                  </>
                )}
              </button>
            </div>
          )}
        </div>
      </div>

      {/* 错误显示 */}
      {displayError && (
        <ErrorDisplay
          error={displayError}
          onDismiss={handleDismissError}
          onRetry={status === 'error' ? handleStart : undefined}
        />
      )}

      {/* 保存 / 丢弃操作栏 */}
      {showSavePrompt && editedText.trim() && (
        <div className={styles.saveBar}>
          <span className={styles.saveHint}>
            录音完成，您可以编辑修正后再保存
          </span>
          <div className={styles.saveActions}>
            <button
              className={styles.discardBtn}
              onClick={handleDiscard}
              type="button"
            >
              丢弃
            </button>
            <button className={styles.saveBtn} onClick={handleSave} type="button">
              保存到日历
            </button>
          </div>
        </div>
      )}

      {/* NLP 处理反馈 */}
      {nlpFeedback && (
        <div className={styles.feedbackBar}>
          <svg className={styles.feedbackIcon} viewBox="0 0 20 20" fill="currentColor" width="16" height="16">
            <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
          </svg>
          <span>{nlpFeedback}</span>
        </div>
      )}
    </section>
  );
}
