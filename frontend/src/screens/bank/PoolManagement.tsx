import { useState } from 'react';
import { Section, StatusBadge, ErrorNote } from '../../components/ui';
import { SpringMoney } from '../../components/Springs';
import { ago, moneyINR } from '../../lib/format';
import { usePoll } from '../../lib/poll';
import { api } from '../../api/client';
import { mockApi } from '../../mock/server';
import { PageHeader } from './shared';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

export function PoolManagement() {
  const { data: pool, error } = usePoll(() => live().getAdminPool(), 2000);
  const { data: history } = usePoll(() => live().listPoolHistory(), 4000);
  const { data: queue } = usePoll(() => live().getLoanQueue(), 2000);
  const { data: accounts } = usePoll(() => live().listAccounts(), 10000);
  const [amount, setAmount] = useState('25000');
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null);

  const waiting = queue?.entries.filter((e) => e.status === 'WAITING_FOR_FUNDS') ?? [];
  const nameOf = (id: string) => accounts?.find((a) => a.accountId === id)?.holderName ?? id;

  const replenish = async () => {
    setMsg(null);
    const amt = Number(amount);
    if (!Number.isFinite(amt) || amt <= 0) {
      setMsg({ text: 'Enter a valid amount.', ok: false });
      return;
    }
    try {
      const updated = await live().replenishPool(amt);
      setMsg({ text: `AdminPoolReplenished +${moneyINR(amt)} · pool now ${moneyINR(updated.balance)} · waiting loans re-queued`, ok: true });
    } catch (e) {
      setMsg({ text: e instanceof Error ? e.message : String(e), ok: false });
    }
  };

  return (
    <>
      <PageHeader
        eyebrow="Loan Service · admin pool"
        title="Pool management"
        sub="The fund every loan draws from via the atomic Lua debit. Replenishing appends AdminPoolReplenished and re-queues every WAITING_FOR_FUNDS loan."
      />

      <section className="section">
        <div className="eyebrow">Pool balance</div>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 'var(--sp-xl)', flexWrap: 'wrap' }}>
          <SpringMoney value={pool?.balance ?? 0} className="mono balance-lg" />
          <div style={{ display: 'inline-flex', gap: 'var(--sp-xs)', alignItems: 'center' }}>
            <input
              aria-label="Replenishment amount"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              inputMode="decimal"
              style={{ width: 110, padding: '5px 10px', border: '1px solid var(--hairline-strong)', borderRadius: 'var(--r-sm)', background: 'var(--paper)', color: 'var(--ink)', fontFamily: 'var(--font-mono)', fontSize: 13 }}
            />
            <button className="btn btn-primary" onClick={replenish}>Replenish</button>
          </div>
        </div>
        {msg && <p className={msg.ok ? 'ok-text mono' : 'error-text mono'}>{msg.text}</p>}
        <ErrorNote error={error} />
      </section>

      <div className="grid-2">
        <Section
          title="Waiting for funds"
          note="cleared automatically on replenishment"
        >
          <table className="register">
            <thead>
              <tr><th>Loan</th><th>Customer</th><th className="num">Amount</th><th>Status</th></tr>
            </thead>
            <tbody>
              {waiting.map((e) => (
                <tr key={e.loanId}>
                  <td className="mono">{e.loanId.slice(0, 8)}…</td>
                  <td>{nameOf(e.accountId)}</td>
                  <td className="num">{moneyINR(e.amount)}</td>
                  <td><StatusBadge status={e.status} /></td>
                </tr>
              ))}
              {waiting.length === 0 && (
                <tr><td colSpan={4} style={{ color: 'var(--muted)' }}>No loans waiting. Pool covers the queue.</td></tr>
              )}
            </tbody>
          </table>
        </Section>

        <Section title="Replenishment history" note="GET /api/loans/pool/history · audit trail of AdminPoolReplenished">
          <table className="register">
            <thead>
              <tr><th>When</th><th className="num">Amount</th><th className="num">Balance after</th></tr>
            </thead>
            <tbody>
              {history?.map((h) => (
                <tr key={h.eventId} className="row-in">
                  <td className="mono" style={{ color: 'var(--muted)' }}>{ago(h.at)}</td>
                  <td className="num amount-credit">+{moneyINR(h.amount)}</td>
                  <td className="num">{moneyINR(h.balanceAfter)}</td>
                </tr>
              ))}
              {history && history.length === 0 && (
                <tr><td colSpan={3} style={{ color: 'var(--muted)' }}>No replenishments yet.</td></tr>
              )}
            </tbody>
          </table>
        </Section>
      </div>
    </>
  );
}
