import { useHistory } from './hooks/useHistory';
import { useCalendar } from './hooks/useCalendar';
import { HistoryContext } from './contexts/HistoryContext';
import { CalendarContext } from './contexts/CalendarContext';
import { VoiceRecorder } from './components/VoiceRecorder';
import { CalendarView } from './components/CalendarView';
import { HistoryPanel } from './components/HistoryPanel';
import { ReminderAlert } from './components/ReminderAlert';
import styles from './App.module.css';

function App() {
  const history = useHistory();
  const calendar = useCalendar();

  return (
    <HistoryContext.Provider value={history}>
      <CalendarContext.Provider value={calendar}>
        <div className={styles.app}>
          <div className={styles.card}>
            <header className={styles.header}>
              <h1 className={styles.title}>
                <svg className={styles.titleIcon} viewBox="0 0 24 24" fill="none" width="28" height="28">
                  <rect x="9" y="1" width="6" height="11" rx="3" fill="#4f46e5" />
                  <path d="M5 11a7 7 0 0 0 14 0" stroke="#4f46e5" strokeWidth="2" strokeLinecap="round" fill="none" />
                </svg>
                语音日历
              </h1>
              <p className={styles.subtitle}>
                说出「明天下午三点开会」即可自动添加到日历
              </p>
            </header>

            <VoiceRecorder />
          </div>

          <div className={styles.card}>
            <CalendarView
              events={calendar.events}
              loading={calendar.loading}
              onFetchEvents={calendar.fetchEvents}
              onDeleteEvent={calendar.deleteEvent}
            />
          </div>

          <div className={styles.card}>
            <HistoryPanel />
          </div>
        </div>

        <ReminderAlert
          onFetchReminders={calendar.fetchReminders}
          onAckReminder={calendar.ackReminder}
        />
      </CalendarContext.Provider>
    </HistoryContext.Provider>
  );
}

export default App;
