import { useEffect, useState } from 'react';
import { Dashboard } from './screens/Dashboard';
import { LoanQueue } from './screens/LoanQueue';
import { Transfer } from './screens/Transfer';
import { OfflinePayment } from './screens/OfflinePayment';
import { SystemOps } from './screens/SystemOps';

type ScreenKey = 'dashboard' | 'loans' | 'transfer' | 'offline' | 'ops';

const NAV: { key: ScreenKey; label: string; hint: string; render: () => React.ReactNode }[] = [
  { key: 'dashboard', label: 'Dashboard', hint: '1', render: () => <Dashboard /> },
  { key: 'loans', label: 'Loan Queue', hint: '2', render: () => <LoanQueue /> },
  { key: 'transfer', label: 'Transfer', hint: '3', render: () => <Transfer /> },
  { key: 'offline', label: 'Offline Payment', hint: '4', render: () => <OfflinePayment /> },
  { key: 'ops', label: 'System Ops', hint: '5', render: () => <SystemOps /> },
];

export default function App() {
  const [screen, setScreen] = useState<ScreenKey>('dashboard');
  const [theme, setTheme] = useState<'light' | 'dark'>(() =>
    (localStorage.getItem('lt-theme') as 'light' | 'dark') ?? 'light',
  );

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('lt-theme', theme);
  }, [theme]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement || e.target instanceof HTMLSelectElement) return;
      const idx = Number(e.key) - 1;
      if (idx >= 0 && idx < NAV.length) setScreen(NAV[idx].key);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const current = NAV.find((n) => n.key === screen) ?? NAV[0];

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-dot" aria-hidden />
          <span>Ledger Terminal</span>
        </div>
        <nav style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {NAV.map((n) => (
            <a
              key={n.key}
              href={`#${n.key}`}
              className={`nav-item ${screen === n.key ? 'active' : ''}`}
              onClick={(e) => {
                e.preventDefault();
                setScreen(n.key);
              }}
            >
              <span className="nav-label">{n.label}</span>
              <span className="nav-key">{n.hint}</span>
            </a>
          ))}
        </nav>
        <div style={{ marginTop: 'auto', display: 'flex', flexDirection: 'column', gap: 'var(--sp-sm)' }}>
          <button
            className="btn"
            onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}
            title="Toggle console theme"
          >
            {theme === 'light' ? '◐ Console' : '◑ Paper'}
          </button>
          <div className="section-note" style={{ fontSize: 10.5, lineHeight: 1.5 }}>
            Contract-first UI ·<br />
            types from docs/api_spec/*.yaml
          </div>
        </div>
      </aside>
      <main className="main" key={screen}>
        {current.render()}
      </main>
    </div>
  );
}
