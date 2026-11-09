import { useEffect, useState } from 'react';
import {
  BrowserRouter,
  Link,
  Navigate,
  Route,
  Routes,
  useNavigate,
  useParams,
} from 'react-router-dom';
import { api } from './api/client';
import { isLive } from './api/base';
import { mockApi } from './mock/server';
import { usePoll } from './lib/poll';
import { clearSession, getSession, type AuthRole } from './auth';
import { LoginPage } from './LoginPage';

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
import { OpenAccount } from './screens/customer/OpenAccount';
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

const live = () => (isLive ? api : mockApi);

type NavItem = {
  key: string;
  label: string;
  hint: string;
  render: (ctx: { accountId: string }) => React.ReactNode;
};

const CUSTOMER_NAV: NavItem[] = [
  { key: 'dashboard', label: 'Accounts', hint: '1', render: ({ accountId }) => <Dashboard accountId={accountId} /> },
  { key: 'open-account', label: 'Open account', hint: '0', render: ({ accountId }) => <OpenAccount /> },
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
const HOME: Record<Role, string> = { customer: 'dashboard', bank: 'ops' };

export default function App() {
  const [theme, setTheme] = useState<'light' | 'dark'>(() =>
    (localStorage.getItem('lt-theme') as 'light' | 'dark') ?? 'light',
  );

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('lt-theme', theme);
  }, [theme]);

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/customer/dashboard" replace />} />
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/:role/:screen"
          element={
            <Portal
              theme={theme}
              toggleTheme={() => setTheme(theme === 'light' ? 'dark' : 'light')}
            />
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

function Portal({ theme, toggleTheme }: { theme: 'light' | 'dark'; toggleTheme: () => void }) {
  const params = useParams<{ role: string; screen: string }>();
  const navigate = useNavigate();
  const session = getSession();

  const roleParam: Role | undefined =
    params.role === 'customer' || params.role === 'bank' ? params.role : undefined;
  const expectedRole: AuthRole | undefined =
    roleParam === 'bank' ? 'BANK' : roleParam === 'customer' ? 'CUSTOMER' : undefined;

  const role: Role = isLive && session ? (session.role === 'BANK' ? 'bank' : 'customer') : roleParam ?? 'customer';
  const nav = NAVS[role];
  const current = nav.find((n) => n.key === params.screen) ?? nav[0];

  const [accountId, setAccountId] = useState<string>(
    isLive ? session?.accountId ?? '' : 'a1',
  );
  const { data: accounts } = usePoll(() => live().listAccounts(), 10000);

  useEffect(() => {
    if (!isLive || !accounts || accounts.length === 0) return;
    if (!accounts.some((a) => a.accountId === accountId)) {
      const preferred = session?.accountId
        && accounts.some((a) => a.accountId === session.accountId)
        ? session.accountId
        : accounts[0].accountId;
      setAccountId(preferred);
    }
  }, [accounts, accountId, session?.accountId]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement || e.target instanceof HTMLSelectElement) return;
      const keyNum = Number(e.key);
      if (isNaN(keyNum) && e.key !== '0') return;
      let idx: number;
      if (keyNum === 0) {
        // Find the item with hint '0' for this role
        idx = nav.findIndex(n => n.hint === '0');
        if (idx < 0) return;
      } else if (keyNum >= 1 && keyNum <= 9) {
        idx = nav.findIndex(n => n.hint === String(keyNum));
        if (idx < 0) return;
      } else {
        return;
      }
      e.preventDefault();
      navigate(`/${role}/${nav[idx].key}`);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [role, nav, navigate]);

  if (isLive && !session) {
    return <Navigate to="/login" replace />;
  }
  if (isLive && expectedRole && session && session.role !== expectedRole) {
    return <Navigate to={`/${session.role.toLowerCase()}/${HOME[session.role === 'BANK' ? 'bank' : 'customer']}`} replace />;
  }
  if (!roleParam) {
    return <Navigate to={`/${role}/${HOME[role]}`} replace />;
  }
  if (params.screen !== current.key) {
    return <Navigate to={`/${role}/${current.key}`} replace />;
  }

  const mine = accounts?.find((a) => a.accountId === accountId);
  const switchPortal = () => {
    if (isLive) {
      clearSession();
      navigate('/login?switch=1');
      return;
    }
    const next: Role = role === 'customer' ? 'bank' : 'customer';
    navigate(`/${next}/${HOME[next]}`);
  };

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-dot" aria-hidden />
          <span>Ledger Terminal</span>
        </div>

        <div className="role-switch" role="tablist" aria-label="Portal">
          <Link
            className={role === 'customer' ? 'role-btn active' : 'role-btn'}
            to={isLive ? '/login?switch=1' : '/customer/dashboard'}
            onClick={isLive ? (e) => { e.preventDefault(); switchPortal(); } : undefined}
          >
            Customer
          </Link>
          <Link
            className={role === 'bank' ? 'role-btn active' : 'role-btn'}
            to={isLive ? '/login?switch=1' : '/bank/ops'}
            onClick={isLive ? (e) => { e.preventDefault(); switchPortal(); } : undefined}
          >
            Bank
          </Link>
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
            <Link
              key={n.key}
              to={`/${role}/${n.key}`}
              className={`nav-item ${current.key === n.key ? 'active' : ''}`}
            >
              <span className="nav-label">{n.label}</span>
              <span className="nav-key">{n.hint}</span>
            </Link>
          ))}
        </nav>

        <div style={{ marginTop: 'auto', display: 'flex', flexDirection: 'column', gap: 'var(--sp-sm)' }}>
          {session && (
            <div className="section-note" style={{ fontSize: 10.5, lineHeight: 1.5 }}>
              {session.displayName}
              <br />
              {session.role} · JWT session
            </div>
          )}
          {mine && (
            <div className="section-note" style={{ fontSize: 10.5, lineHeight: 1.5 }}>
              {mine.holderName} · {mine.region}
              <br />
              KYC {mine.kycStatus} · {mine.status}
            </div>
          )}
          <button className="btn" onClick={toggleTheme} title="Toggle console theme">
            {theme === 'light' ? '◐ Console' : '◑ Paper'}
          </button>
          {isLive ? (
            <button
              className="btn"
              onClick={() => {
                clearSession();
                navigate('/login', { replace: true });
              }}
            >
              Sign out
            </button>
          ) : (
            <div className="section-note" style={{ fontSize: 10.5, lineHeight: 1.5 }}>
              Mock transport ·<br />
              set VITE_API_BASE for the live bank
            </div>
          )}
        </div>
      </aside>
      <main className="main" key={`${role}-${current.key}`}>
        {current.render({ accountId })}
      </main>
    </div>
  );
}
