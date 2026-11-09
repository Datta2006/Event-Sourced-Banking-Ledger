import { useState } from 'react';
import { PageHeader } from './shared';
import { api } from '../../api/client';
import { mockApi } from '../../mock/server';
import { useNavigate } from 'react-router-dom';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

/** Simple IP selector for demo (in production would auto-detect) */
const REGION_IPS = {
  APAC: '49.37.10.10',
  EU: '85.214.132.1',
  AMER: '8.8.8.8',
} as const;

export function OpenAccount() {
  const navigate = useNavigate();
  const [holderName, setHolderName] = useState('');
  const [region, setRegion] = useState<'APAC' | 'EU' | 'AMER'>('APAC');
  const [initialDeposit, setInitialDeposit] = useState(10000);
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  const open = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(undefined);
    try {
      const result = await live().openAccount({
        holderName,
        registrationIp: REGION_IPS[region],
        initialDeposit,
      });
      // Navigate to the new account's dashboard
      navigate(`/customer/dashboard?accountId=${result.accountId}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to open account');
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader
        eyebrow="Customer portal · open a new account"
        title="Open Account"
        sub="Create your first account with an initial deposit"
      />

      <section className="section">
        <form onSubmit={open} style={{ maxWidth: 480 }}>
          <div className="field">
            <label htmlFor="holderName">Full Name</label>
            <input
              id="holderName"
              placeholder="e.g., Raj Patel"
              value={holderName}
              onChange={(e) => setHolderName(e.target.value)}
              required
            />
          </div>

          <div className="field" style={{ marginTop: 'var(--sp-lg)' }}>
            <label htmlFor="region">Region (shard)</label>
            <select
              id="region"
              value={region}
              onChange={(e) => setRegion(e.target.value as 'APAC' | 'EU' | 'AMER')}
            >
              <option value="APAC">APAC (Asia-Pacific)</option>
              <option value="EU">EU (Europe)</option>
              <option value="AMER">AMER (Americas)</option>
            </select>
            <div style={{ fontSize: 11, color: 'var(--muted)', marginTop: 4 }}>
              Region resolved from IP address - accounts shard by region
            </div>
          </div>

          <div className="field" style={{ marginTop: 'var(--sp-lg)' }}>
            <label htmlFor="initialDeposit">Initial Deposit (₹)</label>
            <input
              id="initialDeposit"
              type="number"
              min={100}
              step={100}
              value={initialDeposit}
              onChange={(e) => setInitialDeposit(Number(e.target.value))}
              required
            />
          </div>

          {error && <p className="error-text mono" style={{ marginTop: 'var(--sp-lg)' }}>⚠ {error}</p>}

          <button
            className="btn btn-primary"
            type="submit"
            disabled={busy || !holderName || initialDeposit < 100}
            style={{ width: '100%', marginTop: 'var(--sp-lg)' }}
          >
            {busy ? 'Opening account…' : 'Open Account'}
          </button>
        </form>
      </section>
    </>
  );
}