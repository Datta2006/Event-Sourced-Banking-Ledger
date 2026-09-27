import { useEffect, useState } from 'react';
import { api } from './api/client';
import { mockApi } from './mock/server';
import { usePoll } from './lib/poll';

// customer screens
import { Dashboard } from './screens/customer/Dashboard';
import { Statement } from './screens/customer/Statement';
import { Transfer } from './screens/customer/Transfer';
import { OfflinePayment } from './screens/customer/OfflinePayment';
import { ApplyLoan } from './screens/customer/ApplyLoan';
import { MyLoans } from './screens/customer/MyLoans';
import { TrustScore } from './screens/customer/TrustScore';
import { Notifications } from './screens/customer/Notifications';
import { FraudAlerts } from './screens/customer/FraudAlerts';
// bank screens
import { OpsDashboard } from './screens/bank/OpsDashboard';
import { LoanQueue } from './screens/bank/LoanQueue';
import { PoolManagement } from './screens/bank/PoolManagement';
import { FraudReview } from './screens/bank/FraudReview';
import { AccountsManagement } from './screens/bank/AccountsManagement';
import { TrustAudit } from './screens/bank/TrustAudit';
import { ReservationsOverview } from './screens/bank/ReservationsOverview';
import { NotificationsLog } from './screens/bank/NotificationsLog';
import { SystemOps } from './screens/bank/SystemOps';
import { Fragmentation } from './screens/bank/Fragmentation';

type Role = 'customer' | 'bank';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

type NavItem = {
  key: string;
  label: string;
  hint: string;
  render: (ctx: { accountId: string }) => React.ReactNode;
};

const CUSTOMER_NAV: NavItem[] = [
  { key: 'dashboard', label: 'Accounts', hint: '1', render: ({ accountId }) => <Dashboard accountId={accountId} /> },
  { key: 'statement', label: 'Statement', hint: '2', render: ({ accountId }) => <Statement accountId={accountId} /> },
  { key: 'transfer', label: 'Transfer', hint: '3', render: ({ accountId }) => <Transfer accountId={accountId} /> },
  { key: 'offline', label: 'Offline payment', hint: '4', render: ({ accountId }) => <OfflinePayment accountId={accountId} /> },
  { key: 'apply', label: 'Apply for loan', hint: '5', render: ({ accountId }) => <ApplyLoan accountId={accountId} /> },
  { key: 'loans', label: 'My loans', hint: '6', render: ({ accountId }) => <MyLoans accountId={accountId} /> },
  { key: 'trust', label: 'Trust score', hint: '7', render: ({ accountId }) => <TrustScore accountId={accountId} /> },
  { key: 'alerts', label: 'Fraud alerts', hint: '8', render: ({ accountId }) => <FraudAlerts accountId={accountId} /> },
  { key: 'notifications', label: 'Notifications', hint: '9', render: ({ accountId }) => <Notifications accountId={accountId} /> },
];

const BANK_NAV: NavItem[] = [
  { key: 'ops', label: 'Ops dashboard', hint: '1', render: () => <OpsDashboard /> },
  { key: 'queue', label: 'Loan queue', hint: '2', render: () => <LoanQueue /> },
  { key: 'pool', label: 'Pool management', hint: '3', render: () => <PoolManagement /> },
  { key: 'fraud', label: 'Fraud review', hint: '4', render: () => <FraudReview /> },
  { key: 'accounts', label: 'Accounts', hint: '5', render: () => <AccountsManagement /> },
  { key: 'trust', label: 'Trust audit', hint: '6', render: () => <TrustAudit /> },
  { key: 'reservations', label: 'Reservations', hint: '7', render: () => <ReservationsOverview /> },
  { key: 'notiflog', label: 'Notifications log', hint: '8', render: () => <NotificationsLog /> },
  { key: 'frag', label: 'Fragmentation', hint: '9', render: () => <Fragmentation /> },
  { key: 'sysops', label: 'System & data', hint: '0', render: () => <SystemOps /> },
];

const NAVS: Record<Role, NavItem[]> = { customer: CUSTOMER_NAV, bank: BANK_NAV };

export default function App() {
  const [role, setRole] = useState<Role>('customer');
  const [screen, setScreen] = useState('dashboard');
  const [accountId, setAccountId] = useState('a1');
  const [theme, setTheme] = useState<'light' | 'dark'>(() =>
    (localStorage.getItem('lt-theme') as 'light' | 'dark') ?? 'light',
  );

  const { data: accounts } = usePoll(() => live().listAccounts(), 10000);
  const mine = accounts?.find((a) => a.accountId === accountId);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('lt-theme', theme);
  }, [theme]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement || e.target instanceof HTMLSelectElement) return;
      const idx = Number(e.key) === 0 ? 9 : Number(e.key) - 1;
      const nav = NAVS[role];
      if (idx >= 0 && idx < nav.length) {
        e.preventDefault();
        setScreen(nav[idx].key);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [role]);

  // role switch lands on that role's first screen
  const switchRole = (r: Role) => {
    setRole(r);
    setScreen(NAVS[r][0].key);
  };

  const nav = NAVS[role];
  const current = nav.find((n) => n.key === screen) ?? nav[0];

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-dot" aria-hidden />
          <span>Ledger Terminal</span>
        </div>

        <div className="role-switch" role="tablist" aria-label="Portal">
          <button
            className={role === 'customer' ? 'role-btn active' : 'role-btn'}
            onClick={() => switchRole('customer')}
          >
            Customer
          </button>
          <button
            className={role === 'bank' ? 'role-btn active' : 'role-btn'}
            onClick={() => switchRole('bank')}
          >
            Bank
          </button>
        </div>

        {role === 'customer' && accounts && accounts.length > 0 && (
          <div className="field" style={{ margin: 'var(--sp-md) 0' }}>
            <label htmlFor="acct-switch">Acting as</label>
            <select
              id="acct-switch"
              value={accountId}
              onChange={(e) => setAccountId(e.target.value)}
            >
              {accounts.map((a) => (
                <option key={a.accountId} value={a.accountId}>
                  {a.holderName}
                </option>
              ))}
            </select>
          </div>
        )}

        <nav style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {nav.map((n) => (
            <a
              key={n.key}
              href={`#${role}/${n.key}`}
              className={`nav-item ${current.key === n.key ? 'active' : ''}`}
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
          {mine && (
            <div className="section-note" style={{ fontSize: 10.5, lineHeight: 1.5 }}>
              {mine.holderName} · {mine.region}
              <br />
              KYC {mine.kycStatus} · {mine.status}
            </div>
          )}
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
      <main className="main" key={`${role}-${current.key}`}>
        {current.render({ accountId })}
      </main>
    </div>
  );
}
