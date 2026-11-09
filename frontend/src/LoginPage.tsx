import { useState } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { clearSession, getSession, login, setSession } from './auth';
import { isLive } from './api/base';

const HOME = { CUSTOMER: 'dashboard', BANK: 'ops' } as const;

export function LoginPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  const existing = getSession();

  if (!isLive) {
    return <Navigate to="/customer/dashboard" replace />;
  }
  if (existing && !params.get('switch')) {
    return <Navigate to={`/${existing.role.toLowerCase()}/${HOME[existing.role]}`} replace />;
  }

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(undefined);
    try {
      const session = await login(username.trim(), password);
      setSession(session);
      navigate(`/${session.role.toLowerCase()}/${HOME[session.role]}`, { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    } finally {
      setBusy(false);
    }
  };

  const fill = (role: 'CUSTOMER' | 'BANK') => {
    setUsername(role === 'CUSTOMER' ? 'customer' : 'bank');
    setPassword(role === 'CUSTOMER' ? 'customer123' : 'bank123');
    setError(undefined);
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        padding: 24,
        background: 'var(--bg)',
      }}
    >
      <div className="section" style={{ width: 'min(460px, 100%)' }}>
        <div className="brand" style={{ marginBottom: 'var(--sp-lg)' }}>
          <span className="brand-dot" aria-hidden />
          <span>Ledger Terminal</span>
        </div>
        <div className="eyebrow">Two portals · one event-sourced bank</div>
        <h2 className="section-title" style={{ marginTop: 6 }}>
          Sign in
        </h2>
        <div className="section-note">
          The API gateway issues a signed JWT; role decides customer or bank access.
        </div>

        <form onSubmit={submit} style={{ marginTop: 'var(--sp-lg)' }}>
          <div className="field">
            <label htmlFor="username">Username</label>
            <input
              id="username"
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
          </div>
          <div className="field" style={{ marginTop: 'var(--sp-md)' }}>
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          {error && <p className="error-text mono">⚠ {error}</p>}
          <button
            className="btn btn-primary"
            type="submit"
            disabled={busy || !username || !password}
            style={{ width: '100%', marginTop: 'var(--sp-lg)' }}
          >
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <div style={{ display: 'flex', gap: 8, marginTop: 'var(--sp-lg)' }}>
          <button className="btn" type="button" onClick={() => fill('CUSTOMER')}>
            Fill customer demo
          </button>
          <button className="btn" type="button" onClick={() => fill('BANK')}>
            Fill bank demo
          </button>
        </div>

        {existing && (
          <button
            className="btn"
            style={{ width: '100%', marginTop: 8 }}
            onClick={() => {
              clearSession();
              navigate('/login', { replace: true });
            }}
          >
            Sign out of {existing.displayName}
          </button>
        )}
      </div>
    </div>
  );
}
