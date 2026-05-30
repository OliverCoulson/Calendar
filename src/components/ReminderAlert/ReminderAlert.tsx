import { useEffect, useRef, useCallback, useState } from 'react';
import type { CalendarEvent } from '../../hooks/useCalendar';
import styles from './ReminderAlert.module.css';

interface ReminderAlertProps {
  onFetchReminders: () => Promise<CalendarEvent[]>;
  onAckReminder: (id: string) => Promise<void>;
}

export function ReminderAlert({ onFetchReminders, onAckReminder }: ReminderAlertProps) {
  const [activeReminders, setActiveReminders] = useState<CalendarEvent[]>([]);
  const [dismissedIds, setDismissedIds] = useState<Set<string>>(new Set());
  const notifiedRef = useRef<Set<string>>(new Set());
  const audioCtxRef = useRef<AudioContext | null>(null);

  // 请求通知权限
  useEffect(() => {
    if ('Notification' in window && Notification.permission === 'default') {
      Notification.requestPermission();
    }
  }, []);

  // 播放提示音
  const playBeep = useCallback(() => {
    try {
      if (!audioCtxRef.current) {
        audioCtxRef.current = new AudioContext();
      }
      const ctx = audioCtxRef.current;
      // 三段短促蜂鸣
      [0, 0.2, 0.4].forEach((delay) => {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.frequency.value = 880;
        osc.type = 'sine';
        gain.gain.setValueAtTime(0.3, ctx.currentTime + delay);
        gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + delay + 0.15);
        osc.start(ctx.currentTime + delay);
        osc.stop(ctx.currentTime + delay + 0.15);
      });
    } catch {
      /* 浏览器限制 */
    }
  }, []);

  // 发送浏览器通知
  const sendNotification = useCallback((title: string, body: string) => {
    if ('Notification' in window && Notification.permission === 'granted') {
      try {
        new Notification(title, { body, icon: '🎤', tag: 'calendar-reminder' });
      } catch { /* ignore */ }
    }
  }, []);

  // 轮询提醒（每 30 秒）
  useEffect(() => {
    const check = async () => {
      const reminders = await onFetchReminders();
      if (reminders.length === 0) return;

      // 过滤已通知过的
      const fresh = reminders.filter((r) => !notifiedRef.current.has(r.id));

      if (fresh.length > 0) {
        // 播放声音
        playBeep();

        // 发送通知
        fresh.forEach((r) => {
          const time = r.startTime.slice(11, 16);
          sendNotification('⏰ 日程提醒', `${time} — ${r.title}`);
          notifiedRef.current.add(r.id);
        });

        // 显示提醒弹窗
        setActiveReminders((prev) => {
          const existing = new Set(prev.map((r) => r.id));
          const toAdd = fresh.filter((r) => !existing.has(r.id));
          return [...prev, ...toAdd];
        });
      }
    };

    // 首次立即检查
    check();
    const interval = setInterval(check, 30000);
    return () => clearInterval(interval);
  }, [onFetchReminders, playBeep, sendNotification]);

  // 关闭单个提醒
  const handleDismiss = async (id: string) => {
    setDismissedIds((prev) => new Set(prev).add(id));
    await onAckReminder(id);

    // 延迟移除
    setTimeout(() => {
      setActiveReminders((prev) => prev.filter((r) => r.id !== id));
    }, 300);
  };

  const visibleReminders = activeReminders.filter((r) => !dismissedIds.has(r.id));

  if (visibleReminders.length === 0) return null;

  return (
    <div className={styles.overlay}>
      {visibleReminders.map((r) => {
        const time = r.startTime.slice(11, 16);
        return (
          <div key={r.id} className={`${styles.toast} ${dismissedIds.has(r.id) ? styles.dismissing : styles.enter}`}>
            <div className={styles.toastIcon}>⏰</div>
            <div className={styles.toastBody}>
              <div className={styles.toastTitle}>{r.title}</div>
              <div className={styles.toastTime}>
                {r.startTime.slice(0, 10)} {time}
                {r.endTime ? ` — ${r.endTime.slice(11, 16)}` : ''}
              </div>
            </div>
            <button className={styles.toastBtn} onClick={() => handleDismiss(r.id)} type="button">
              知道了
            </button>
          </div>
        );
      })}
    </div>
  );
}
