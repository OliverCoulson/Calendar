import { useState, useCallback } from 'react';

const API = 'http://localhost:3002/api/calendar';

export interface CalendarEvent {
  id: string;
  title: string;
  startTime: string;
  endTime: string | null;  // null = 未指定结束时间
  location: string | null;
  description: string | null;
  remindTime: string | null;
  reminded: boolean;
}

export interface VoiceResult {
  success: boolean;
  recognizedText: string;
  intent: string;
  confidence: number;
  message: string;
  events: CalendarEvent[];
}

export function useCalendar() {
  const [events, setEvents] = useState<CalendarEvent[]>([]);
  const [loading, setLoading] = useState(false);

  // 获取所有事件
  const fetchEvents = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetch(`${API}/events`);
      const data = await res.json();
      setEvents(data);
    } catch {
      // 后端未启动
    } finally {
      setLoading(false);
    }
  }, []);

  // 发送语音文本到后端 NLP 处理
  const processVoice = useCallback(async (text: string): Promise<VoiceResult | null> => {
    try {
      const res = await fetch(`${API}/voice`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text }),
      });
      const data: VoiceResult = await res.json();
      if (data.events) {
        setEvents(data.events);
      }
      return data;
    } catch {
      return null;
    }
  }, []);

  // 删除事件
  const deleteEvent = useCallback(async (id: string) => {
    try {
      const res = await fetch(`${API}/events/${id}`, { method: 'DELETE' });
      const data = await res.json();
      if (data.events) setEvents(data.events);
      return data;
    } catch {
      return null;
    }
  }, []);

  // 获取待提醒事件
  const fetchReminders = useCallback(async (): Promise<CalendarEvent[]> => {
    try {
      const res = await fetch(`${API}/reminders/pending`);
      return await res.json();
    } catch {
      return [];
    }
  }, []);

  // 确认提醒
  const ackReminder = useCallback(async (id: string) => {
    try {
      await fetch(`${API}/reminders/${id}/ack`, { method: 'POST' });
    } catch { /* ignore */ }
  }, []);

  return { events, loading, fetchEvents, processVoice, deleteEvent, fetchReminders, ackReminder };
}

export type UseCalendarReturn = ReturnType<typeof useCalendar>;
