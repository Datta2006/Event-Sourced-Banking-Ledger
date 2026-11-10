import { useState } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { clearSession, DEMO_LOGIN, getSession, login, setSession, type AuthRole } from './auth';

const HOME = { CUSTOMER: 'dashboard', BANK: 'ops' } as const;

const ROLE_COPY: Record<AuthRole, { title: string; body: string; cta: string }> = {
  CUSTOMER: {
    title: 'Customer',
    body: 'Accounts, statements, transfers, offline payments, loans and trust score.',
    cta: 'Continue as customer',
  },
  BANK: {
    title: 'Bank operations',
    body: 'Loan queue, admin pool, fraud review, accounts, reservations and system health.',
    cta: 'Continue as bank',
  },
};

export function LoginPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  const existing = getSession();

  if (existing && !params.get('switch')) {
    return <Navigate to={`/${existing.role.toLowerCase()}/${HOME[existing.role]}`} replace />;
  }

  const enter = async (nextUsername: string, nextPassword: string) => {
    setBusy(true);
    setError(undefined);
    try {
      const session = await login(nextUsername.trim(), nextPassword);
      setSession(session);
      navigate(`/${session.role.toLowerCase()}/${HOME[session.role]}`, { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    } finally {
      setBusy(false);
    }
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    await enter(username, password);
  };

  const fill = (role: AuthRole) => {
    setUsername(DEMO_LOGIN[role].username);
    setPassword(DEMO_LOGIN[role].password);
    setError(undefined);
  };

  return (
    <div className="login-page">
      <header className="login-topbar">
        <div className="brand">
          <span className="brand-dot" aria-hidden />
          <span>Ledger Terminal</span>
        </div>
        <span className="login-status"><span className="live-dot">offline demo data</span></span>
      </header>

      <main className="login-card">
        <section className="login-hero">
          <div className="eyebrow">Two portals · one event-sourced bank</div>
          <h1>Sign in to the ledger.</h1>
          <p>
            A course demo of an event-sourced banking system. Every table is seeded with fixed
            data — click around freely, nothing depends on a live customer.
          </p>

          <div className="login-role-grid">
            {(Object.keys(ROLE_COPY) as AuthRole[]).map((role) => (
              <button
                key={role}
                type="button"
                className={`login-role ${role === 'BANK' ? 'bank' : 'customer'}`}
                disabled={busy}
                onClick={() => void enter(DEMO_LOGIN[role].username, DEMO_LOGIN[role].password)}
              >
                <span className="login-role-head">
                  <span className={`login-role-dot ${role === 'BANK' ? 'bank' : ''}`} aria-hidden />
                  <span>{ROLE_COPY[role].title}</span>
                </span>
                <span className="login-role-body">{ROLE_COPY[role].body}</span>
                <span className="login-role-cta">{busy ? 'Opening…' : ROLE_COPY[role].cta} →</span>
              </button>
            ))}
          </div>

          <div className="login-hint">
            <span>Course demo credentials</span>
            <code>customer / customer123</code>
            <code>bank / bank123</code>
          </div>
        </section>

        <section className="login-form-panel">
          <div className="eyebrow">Credentials</div>
          <h2>Manual sign in</h2>
          <form onSubmit={submit}>
            <div className="field">
              <label htmlFor="username">Username</label>
              <input
                id="username"
                autoComplete="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="customer"
              />
            </div>
            <div className="field">
              <label htmlFor="password">Password</label>
              <input
                id="password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
              />
            </div>
            {error && <p className="error-text mono">⚠ {error}</p>}
            <button className="btn btn-primary login-submit" type="submit" disabled={busy || !username || !password}>
              {busy ? 'Signing in…' : 'Sign in'}
            </button>
          </form>

          <div className="login-fill-row">
            <button className="btn btn-compact" type="button" onClick={() => fill('CUSTOMER')}>
              Fill customer
            </button>
            <button className="btn btn-compact" type="button" onClick={() => fill('BANK')}>
              Fill bank
            </button>
          </div>

          <div className="login-demo-note">
            <strong>Customer account</strong>
            <span className="mono">a01 · Aarav Sharma</span>
            <span>Fixed seed data across APAC, EU and AMER shards.</span>
          </div>

          {existing && (
            <button
              className="btn login-signout"
              type="button"
              onClick={() => {
                clearSession();
                navigate('/login', { replace: true });
              }}
            >
              Sign out of {existing.displayName}
            </button>
          )}
        </section>
      </main>
    </div>
  );
}
