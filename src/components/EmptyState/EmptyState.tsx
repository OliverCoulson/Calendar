import styles from './EmptyState.module.css';

interface EmptyStateProps {
  type: 'no-history' | 'no-search-results';
  hasSearchQuery?: boolean;
  onClearSearch?: () => void;
}

export function EmptyState({ type, hasSearchQuery, onClearSearch }: EmptyStateProps) {
  return (
    <div className={styles.container}>
      <div className={styles.icon}>
        {type === 'no-history' ? (
          <svg viewBox="0 0 80 80" fill="none" className={styles.svg}>
            <rect x="22" y="14" width="36" height="44" rx="8" stroke="currentColor" strokeWidth="2.5" fill="none" />
            <rect x="30" y="22" width="20" height="12" rx="2" fill="currentColor" opacity="0.3" />
            <line x1="30" y1="40" x2="50" y2="40" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.5" />
            <line x1="30" y1="46" x2="44" y2="46" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.5" />
            <circle cx="58" cy="58" r="14" fill="#4f46e5" stroke="white" strokeWidth="3" />
            <path d="M54 58l3 3 5-6" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" fill="none" />
          </svg>
        ) : (
          <svg viewBox="0 0 80 80" fill="none" className={styles.svg}>
            <circle cx="36" cy="36" r="16" stroke="currentColor" strokeWidth="2.5" fill="none" />
            <line x1="47.5" y1="47.5" x2="58" y2="58" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
            <line x1="28" y1="36" x2="44" y2="36" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.4" />
          </svg>
        )}
      </div>
      <p className={styles.title}>
        {type === 'no-history'
          ? '还没有语音记录'
          : '没有找到匹配的记录'}
      </p>
      <p className={styles.hint}>
        {type === 'no-history'
          ? '点击麦克风按钮开始语音输入吧！'
          : hasSearchQuery
            ? '试试修改搜索关键词'
            : ''}
      </p>
      {type === 'no-search-results' && hasSearchQuery && onClearSearch && (
        <button className={styles.clearBtn} onClick={onClearSearch} type="button">
          清除搜索
        </button>
      )}
    </div>
  );
}
