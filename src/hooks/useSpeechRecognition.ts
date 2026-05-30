import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type {
  RecognitionError,
  RecognitionStatus,
  UseSpeechRecognitionOptions,
  UseSpeechRecognitionReturn,
} from '../types';
import { DEFAULT_LANG, SILENCE_TIMEOUT_MS } from '../constants';

// 浏览器兼容的类型声明
interface SpeechRecognitionEvent extends Event {
  resultIndex: number;
  results: SpeechRecognitionResultList;
}

interface SpeechRecognitionErrorEvent extends Event {
  error: string;
  message: string;
}

interface ISpeechRecognition extends EventTarget {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  maxAlternatives: number;
  onresult: ((event: SpeechRecognitionEvent) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEvent) => void) | null;
  onend: (() => void) | null;
  onstart: (() => void) | null;
  onspeechend: (() => void) | null;
  onsoundstart: (() => void) | null;
  onsoundend: (() => void) | null;
  start: () => void;
  stop: () => void;
  abort: () => void;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function getSpeechRecognitionConstructor(): (new () => ISpeechRecognition) | null {
  const win = window as any;
  const Ctor = win.SpeechRecognition || win.webkitSpeechRecognition;
  return Ctor as (new () => ISpeechRecognition) | null;
}

function mapErrorType(error: string): RecognitionError['type'] {
  switch (error) {
    case 'not-allowed':
    case 'permission-denied':
      return 'not-allowed';
    case 'no-speech':
      return 'no-speech';
    case 'network':
      return 'network';
    case 'audio-capture':
      return 'audio-capture';
    case 'service-not-allowed':
      return 'service-not-allowed';
    case 'aborted':
      return 'aborted';
    default:
      return 'audio-capture';
  }
}

export function useSpeechRecognition(
  options: UseSpeechRecognitionOptions = {},
): UseSpeechRecognitionReturn {
  const {
    lang = DEFAULT_LANG,
    continuous = true,
    interimResults = true,
    maxAlternatives = 1,
    silenceTimeout = SILENCE_TIMEOUT_MS,
  } = options;

  const recognitionRef = useRef<ISpeechRecognition | null>(null);
  const stoppedByUserRef = useRef(false);
  const silenceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const finalTranscriptRef = useRef('');

  const [status, setStatus] = useState<RecognitionStatus>('idle');
  const [finalTranscript, setFinalTranscript] = useState('');
  const [interimTranscript, setInterimTranscript] = useState('');
  const [error, setError] = useState<RecognitionError | null>(null);

  // 用 ref 跟踪最新状态，避免闭包捕获过期值
  const statusRef = useRef<RecognitionStatus>('idle');
  statusRef.current = status;

  const isSupported = useMemo(() => {
    return typeof window !== 'undefined' && getSpeechRecognitionConstructor() !== null;
  }, []);

  // 组件挂载时检查是否支持
  useEffect(() => {
    if (!isSupported) {
      setStatus('unsupported');
    }
  }, [isSupported]);

  // 清理静音计时器
  const clearSilenceTimer = useCallback(() => {
    if (silenceTimerRef.current) {
      clearTimeout(silenceTimerRef.current);
      silenceTimerRef.current = null;
    }
  }, []);

  // 销毁当前 recognition 实例
  const destroyRecognition = useCallback(() => {
    clearSilenceTimer();
    const rec = recognitionRef.current;
    if (rec) {
      rec.onresult = null;
      rec.onerror = null;
      rec.onend = null;
      rec.onstart = null;
      rec.onspeechend = null;
      rec.onsoundstart = null;
      rec.onsoundend = null;
      recognitionRef.current = null;
    }
  }, [clearSilenceTimer]);

  const stop = useCallback(() => {
    stoppedByUserRef.current = true;
    clearSilenceTimer();
    if (recognitionRef.current) {
      try {
        recognitionRef.current.stop();
      } catch {
        // 可能已经停止了
      }
    }
    setStatus('processing');
  }, [clearSilenceTimer]);

  const abort = useCallback(() => {
    stoppedByUserRef.current = true;
    clearSilenceTimer();
    if (recognitionRef.current) {
      try {
        recognitionRef.current.abort();
      } catch {
        // 可能已经停止了
      }
    }
    finalTranscriptRef.current = '';
    setFinalTranscript('');
    setInterimTranscript('');
    setError(null);
    setStatus('idle');
    destroyRecognition();
  }, [clearSilenceTimer, destroyRecognition]);

  const reset = useCallback(() => {
    abort();
    setError(null);
    setStatus('idle');
  }, [abort]);

  const start = useCallback(() => {
    // Guard: 只能在 idle 或 error 状态下启动
    if (statusRef.current !== 'idle' && statusRef.current !== 'error') return;

    const Ctor = getSpeechRecognitionConstructor();
    if (!Ctor) {
      setStatus('unsupported');
      return;
    }

    // 销毁旧实例
    destroyRecognition();

    // 创建新实例
    const rec = new Ctor() as ISpeechRecognition;
    rec.continuous = continuous;
    rec.interimResults = interimResults;
    rec.lang = lang;
    rec.maxAlternatives = maxAlternatives;

    stoppedByUserRef.current = false;
    // 每次显式调用 start() 都从头开始，不保留上次识别内容
    finalTranscriptRef.current = '';
    setFinalTranscript('');
    setInterimTranscript('');
    setError(null);

    rec.onstart = () => {
      setStatus('listening');
    };

    rec.onresult = (event: SpeechRecognitionEvent) => {
      let interim = '';

      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i];
        if (result.isFinal) {
          const text = result[0]?.transcript ?? '';
          finalTranscriptRef.current += text;
        } else {
          interim += result[0]?.transcript ?? '';
        }
      }

      setFinalTranscript(finalTranscriptRef.current);
      setInterimTranscript(interim);
    };

    rec.onerror = (event: SpeechRecognitionErrorEvent) => {
      const errorType = mapErrorType(event.error);

      // no-speech 是良性错误，不阻止继续使用
      if (errorType === 'no-speech') {
        setError({
          type: errorType,
          message: event.message || '未检测到语音',
        });
        return;
      }

      // aborted 是用户主动操作
      if (errorType === 'aborted') {
        return;
      }

      setError({
        type: errorType,
        message: event.message || `语音识别错误: ${event.error}`,
        originalError: event,
      });
      setStatus('error');
    };

    rec.onspeechend = () => {
      // 用户停止说话，启动静音计时器
      if (silenceTimeout > 0) {
        clearSilenceTimer();
        silenceTimerRef.current = setTimeout(() => {
          if (recognitionRef.current && !stoppedByUserRef.current) {
            stop();
          }
        }, silenceTimeout);
      }
    };

    rec.onsoundstart = () => {
      // 用户开始说话，取消静音计时器
      clearSilenceTimer();
    };

    rec.onsoundend = () => {
      // 声音片段结束，留空
    };

    rec.onend = () => {
      const currentStatus = statusRef.current;

      // 如果用户没有主动停止，说明是 Chrome 的 60s 静音自动停止
      if (!stoppedByUserRef.current && currentStatus === 'listening') {
        try {
          rec.start();
          return;
        } catch {
          setStatus('idle');
          return;
        }
      }

      // 处理完成后恢复 idle
      if (currentStatus === 'processing') {
        setInterimTranscript('');
        setStatus('idle');
        return;
      }

      // 其他情况（比如 error 后 onend）
      if (currentStatus === 'listening' || currentStatus === 'error') {
        setStatus('idle');
      }
    };

    try {
      rec.start();
      recognitionRef.current = rec;
    } catch (err: any) {
      setError({
        type: 'not-allowed',
        message: '无法启动语音识别，请检查麦克风权限。',
        originalError: err,
      });
      setStatus('error');
    }
  }, [
    finalTranscript,
    continuous,
    interimResults,
    lang,
    maxAlternatives,
    silenceTimeout,
    destroyRecognition,
    stop,
    clearSilenceTimer,
  ]);

  // 组件卸载清理
  useEffect(() => {
    return () => {
      stoppedByUserRef.current = true;
      clearSilenceTimer();
      if (recognitionRef.current) {
        try {
          recognitionRef.current.abort();
        } catch {
          /* ignore */
        }
        destroyRecognition();
      }
    };
  }, [clearSilenceTimer, destroyRecognition]);

  return {
    status: isSupported ? status : 'unsupported',
    transcript: finalTranscript,
    interimTranscript,
    error,
    isSupported,
    start,
    stop,
    abort,
    reset,
  };
}
