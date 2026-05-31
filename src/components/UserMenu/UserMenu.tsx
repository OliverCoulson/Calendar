import { useState, useEffect, useCallback } from 'react';
import styles from './UserMenu.module.css';

interface UserInfo {
  id: number; phone: string; nickname: string; avatar: string; type: string; token: string;
  pendingCount?: number;
  relations?: Array<{ id: number; nickname: string; phone: string; avatar: string; alias: string | null; type: string }>;
}
interface PendingReq {
  id: number; nickname: string; phone: string; avatar: string; alias: string | null; type: string; created_at: string;
}
function displayName(r: { nickname: string; alias: string | null }) {
  return r.alias || r.nickname;
}

const API = 'http://localhost:3002/api/auth';

export function UserMenu() {
  const [user, setUser] = useState<UserInfo | null>(null);
  const [showModal, setShowModal] = useState(false);
  const [showDropdown, setShowDropdown] = useState(false);
  const [mode, setMode] = useState<'login' | 'register' | 'bind'>('login');
  const [phone, setPhone] = useState(''); const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState(''); const [type, setType] = useState<'elderly' | 'guardian'>('elderly');
  const [targetPhone, setTargetPhone] = useState('');
  const [alias, setAlias] = useState('');
  const [msg, setMsg] = useState(''); const [loading, setLoading] = useState(false);
  const [pendingReqs, setPendingReqs] = useState<PendingReq[]>([]);

  const fetchUser = useCallback(async (token: string) => {
    try {
      const res = await fetch(`${API}/me`, { headers: { 'X-Token': token } });
      const data = await res.json();
      if (data.success && data.id) setUser(data);
    } catch { /* */ }
  }, []);

  // 启动时检查登录 + 监听全局 open-login
  useEffect(() => {
    const token = localStorage.getItem('calendar_token');
    if (token) fetchUser(token);
    const handler = () => { setMode('login'); setShowModal(true); };
    window.addEventListener('open-login', handler);
    return () => window.removeEventListener('open-login', handler);
  }, [fetchUser]);

  // 拉取待处理请求（下拉打开时刷新）
  const fetchPending = useCallback(async () => {
    if (!user) return;
    try {
      const res = await fetch(`${API}/bind/pending`, { headers: { 'X-Token': user.token } });
      const data = await res.json();
      setPendingReqs(Array.isArray(data) ? data : []);
    } catch { /* */ }
  }, [user]);

  useEffect(() => { fetchPending(); }, [fetchPending]);

  // 打开下拉时刷新
  const handleToggleDropdown = () => {
    if (!showDropdown) fetchPending();
    setShowDropdown(!showDropdown);
  };

  const handleAuth = async () => {
    setLoading(true); setMsg('');
    const endpoint = mode === 'login' ? '/login' : '/register';
    const body: Record<string, string> = { phone, password };
    if (mode === 'register') { body.nickname = nickname || phone; body.type = type; }
    try {
      const res = await fetch(API + endpoint, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
      const data = await res.json();
      setMsg(data.message);
      if (data.success && data.token) {
        localStorage.setItem('calendar_token', data.token);
        await fetchUser(data.token);
        setShowModal(false);
      }
    } catch { setMsg('网络错误'); } finally { setLoading(false); }
  };

  const handleBind = async () => {
    console.log('[Bind] handleBind 触发, user=', user?.nickname, 'targetPhone=', targetPhone);
    if (!user) { setMsg('未登录'); return; }
    if (!targetPhone) { setMsg('请输入手机号'); return; }
    setLoading(true); setMsg('');
    try {
      console.log('[Bind] 发送请求到:', `${API}/bind/request`);
      const res = await fetch(`${API}/bind/request`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Token': user.token },
        body: JSON.stringify({ targetPhone, alias }),
      });
      console.log('[Bind] 响应状态:', res.status);
      const data = await res.json();
      console.log('[Bind] 响应数据:', data);
      setMsg(data.message);
      if (data.success) setShowModal(false);
    } catch (e) { console.error('[Bind] 网络错误:', e); setMsg('网络错误: ' + (e as Error).message); } finally { setLoading(false); }
  };

  const handleApprove = async (id: number) => {
    if (!user) return;
    await fetch(`${API}/bind/${id}/handle`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Token': user.token },
      body: JSON.stringify({ action: 1 }),
    });
    setPendingReqs(prev => prev.filter(r => r.id !== id));
    fetchUser(user.token);
  };

  const handleReject = async (id: number) => {
    if (!user) return;
    await fetch(`${API}/bind/${id}/handle`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Token': user.token },
      body: JSON.stringify({ action: 2 }),
    });
    setPendingReqs(prev => prev.filter(r => r.id !== id));
  };

  const handleLogout = () => { localStorage.removeItem('calendar_token'); setUser(null); setShowDropdown(false); };

  const openModal = (m: 'login' | 'register' | 'bind') => { setMode(m); setMsg(''); setShowModal(true); };
  const pendingBadge = pendingReqs.length;

  return (
    <>
      {/* 头像按钮 + 红点 */}
      <button className={styles.avatarBtn} onClick={() => user ? handleToggleDropdown() : openModal('login')} type="button">
        {user ? <span className={styles.avatar}>{user.avatar}</span> : (
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="8" r="4" stroke="currentColor" strokeWidth="2" fill="none" />
            <path d="M4 20c0-4 4-7 8-7s8 3 8 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" fill="none" />
          </svg>
        )}
        {pendingBadge > 0 && <span className={styles.badge}>{pendingBadge}</span>}
      </button>

      {/* 下拉菜单 */}
      {user && showDropdown && (
        <div className={styles.dropdown}>
          <div className={styles.userInfo}>
            <span className={styles.userAvatar}>{user.avatar}</span>
            <div>
              <div className={styles.userName}>{user.nickname}</div>
              <div className={styles.userPhone}>{user.phone}</div>
            </div>
          </div>
          <div className={styles.userMeta}>{user.type === 'elderly' ? '👴 老人' : '👤 监护人'}</div>

          {/* 待处理请求 */}
          {pendingReqs.length > 0 && (
            <div className={styles.section}>
              <div className={styles.sectionTitle}>🔔 绑定请求 ({pendingReqs.length})</div>
              {pendingReqs.map(r => (
                <div key={r.id} className={styles.reqItem}>
                  <span>{r.avatar} {displayName(r)}</span>
                  <span className={styles.reqPhone}>{r.phone}</span>
                  <div className={styles.reqActions}>
                    <button className={styles.approveBtn} onClick={() => handleApprove(r.id)}>✓</button>
                    <button className={styles.rejectBtn} onClick={() => handleReject(r.id)}>✕</button>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* 已绑定关系 */}
          {user.relations && user.relations.length > 0 && (
            <div className={styles.section}>
              <div className={styles.sectionTitle}>🔗 已绑定</div>
              {user.relations.map(r => (
                <div key={r.id} className={styles.relItem}>
                  <span>{r.avatar} {displayName(r)}</span>
                  {r.alias && <span className={styles.relAlias}>({r.nickname})</span>}
                </div>
              ))}
            </div>
          )}

          <button className={styles.menuBtn} onClick={() => { setShowDropdown(false); openModal('bind'); }}>➕ 添加绑定</button>
          <button className={styles.menuBtn} onClick={handleLogout} style={{ color: '#dc2626' }}>退出登录</button>
        </div>
      )}

      {/* 弹窗 */}
      {showModal && (
        <div className={styles.overlay} onClick={() => setShowModal(false)}>
          <div className={styles.modal} onClick={e => e.stopPropagation()}>
            <h3 className={styles.modalTitle}>{mode === 'login' ? '登录' : mode === 'register' ? '注册' : '添加绑定'}</h3>
            {mode === 'bind' && user ? (
              <>
                <p className={styles.bindHint}>输入对方手机号发起绑定申请</p>
                <input className={styles.input} type="text" maxLength={11} placeholder="对方手机号" value={targetPhone} onChange={e => setTargetPhone(e.target.value)} />
                <input className={styles.input} placeholder="备注名（如：爸爸、小明）" value={alias} onChange={e => setAlias(e.target.value)} />
                <button className={styles.btn} onClick={handleBind} disabled={loading}>{loading ? '发送中...' : '发送申请'}</button>
              </>
            ) : (
              <>
                <input className={styles.input} type="tel" maxLength={11} placeholder="手机号" value={phone} onChange={e => setPhone(e.target.value)} />
                <input className={styles.input} type="password" placeholder="密码（至少4位）" value={password} onChange={e => setPassword(e.target.value)} />
                {mode === 'register' && (<>
                  <input className={styles.input} placeholder="昵称" value={nickname} onChange={e => setNickname(e.target.value)} />
                  <div className={styles.typeGroup}>
                    <button className={`${styles.typeBtn} ${type === 'elderly' ? styles.active : ''}`} onClick={() => setType('elderly')}>👴 老人</button>
                    <button className={`${styles.typeBtn} ${type === 'guardian' ? styles.active : ''}`} onClick={() => setType('guardian')}>👤 监护人</button>
                  </div>
                </>)}
                <button className={styles.btn} onClick={handleAuth} disabled={loading}>{loading ? '处理中...' : mode === 'login' ? '登录' : '注册'}</button>
              </>
            )}
            {msg && <p className={styles.msg}>{msg}</p>}
            {mode === 'login' ? <p className={styles.switch}>没有账号？<button onClick={() => openModal('register')}>去注册</button></p>
              : mode === 'register' ? <p className={styles.switch}>已有账号？<button onClick={() => openModal('login')}>去登录</button></p> : null}
          </div>
        </div>
      )}
    </>
  );
}
