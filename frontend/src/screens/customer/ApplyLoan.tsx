import { useState } from 'react';
import { TIER_NAMES } from '../../api/contract';
import { Section, ErrorNote } from '../../components/ui';
import { moneyINR } from '../../lib/format';
import { api } from '../../api/client';
import { mockApi } from '../../mock/server';
import { PageHeader, NoAccount, useMe } from './shared';
import { useTrustTier } from './useTrustTier';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

export function ApplyLoan({ accountId }: { accountId: string }) {
  const { data: me } = useMe(accountId);
  const { data: trust } = useTrustTier(accountId);
  const [amount, setAmount] = useState('10000');
  const [purpose, setPurpose] = useState('');
  const [queued, setQueued] = useState<{ loanId: string; amount: number; tier: number } | null>(null);
  const [err, setErr] = useState<string | undefined>();

  const apply = async () => {
    setErr(undefined);
    const amt = Number(amount);
    if (!Number.isFinite(amt) || amt <= 0) {
      setErr('Enter a valid amount.');
      return;
    }
    try {
      const loan = await live().requestLoan(accountId, amt, purpose || undefined);
      setQueued({ loanId: loan.loanId, amount: loan.amount, tier: loan.trustTier });
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <>
      <PageHeader
        eyebrow="Customer portal · loan service"
        title="Apply for a loan"
        sub="Your request lands on the Valkey priority queue: tier first, arrival time second. The worker runs an atomic pop-and-debit of the admin pool — if the pool can't cover it, you wait for replenishment, never a double-approval."
      />
      {!me && <NoAccount accountId={accountId} />}

      <div className="grid-2" style={{ marginTop: 'var(--sp-xl)' }}>
        <section className="panel">
          <div className="panel-head">
            <span className="panel-title">New request</span>
            <span className="mono" style={{ fontSize: 10, color: 'var(--muted)' }}>POST /api/loans</span>
          </div>
          <div className="panel-body" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-lg)' }}>
            <div className="field">
              <label htmlFor="amt">Amount (₹)</label>
              <input id="amt" value={amount} onChange={(e) => setAmount(e.target.value)} inputMode="decimal" />
            </div>
            <div className="field">
              <label htmlFor="purpose">Purpose (optional)</label>
              <input id="purpose" value={purpose} onChange={(e) => setPurpose(e.target.value)} placeholder="e.g. home renovation" />
            </div>
            <button className="btn btn-primary" onClick={apply} disabled={!me}>
              Queue request
            </button>
            <ErrorNote error={err} />
          </div>
        </section>

        <section className="panel">
          <div className="panel-head">
            <span className="panel-title">Where you'll queue</span>
          </div>
          <div className="panel-body">
            {trust ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-lg)' }}>
                <div style={{ display: 'flex', alignItems: 'baseline', gap: 'var(--sp-lg)' }}>
                  <span className="mono balance-lg">{trust.score}</span>
                  <span className={`badge ${trust.tier >= 2 ? 'green' : trust.tier === 1 ? 'blue' : 'amber'}`}>
                    tier {trust.tier} · {TIER_NAMES[trust.tier]}
                  </span>
                </div>
                <p style={{ color: 'var(--muted)', fontSize: 13, margin: 0 }}>
                  Tier is snapshotted from the trust-score feed when you apply — deposits and repayments
                  raise it, defaults sink it. Higher tiers jump the queue; within a tier it's first-come,
                  first-served.
                </p>
              </div>
            ) : (
              <p style={{ color: 'var(--muted)', fontSize: 13 }}>Loading trust score…</p>
            )}
          </div>
        </section>
      </div>

      {queued && (
        <Section title="Request queued" note="LoanRequested appended · status QUEUED">
          <p className="ok-text mono">
            {queued.loanId.slice(0, 8)}… · {moneyINR(queued.amount)} · tier {queued.tier} ({TIER_NAMES[queued.tier as 0 | 1 | 2 | 3]}) —
            watch its progress under “My loans”.
          </p>
        </Section>
      )}
    </>
  );
}
