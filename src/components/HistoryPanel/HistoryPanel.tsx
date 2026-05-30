import { useSharedHistory } from '../../contexts/HistoryContext';
import { HistoryToolbar } from '../HistoryToolbar';
import { HistoryItem } from '../HistoryItem';
import { EmptyState } from '../EmptyState';
import styles from './HistoryPanel.module.css';

export function HistoryPanel() {
  const {
    filteredRecordings,
    recordings,
    searchQuery,
    sortOrder,
    setSearchQuery,
    setSortOrder,
    deleteRecording,
    clearAll,
    exportAsJSON,
    exportAsTXT,
  } = useSharedHistory();

  const handleSortToggle = () => {
    setSortOrder(sortOrder === 'newest' ? 'oldest' : 'newest');
  };

  return (
    <section className={styles.panel}>
      <header className={styles.header}>
        <h2 className={styles.title}>历史记录</h2>
        {recordings.length > 0 && (
          <span className={styles.count}>{recordings.length} 条</span>
        )}
      </header>

      <HistoryToolbar
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        sortOrder={sortOrder}
        onSortToggle={handleSortToggle}
        onExportJSON={exportAsJSON}
        onExportTXT={exportAsTXT}
        onClearAll={clearAll}
        hasRecordings={recordings.length > 0}
      />

      <div className={styles.list}>
        {filteredRecordings.length === 0 ? (
          <EmptyState
            type={
              recordings.length === 0 ? 'no-history' : 'no-search-results'
            }
            hasSearchQuery={searchQuery.length > 0}
            onClearSearch={() => setSearchQuery('')}
          />
        ) : (
          filteredRecordings.map((rec) => (
            <HistoryItem
              key={rec.id}
              recording={rec}
              onDelete={deleteRecording}
            />
          ))
        )}
      </div>
    </section>
  );
}
