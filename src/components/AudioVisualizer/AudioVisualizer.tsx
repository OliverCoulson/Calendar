import { useEffect, useRef } from 'react';
import styles from './AudioVisualizer.module.css';

interface AudioVisualizerProps {
  frequencyData: Uint8Array;
  isActive: boolean;
  barCount?: number;
  barColor?: string;
  barGap?: number;
}

export function AudioVisualizer({
  frequencyData,
  isActive,
  barCount = 40,
  barColor = '#4f46e5',
  barGap = 2,
}: AudioVisualizerProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const rafRef = useRef<number>(0);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;
    ctx.scale(dpr, dpr);

    const width = rect.width;
    const height = rect.height;

    const draw = () => {
      ctx.clearRect(0, 0, width, height);

      const data = frequencyData;
      const len = data.length;

      if (!isActive || len === 0) {
        // 画平坦基线
        ctx.fillStyle = barColor + '1A'; // ~10% opacity
        const barW = (width - (barCount - 1) * barGap) / barCount;
        for (let i = 0; i < barCount; i++) {
          const x = i * (barW + barGap);
          ctx.fillRect(x, height - 3, barW, 3);
        }
        return;
      }

      const barWidth = (width - (barCount - 1) * barGap) / barCount;
      const step = Math.max(1, Math.floor(len / barCount));

      for (let i = 0; i < barCount; i++) {
        // 从对应频率区间取平均值
        let sum = 0;
        const start = i * step;
        const end = Math.min(start + step, len);
        for (let j = start; j < end; j++) {
          sum += data[j];
        }
        const avg = sum / (end - start);
        const barHeight = Math.max(3, (avg / 255) * height);

        const x = i * (barWidth + barGap);

        // 渐变：底部深色，顶部浅色
        const gradient = ctx.createLinearGradient(x, height, x, height - barHeight);
        gradient.addColorStop(0, barColor);
        gradient.addColorStop(1, barColor + '99');

        ctx.fillStyle = gradient;
        ctx.beginPath();
        ctx.roundRect(x, height - barHeight, barWidth, barHeight, [
          barWidth / 2,
          barWidth / 2,
          0,
          0,
        ]);
        ctx.fill();
      }

      rafRef.current = requestAnimationFrame(draw);
    };

    draw();

    return () => {
      cancelAnimationFrame(rafRef.current);
    };
  }, [frequencyData, isActive, barCount, barColor, barGap]);

  return (
    <div className={`${styles.container} ${isActive ? styles.active : ''}`}>
      <canvas ref={canvasRef} className={styles.canvas} />
    </div>
  );
}
