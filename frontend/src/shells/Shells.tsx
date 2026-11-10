import { useEffect, useMemo, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { clearSession, getSession, type AuthRole } from '../auth';
import { mockApi } from '../mock/server';
import { api } from '../api/client';
import { isLive } from '../api/base';
import { usePoll } from '../lib/poll';

// customer screens
import { Dashboard } from '../screens/customer/Dashboard';
import { Statement } from '../screens/customer/Statement';
import { Transfer } from '../screens/customer/Transfer';
import { OfflinePayment } from '../screens/customer/OfflinePayment';
import { ApplyLoan } from '../screens/customer/ApplyLoan';
import { MyLoans } from '../screens/customer/MyLoans';
import { TrustScore } from '../screens/customer/TrustScore';
import { Notifications } from '../screens/customer/Notifications';
import { FraudAlerts } from '../screens/customer/FraudAlerts';
import { OpenAccount } from '../screens/customer/OpenAccount';

// bank screens
import { OpsDashboard } from '../screens/bank/OpsDashboard';
import { LoanQueue } from '../screens/bank/LoanQueue';
import { PoolManagement } from '../screens/bank/PoolManagement';
import { FraudReview } from '../screens/bank/FraudReview';
import { AccountsManagement } from '../screens/bank/AccountsManagement';
import { TrustAudit } from '../screens/bank/TrustAudit';
import { ReservationsOverview } from '../screens/bank/ReservationsOverview';
import { NotificationsLog } from '../screens/bank/NotificationsLog';
import { Fragmentation } from '../screens/bank/Fragmentation';
import { SystemOps } from '../screens/bank/SystemOps';

type Role = 'customer' | 'bank';

type NavItem = {
  key: string;
  label: string;
  hint: string;
  render: (accountId: string) => React.ReactNode;
};

const CUSTOMER_NAV: NavItem[] = [
  { key: 'dashboard', label: 'Accounts', hint: '1', render: (accountId) => <Dashboard accountId={accountId} /> },
  { key: 'open-account', label: 'Open account', hint: '0', render: () => <OpenAccount /> },
  { key: 'statement', label: 'Statement', hint: '2', render: (accountId) => <Statement accountId={accountId} /> },
  { key: 'transfer', label: 'Transfer', hint: '3', render: (accountId) => <Transfer accountId={accountId} /> },
  { key: 'offline', label: 'Offline payment', hint: '4', render: (accountId) => <OfflinePayment accountId={accountId} /> },
  { key: 'apply', label: 'Apply for loan', hint: '5', render: (accountId) => <ApplyLoan accountId={accountId} /> },
  { key: 'loans', label: 'My loans', hint: '6', render: (accountId) => <MyLoans accountId={accountId} /> },
  { key: 'trust', label: 'Trust score', hint: '7', render: (accountId) => <TrustScore accountId={accountId} /> },
  { key: 'alerts', label: 'Fraud alerts', hint: '8', render: (accountId) => <FraudAlerts accountId={accountId} /> },
  { key: 'notifications', label: 'Notifications', hint: '9', render: (accountId) => <Notifications accountId={accountId} /> },
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

const live = () => (isLive ? api : mockApi);

function PortalShell({ portal }: { portal: Role }) {
  const location = useLocation();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const session = getSession();
  const nav = NAVS[portal];
  const expectedRole: AuthRole = portal === 'bank' ? 'BANK' : 'CUSTOMER';

  const [theme, setTheme] = useState<'light' | 'dark'>(() => {
    const stored = localStorage.getItem('lt-theme');
    return stored === 'dark' || stored === 'light' ? stored : 'light';
  });

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    document.documentElement.dataset.portal = portal;
    localStorage.setItem('lt-theme', theme);
  }, [theme, portal]);

  const parts = location.pathname.split('/').filter(Boolean);
  const screenKey = parts[1] ?? HOME[portal];
  const current = nav.find((n) => n.key === screenKey) ?? nav[0];

  const { data: accounts } = usePoll(() => live().listAccounts(), 5000);
  const [pickedAccountId, setPickedAccountId] = useState<string>('');

  const accountId = useMemo(() => {
    const fromQuery = params.get('accountId');
    if (fromQuery) return fromQuery;
    if (pickedAccountId) return pickedAccountId;
    if (portal === 'customer') {
      const own = session?.accountId;
      if (own) return own;
    }
    return accounts?.[0]?.accountId ?? '';
  }, [accounts, params, pickedAccountId, portal, session?.accountId]);

  useEffect(() => {
    if (!session) return;
    if (!isLive && session.role !== expectedRole) {
      navigate(`/${session.role === 'BANK' ? 'bank' : 'customer'}/${HOME[session.role === 'BANK' ? 'bank' : 'customer']}`, {
        replace: true,
      });
    }
  }, [expectedRole, navigate, session]);

  if (!session) return <Navigate to="/login" replace />;
  if (isLive && session.role !== expectedRole) {
    return (
      <Navigate
        to={`/${session.role.toLowerCase()}/${HOME[session.role === 'BANK' ? 'bank' : 'customer']}`}
        replace
      />
    );
  }
  if (screenKey !== current.key) {
    return <Navigate to={`/${portal}/${current.key}`} replace />;
  }

  const actingAs = accounts?.find((a) => a.accountId === accountId);
  const roleLabel = portal === 'customer' ? 'Customer portal' : 'Bank operations';

  const signOut = () => {
    clearSession();
    navigate('/login', { replace: true });
  };

  return (
    <div className="shell portal-shell" data-portal={portal}>
      <aside className="sidebar">
        <Link to={`/${portal}/${HOME[portal]}`} className="brand portal-brand">
          <span className="brand-dot" aria-hidden />
          <span className="brand-stack">
            <span>Ledger Terminal</span>
            <small>{portal === 'customer' ? 'Customer' : 'Bank'}</small>
          </span>
        </Link>

        <div className="portal-mode-pill">
          <span className="portal-mode-dot" aria-hidden />
          {roleLabel}
        </div>

        {portal === 'bank' && accounts && accounts.length > 0 && (
          <div className="field sidebar-field">
            <label htmlFor="acct-switch">Acting as</label>
            <select
              id="acct-switch"
              value={accountId}
              onChange={(e) => setPickedAccountId(e.target.value)}
            >
              {accounts.map((a) => (
                <option key={a.accountId} value={a.accountId}>
                  {a.holderName} · {a.accountId}
                </option>
              ))}
            </select>
          </div>
        )}

        {portal === 'customer' && accountId && (
          <div className="acting-card">
            <span className="eyebrow">Signed in account</span>
            <strong>{actingAs?.holderName ?? 'New account'}</strong>
            <span className="mono">{accountId}{actingAs ? ` · ${actingAs.region}` : ''}</span>
            <span className="acting-badges">
              <span className="badge">{actingAs?.kycStatus ?? 'PENDING'}</span>
              <span className="badge">{actingAs?.status ?? 'ACTIVE'}</span>
            </span>
          </div>
        )}

        <nav className="portal-nav" aria-label={roleLabel}>
          {nav.map((n) => (
            <Link
              key={n.key}
              to={`/${portal}/${n.key}`}
              className={`nav-item ${current.key === n.key ? 'active' : ''}`}
              aria-current={current.key === n.key ? 'page' : undefined}
            >
              <span className="nav-label">{n.label}</span>
              <span className="nav-key">{n.hint}</span>
            </Link>
          ))}
        </nav>

        <div className="sidebar-foot">
          <div className="session-chip">
            <span className="session-avatar" aria-hidden>{session.displayName.slice(0, 1)}</span>
            <span>
              <strong>{session.displayName}</strong>
              <small>{isLive ? 'Gateway JWT session' : 'Demo session · offline data'}</small>
            </span>
          </div>
          <div className="sidebar-actions">
            <button
              className="btn btn-compact"
              type="button"
              onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}
              title="Toggle light / dark theme"
            >
              {theme === 'light' ? '◐ Dark' : '◑ Light'}
            </button>
            <button className="btn btn-compact" type="button" onClick={signOut}>
              Sign out
            </button>
          </div>
        </div>
      </aside>

      <main className="main" key={`${portal}-${current.key}`}>
        {current.render(accountId)}
      </main>
    </div>
  );
}

export function CustomerShell() {
  return <PortalShell portal="customer" />;
}

export function BankShell() {
  return <PortalShell portal="bank" />;
}

export { live };
