import { useEffect, useRef, useState } from 'react';
import { Navigate, useNavigate, useParams } from 'react-router-dom';
import { type AuthRole } from '../auth';
import { clearSession, getSession } from '../auth';
import { mockApi } from '../mock/server';
import { api } from '../api/client';
import { isLive } from '../api/base';
import { usePoll } from '../lib/poll';

type Role = 'customer' | 'bank';

type NavItem = {
  key: string;
  label: string;
  hint: string;
};

const CUSTOMER_NAV: NavItem[] = [
  { key: 'dashboard', label: 'Accounts', hint: '1' },
  { key: 'open-account', label: 'Open account', hint: '0' },
  { key: 'statement', label: 'Statement', hint: '2' },
  { key: 'transfer', label: 'Transfer', hint: '3' },
  { key: 'offline', label: 'Offline payment', hint: '4' },
  { key: 'apply', label: 'Apply for loan', hint: '5' },
  { key: 'loans', label: 'My loans', hint: '6' },
  { key: 'trust', label: 'Trust score', hint: '7' },
  { key: 'alerts', label: 'Fraud alerts', hint: '8' },
  { key: 'notifications', label: 'Notifications', hint: '9' },
];

const BANK_NAV: NavItem[] = [
  { key: 'ops', label: 'Ops dashboard', hint: '1' },
  { key: 'queue', label: 'Loan queue', hint: '2' },
  { key: 'pool', label: 'Pool management', hint: '3' },
  { key: 'fraud', label: 'Fraud review', hint: '4' },
  { key: 'accounts', label: 'Accounts', hint: '5' },
  { key: 'trust', label: 'Trust audit', hint: '6' },
  { key: 'reservations', label: 'Reservations', hint: '7' },
  { key: 'notiflog', label: 'Notifications log', hint: '8' },
  { key: 'frag', label: 'Fragmentation', hint: '9' },
  { key: 'sysops', label: 'System & data', hint: '0' },
];

const HOME: Record<Role, string> = { customer: 'dashboard', bank: 'ops' };

type Screen = React.ReactNode;

function CustomerShell() {
  return <CustomerShellInner />;
}

function CustomerShellInner() {
  const params = useParams<{ role?: string; screen?: string }>();
  const navigate = useNavigate();
  const session = getSession();
  const themeRef = useRef<'light' | 'dark'>('light');
  const [theme, setTheme] = useState<'light' | 'dark'>(() =>
    (localStorage.getItem('lt-theme') as 'light' | 'dark') ?? 'light',
  );

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('lt-theme', theme);
  }, [theme]);

  const roleParam: Role | undefined = params.role === 'customer' ? 'customer' : undefined;
  const expectedRole: AuthRole | undefined =
    params.role === 'bank' ? 'BANK' : params.role === 'customer' ? 'CUSTOMER' : undefined;

  const role: Role = isLive && session ? (session.role === 'BANK' ? 'bank' : 'customer') : roleParam ?? 'customer';
  const nav = CUSTOMER_NAV;
  const current = nav.find((n) => n.key === params.screen) ?? nav[0];

  if (!session) {
    return <Navigate to="/login" replace />;
  }
  if (isLive && expectedRole && session && session.role !== expectedRole) {
    return <Navigate to={`/${session.role.toLowerCase()}/${HOME[session.role === 'BANK' ? 'bank' : 'customer']}`} replace />;
  }
  if (!params.role || !params.screen) {
    return <Navigate to={`/${role}/${HOME[role]}`} replace />;
  }

  const { data: accounts } = usePoll(() => live().listAccounts(), 3000);
  const [accountId, setAccountId] = useState<string>('');
  useEffect(() => {
    if (!accounts || accounts.length === 0) return;
    if (!accountId) {
      setAccountId(accounts[0].accountId);
    }
    void accounts;
  }, [accounts]);

  const switchToBank = () => {
    navigate('/login?switch=1');
  };

  return (
    <div className="customer-shell">
      <header className="cs-topbar">
        <div className="cs-brand">
          <span className="brand-dot" aria-hidden />
          <span>Ledger Terminal · Customer</span>
        </div>
        <nav className="cs-nav">
          {nav.map((n) => (
            <Link
              key={n.key}
              to={`/customer/${n.key}`}
              className={`cs-nav-item ${current.key === n.key ? 'cs-active' : ''}`}
            >
              <span>{n.label}</span>
              <span className="nav-key">{n.hint}</span>
            </Link>
          ))}
        </nav>
        <button className="btn cs-logout" onClick={() => { clearSession(); navigate('/login', { replace: true }); }}>
          Sign out
        </button>
      </header>
      <div className="cs-account">
        <label htmlFor="acct-switch">Acting as</label>
        <select
          id="acct-switch"
          value={accountId}
          onChange={(e) => setAccountId(e.target.value)}
        >
          {(accounts ?? []).map((a) => (
            <option key={a.accountId} value={a.accountId}>
              {a.holderName}
            </option>
          ))}
        </select>
      </div>
      <main className="cs-main">
        {current.render({ accountId: accountId })}
      </main>
    </div>
  );
}

function BankShell() {
  return <BankShellInner />;
}

function BankShellInner() {
  const params = useParams<{ role?: string; screen?: string }>();
  const navigate = useNavigate();
  const session = getSession();
  const themeRef = useRef<'light' | 'dark'>('light');
  const [theme, setTheme] = useState<'light' | 'dark'>(() =>
    (localStorage.getItem('lt-theme') as 'light' | 'dark') ?? 'light',
  );

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('lt-theme', theme);
  }, [theme]);

  const roleParam: Role | undefined = params.role === 'bank' ? 'bank' : undefined;
  const expectedRole: AuthRole | undefined =
    params.role === 'bank' ? 'BANK' : params.role === 'customer' ? 'CUSTOMER' : undefined;

  const role: Role = isLive && session ? (session.role === 'BANK' ? 'bank' : 'customer') : roleParam ?? 'bank';
  const nav = BANK_NAV;
  const current = nav.find((n) => n.key === params.screen) ?? nav[0];

  if (!session) {
    return <Navigate to="/login" replace />;
  }
  if (isLive && expectedRole && session && session.role !== expectedRole) {
    return <Navigate to={`/${session.role.toLowerCase()}/${HOME[session.role === 'BANK' ? 'bank' : 'customer']}`} replace />;
  }
  if (!params.role || !params.screen) {
    return <Navigate to={`/${role}/${HOME[role]}`} replace />;
  }

  const { data: accounts } = usePoll(() => live().listAccounts(), 3000);
  const [accountId, setAccountId] = useState<string>('');
  useEffect(() => {
    if (!accounts || accounts.length === 0) return;
    if (!accountId) {
      setAccountId(accounts[0].accountId);
    }
    void accounts;
  }, [accounts]);

  const switchToCustomer = () => {
    navigate('/login?switch=1');
  };

  return (
    <div className="bank-shell">
      <header className="bs-topbar">
        <div className="bs-brand">
          <span className="brand-dot" aria-hidden />
          <span>Ledger Terminal · Bank</span>
        </div>
        <nav className="bs-nav">
          {nav.map((n) => (
            <Link
              key={n.key}
              to={`/bank/${n.key}`}
              className={`cs-nav-item ${current.key === n.key ? 'bs-active' : ''}`}
            >
              <span>{n.label}</span>
              <span className="nav-key">{n.hint}</span>
            </Link>
          ))}
        </nav>
        <button className="btn bs-logout" onClick={() => { clearSession(); navigate('/login', { replace: true }); }}>
          Sign out
        </button>
      </header>
      <div className="cs-account">
        <label htmlFor="acct-switch">Acting as</label>
        <select
          id="acct-switch"
          value={accountId}
          onChange={(e) => setAccountId(e.target.value)}
        >
          {(accounts ?? []).map((a) => (
            <option key={a.accountId} value={a.accountId}>
              {a.holderName}
            </option>
          ))}
        </select>
      </div>
      <main className="bs-main">
        {current.render({ accountId: accountId })}
      </main>
    </div>
  );
}

function live() {
  return isLive ? api : mockApi;
}

export { CustomerShell, BankShell, live };
