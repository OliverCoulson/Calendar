import { useState, useEffect, useMemo } from 'react';
import type { CalendarEvent } from '../../hooks/useCalendar';
import styles from './CalendarView.module.css';

interface CalendarViewProps {
  events: CalendarEvent[];
  loading: boolean;
  onFetchEvents: () => void;
  onDeleteEvent: (id: string) => void;
}

const WEEKDAYS = ['一', '二', '三', '四', '五', '六', '日'];

function formatEventTime(iso: string | null) {
  if (!iso) return '';
  return iso.slice(11, 16);
}

function formatTimeRange(evt: CalendarEvent): string {
  const start = formatEventTime(evt.startTime);
  const end = formatEventTime(evt.endTime);
  return end ? `${start} - ${end}` : start;
}

export function CalendarView({ events, loading, onFetchEvents, onDeleteEvent }: CalendarViewProps) {
  const today = new Date();
  const [viewYear, setViewYear] = useState(today.getFullYear());
  const [viewMonth, setViewMonth] = useState(today.getMonth() + 1);
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const [hoveredDate, setHoveredDate] = useState<string | null>(null);
  const [tooltipPos, setTooltipPos] = useState({ x: 0, y: 0 });

  useEffect(() => {
    onFetchEvents();
  }, [onFetchEvents]);

  // 计算当前月 42 格 = 6行×7列
  const calendarDays = useMemo(() => {
    const firstDayOfMonth = new Date(viewYear, viewMonth - 1, 1);
    const lastDayOfMonth = new Date(viewYear, viewMonth, 0);
    const daysInMonth = lastDayOfMonth.getDate();

    let startDow = firstDayOfMonth.getDay();
    startDow = startDow === 0 ? 6 : startDow - 1;

    const days: (number | null)[] = [];
    const prevMonthLastDay = new Date(viewYear, viewMonth - 1, 0).getDate();
    for (let i = startDow - 1; i >= 0; i--) {
      days.push(-(prevMonthLastDay - i));
    }
    for (let d = 1; d <= daysInMonth; d++) {
      days.push(d);
    }
    let nextDay = 1;
    while (days.length < 42) {
      days.push(-(100 + nextDay++));
    }
    return days;
  }, [viewYear, viewMonth]);

  const eventsByDate = useMemo(() => {
    const map: Record<string, CalendarEvent[]> = {};
    for (const evt of events) {
      const dateKey = evt.startTime.slice(0, 10);
      if (!map[dateKey]) map[dateKey] = [];
      map[dateKey].push(evt);
    }
    // 每个日期的事件按时间排序
    for (const key of Object.keys(map)) {
      map[key].sort((a, b) => a.startTime.localeCompare(b.startTime));
    }
    return map;
  }, [events]);

  const selectedEvents = selectedDate
    ? eventsByDate[selectedDate] || []
    : [];

  const hoveredEvents = hoveredDate
    ? eventsByDate[hoveredDate] || []
    : [];

  const todayKey = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;

  const goPrevMonth = () => {
    if (viewMonth === 1) { setViewYear(viewYear - 1); setViewMonth(12); }
    else { setViewMonth(viewMonth - 1); }
  };

  const goNextMonth = () => {
    if (viewMonth === 12) { setViewYear(viewYear + 1); setViewMonth(1); }
    else { setViewMonth(viewMonth + 1); }
  };

  const goToday = () => {
    setViewYear(today.getFullYear());
    setViewMonth(today.getMonth() + 1);
    setSelectedDate(todayKey);
  };

  const getDateKey = (day: number): string =>
    `${viewYear}-${String(viewMonth).padStart(2, '0')}-${String(day).padStart(2, '0')}`;

  const getMonthDateKey = (month: number, day: number): string =>
    `${viewYear}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;

  const monthLabel = `${viewYear}年 ${viewMonth}月`;

  // 日期 cell 的渲染数据
  const cellData = useMemo(() => {
    return calendarDays.map((day) => {
      if (day === null) return null;
      const isPrevMonth = day < 0 && day > -100;
      const isNextMonth = day < -50;
      const actualDay = isPrevMonth ? -day : isNextMonth ? -(day + 100) : day;
      const month = isPrevMonth ? viewMonth - 1 : isNextMonth ? viewMonth + 1 : viewMonth;
      const dateKey = getMonthDateKey(month, actualDay);
      const dayEvents = eventsByDate[dateKey] || [];
      return { day, actualDay, dateKey, dayEvents, isPrevMonth, isNextMonth, isOtherMonth: isPrevMonth || isNextMonth };
    });
  }, [calendarDays, viewYear, viewMonth, eventsByDate]);

  const handleMouseEnter = (e: React.MouseEvent, dateKey: string, dayEvents: CalendarEvent[]) => {
    if (dayEvents.length === 0) return;
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    setTooltipPos({ x: rect.left + rect.width / 2, y: rect.top - 8 });
    setHoveredDate(dateKey);
  };

  const handleMouseLeave = () => {
    setHoveredDate(null);
  };

  return (
    <div className={styles.calendar}>
      {/* === 月份导航 === */}
      <div className={styles.nav}>
        <button className={styles.navBtn} onClick={goPrevMonth} type="button">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <polyline points="15 18 9 12 15 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </button>
        <h3 className={styles.monthLabel}>{monthLabel}</h3>
        <button className={styles.navBtn} onClick={goNextMonth} type="button">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <polyline points="9 18 15 12 9 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </button>
        <button className={styles.todayBtn} onClick={goToday} type="button">
          今天
        </button>
      </div>

      {/* === 星期头 === */}
      <div className={styles.weekHeader}>
        {WEEKDAYS.map((w) => (
          <div key={w} className={styles.weekDay}>{w}</div>
        ))}
      </div>

      {/* === 日期网格 === */}
      <div className={styles.grid}>
        {cellData.map((data, idx) => {
          if (!data) return <div key={idx} className={styles.emptyCell} />;

          const { actualDay, dateKey, dayEvents, isOtherMonth } = data;
          const isToday = dateKey === todayKey;
          const isSelected = dateKey === selectedDate;
          const count = dayEvents.length;

          return (
            <button
              key={idx}
              className={`${styles.cell} ${isToday ? styles.today : ''} ${isSelected ? styles.selected : ''} ${isOtherMonth ? styles.otherMonth : ''} ${count > 0 ? styles.hasEvent : ''}`}
              onClick={() => setSelectedDate(dateKey)}
              onMouseEnter={(e) => handleMouseEnter(e, dateKey, dayEvents)}
              onMouseLeave={handleMouseLeave}
              type="button"
            >
              <span className={styles.dayNum}>{actualDay}</span>

              {/* 事件显示：≤2 个显示标题文字，>2 个显示圆点 + ... */}
              {count > 0 && count <= 2 && (
                <div className={styles.eventLabels}>
                  {dayEvents.map((evt) => (
                    <span key={evt.id} className={styles.eventLabel} title={`${formatEventTime(evt.startTime)} ${evt.title}`}>
                      {evt.title.length > 4 ? evt.title.slice(0, 4) + '…' : evt.title}
                    </span>
                  ))}
                </div>
              )}
              {count > 2 && (
                <div className={styles.eventLabels}>
                  {dayEvents.slice(0, 2).map((evt) => (
                    <span key={evt.id} className={styles.eventLabel}>
                      {evt.title.length > 4 ? evt.title.slice(0, 4) + '…' : evt.title}
                    </span>
                  ))}
                  <span className={styles.eventMore}>+{count - 2} …</span>
                </div>
              )}
            </button>
          );
        })}
      </div>

      {/* === Hover 浮窗 === */}
      {hoveredDate && hoveredEvents.length > 0 && (
        <div
          className={styles.tooltip}
          style={{ left: tooltipPos.x, top: tooltipPos.y }}
        >
          <div className={styles.tooltipDate}>{hoveredDate}</div>
          {hoveredEvents.map((evt) => (
            <div key={evt.id} className={styles.tooltipItem}>
              <span className={styles.tooltipTime}>{formatTimeRange(evt)}</span>
              <span className={styles.tooltipTitle}>{evt.title}</span>
            </div>
          ))}
        </div>
      )}

      {/* === 选中日期的事件列表 === */}
      {selectedDate && (
        <div className={styles.eventPanel}>
          <div className={styles.eventPanelHeader}>
            <h4 className={styles.eventPanelTitle}>
              {selectedDate}
              {selectedDate === todayKey ? ' 今天' : ''}
            </h4>
            {loading && <span className={styles.loadingHint}>加载中...</span>}
          </div>

          {selectedEvents.length === 0 ? (
            <p className={styles.noEvents}>当天无事件</p>
          ) : (
            <ul className={styles.eventList}>
              {selectedEvents.map((evt) => (
                <li key={evt.id} className={styles.eventItem}>
                  <div className={styles.eventTimeBadge}>
                    {formatTimeRange(evt)}
                  </div>
                  <span className={styles.eventTitle}>{evt.title}</span>
                  {evt.description && (
                    <span className={styles.eventDesc}>{evt.description}</span>
                  )}
                  <button
                    className={styles.eventDelete}
                    onClick={() => onDeleteEvent(evt.id)}
                    title="删除"
                    type="button"
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                      <line x1="18" y1="6" x2="6" y2="18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                      <line x1="6" y1="6" x2="18" y2="18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                    </svg>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
