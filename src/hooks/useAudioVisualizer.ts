import { useCallback, useEffect, useRef, useState } from 'react';
import type { UseAudioVisualizerOptions, UseAudioVisualizerReturn } from '../types';

function mapMediaError(err: DOMException | Error): string {
  if (err instanceof DOMException) {
    switch (err.name) {
      case 'NotAllowedError':
        return '麦克风权限已被拒绝';
      case 'NotFoundError':
        return '未检测到麦克风设备';
      case 'NotReadableError':
        return '无法访问麦克风，可能被其他应用占用';
      case 'OverconstrainedError':
        return '麦克风不满足所需约束';
      default:
        return `音频采集失败: ${err.message}`;
    }
  }
  return `音频采集失败: ${err.message}`;
}

export function useAudioVisualizer(
  options: UseAudioVisualizerOptions = {},
): UseAudioVisualizerReturn {
  const {
    fftSize = 128,
    smoothingTimeConstant = 0.8,
    minDecibels = -90,
    maxDecibels = -10,
  } = options;

  const streamRef = useRef<MediaStream | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const rafRef = useRef<number>(0);

  const [frequencyData, setFrequencyData] = useState<Uint8Array>(
    new Uint8Array(0),
  );
  const [isActive, setIsActive] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const stop = useCallback(() => {
    cancelAnimationFrame(rafRef.current);
    rafRef.current = 0;

    if (streamRef.current) {
      streamRef.current.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
    }

    if (audioContextRef.current) {
      audioContextRef.current.close().catch(() => {
        /* ignore */
      });
      audioContextRef.current = null;
    }

    analyserRef.current = null;
    setIsActive(false);
    setFrequencyData(new Uint8Array(0));
  }, []);

  const start = useCallback(async () => {
    setError(null);

    try {
      // 获取音频流
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      streamRef.current = stream;

      // 创建 AudioContext
      const AudioContextCtor =
        window.AudioContext ||
        // @ts-expect-error webkit prefix
        window.webkitAudioContext;
      const ctx = new AudioContextCtor();
      audioContextRef.current = ctx;

      // 如果 AudioContext 被暂停（浏览器策略），恢复它
      if (ctx.state === 'suspended') {
        await ctx.resume();
      }

      // 创建 AnalyserNode
      const analyser = ctx.createAnalyser();
      analyser.fftSize = fftSize;
      analyser.smoothingTimeConstant = smoothingTimeConstant;
      analyser.minDecibels = minDecibels;
      analyser.maxDecibels = maxDecibels;
      analyserRef.current = analyser;

      // 连接：source → analyser（不连接到 destination，避免回声）
      const source = ctx.createMediaStreamSource(stream);
      source.connect(analyser);

      setIsActive(true);

      // RAF 循环采集频率数据
      const bufferLength = analyser.frequencyBinCount;
      const dataArray = new Uint8Array(bufferLength);

      const loop = () => {
        if (!analyserRef.current) return;
        analyserRef.current.getByteFrequencyData(dataArray);
        setFrequencyData(new Uint8Array(dataArray));
        rafRef.current = requestAnimationFrame(loop);
      };
      loop();
    } catch (err: any) {
      setError(mapMediaError(err));
    }
  }, [fftSize, smoothingTimeConstant, minDecibels, maxDecibels]);

  // 页面隐藏时暂停 RAF，节省 CPU
  useEffect(() => {
    const handleVisibility = () => {
      if (document.hidden && rafRef.current) {
        cancelAnimationFrame(rafRef.current);
        rafRef.current = 0;
      } else if (!document.hidden && isActive && analyserRef.current) {
        const bufferLength = analyserRef.current.frequencyBinCount;
        const dataArray = new Uint8Array(bufferLength);
        const loop = () => {
          if (!analyserRef.current) return;
          analyserRef.current.getByteFrequencyData(dataArray);
          setFrequencyData(new Uint8Array(dataArray));
          rafRef.current = requestAnimationFrame(loop);
        };
        loop();
      }
    };

    document.addEventListener('visibilitychange', handleVisibility);
    return () => {
      document.removeEventListener('visibilitychange', handleVisibility);
    };
  }, [isActive]);

  // 组件卸载清理
  useEffect(() => {
    return () => {
      cancelAnimationFrame(rafRef.current);
      streamRef.current?.getTracks().forEach((t) => t.stop());
      audioContextRef.current?.close().catch(() => {
        /* ignore */
      });
    };
  }, []);

  return {
    frequencyData,
    isActive,
    error,
    start,
    stop,
  };
}
