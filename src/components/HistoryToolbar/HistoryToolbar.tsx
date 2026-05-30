import { useState, useEffect, useRef } from 'react';
import styles from './HistoryToolbar.module.css';

interface HistoryToolbarProps {
  searchQuery: string;
  onSearchChange: (query: string) => void;
  sortOrder: 'newest' | 'oldest';
  onSortToggle: () => void;
  onExportJSON: () => void;
  onExportTXT: () => void;
  onClearAll: () => void;
  hasRecordings: boolean;
}

export function HistoryToolbar({
  searchQuery,
  onSearchChange,
  sortOrder,
  onSortToggle,
  onExportJSON,
  onExportTXT,
  onClearAll,
  hasRecordings,
}: HistoryToolbarProps) {
  const [localQuery, setLocalQuery] = useState(searchQuery);
  const debounceRef = useRef<ReturnType<typeof setTimeout>>();
  const [showExportMenu, setShowExportMenu] = useState(false);

  // 同步外部 searchQuery 变化
  useEffect(() => {
    setLocalQuery(searchQuery);
  }, [searchQuery]);

  // 防抖搜索
  const handleSearchInput = (value: string) => {
    setLocalQuery(value);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      onSearchChange(value);
    }, 250);
  };

  const handleClearSearch = () => {
    setLocalQuery('');
    onSearchChange('');
  };

  const handleClearAll = () => {
    if (window.confirm('确定要删除所有历史记录吗？此操作不可撤销。')) {
      onClearAll();
    }
  };

  return (
    <div className={styles.toolbar}>
      {/* 搜索框 */}
      <div className={styles.searchWrapper}>
        <svg className={styles.searchIcon} viewBox="0 0 24 24" fill="none" width="16" height="16">
          <circle cx="11" cy="11" r="7" stroke="currentColor" strokeWidth="2" fill="none" />
          <line x1="17" y1="17" x2="21" y2="21" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        </svg>
        <input
          className={styles.searchInput}
          type="text"
          placeholder="搜索历史记录..."
          value={localQuery}
          onChange={(e) => handleSearchInput(e.target.value)}
        />
        {localQuery && (
          <button className={styles.clearSearchBtn} onClick={handleClearSearch} type="button">
            <svg viewBox="0 0 20 20" fill="currentColor" width="14" height="14">
              <path
                fillRule="evenodd"
                d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z"
                clipRule="evenodd"
              />
            </svg>
          </button>
        )}
      </div>

      {/* 排序按钮 */}
      <button className={styles.toolBtn} onClick={onSortToggle} title="切换排序" type="button">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none">
          {sortOrder === 'newest' ? (
            <>
              <line x1="12" y1="5" x2="12" y2="19" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
              <polyline points="19 12 12 5 5 12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" fill="none" />
            </>
          ) : (
            <>
              <line x1="12" y1="19" x2="12" y2="5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
              <polyline points="5 12 12 19 19 12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" fill="none" />
            </>
          )}
        </svg>
        {sortOrder === 'newest' ? '最新' : '最早'}
      </button>

      {/* 导出菜单 */}
      <div className={styles.exportWrapper}>
        <button
          className={styles.toolBtn}
          onClick={() => setShowExportMenu(!showExportMenu)}
          disabled={!hasRecordings}
          title="导出"
          type="button"
        >
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none">
            <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" fill="none" />
            <polyline points="17 8 12 3 7 8" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" fill="none" />
            <line x1="12" y1="3" x2="12" y2="15" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
          </svg>
          导出
        </button>
        {showExportMenu && (
          <div className={styles.exportMenu}>
            <button
              className={styles.exportMenuItem}
              onClick={() => {
                onExportJSON();
                setShowExportMenu(false);
              }}
              type="button"
            >
              JSON 格式
            </button>
            <button
              className={styles.exportMenuItem}
              onClick={() => {
                onExportTXT();
                setShowExportMenu(false);
              }}
              type="button"
            >
              TXT 文本
            </button>
          </div>
        )}
      </div>

      {/* 清空 */}
      <button
        className={`${styles.toolBtn} ${styles.clearAllBtn}`}
        onClick={handleClearAll}
        disabled={!hasRecordings}
        title="清空全部"
        type="button"
      >
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none">
          <polyline points="3 6 5 6 21 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" fill="none" />
          <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" fill="none" />
        </svg>
        清空
      </button>
    </div>
  );
}
