import { useState } from 'react';
import type { LoanRequestView } from '../../api/contract';
import { TIER_NAMES } from '../../api/contract';
import { Section, StatusBadge, ErrorNote } from '../../components/ui';
import { ago, moneyINR } from '../../lib/format';
import { usePoll } from '../../lib/poll';
import { api } from '../../api/client';
import { mockApi } from '../../mock/server';
import { PageHeader, NoAccount, useMe } from './shared';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

function RepayButton({ loan, onDone }: { loan: LoanRequestView; onDone: (msg: string, ok: boolean) => void }) {
  const [amount, setAmount] = useState(String(loan.amount));
  const [busy, setBusy] = useState(false);
  const repay = async () => {
    setBusy(true);
    try {
      await live().repayLoan(loan.loanId, Number(amount));
      onDone(`Repaid ${moneyINR(Number(amount))} on ${loan.loanId.slice(0, 8)}… · TrustScoreChanged published`, true);
    } catch (e) {
      onDone(e instanceof Error ? e.message : String(e), false);
    } finally {
      setBusy(false);
    }
  };
  return (
    <span style={{ display: 'inline-flex', gap: 'var(--sp-xs)', alignItems: 'center' }}>
      <input
        aria-label="Repayment amount"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        inputMode="decimal"
        style={{ width: 90, padding: '3px 8px', border: '1px solid var(--hairline-strong)', borderRadius: 'var(--r-sm)', background: 'var(--paper)', color: 'var(--ink)', fontFamily: 'var(--font-mono)', fontSize: 12 }}
      />
      <button className="btn" disabled={busy} onClick={repay}>Repay</button>
    </span>
  );
}

export function MyLoans({ accountId }: { accountId: string }) {
  const { data: me } = useMe(accountId);
  const { data: loans, error, } = usePoll(() => live().listLoans(accountId), 3000, [accountId]);
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null);

  return (
    <>
      <PageHeader
        eyebrow="Customer portal · loan service"
        title="My loans"
        sub="Every request you've made: queued position resolves by tier then arrival. Repayment is appended as LoanRepaid and raises your trust score."
      />
      {!me && <NoAccount accountId={accountId} />}

      {msg && <p className={msg.ok ? 'ok-text mono' : 'error-text mono'}>{msg.text}</p>}

      <Section title="Loan history" note="GET /api/loans?accountId=… · newest first">
        <table className="register">
          <thead>
            <tr><th>Loan</th><th className="num">Amount</th><th>Tier at request</th><th>Status</th><th>Requested</th><th></th></tr>
          </thead>
          <tbody>
            {loans?.map((l) => (
              <tr key={l.loanId}>
                <td className="mono">{l.loanId.slice(0, 8)}…</td>
                <td className="num">{moneyINR(l.amount)}</td>
                <td>
                  <span className={`badge ${l.trustTier >= 2 ? 'green' : l.trustTier === 1 ? 'blue' : 'amber'}`}>
                    {TIER_NAMES[l.trustTier as 0 | 1 | 2 | 3]}
                  </span>
                </td>
                <td><StatusBadge status={l.status} /></td>
                <td className="mono" style={{ color: 'var(--muted)' }}>{ago(l.requestedAt)}</td>
                <td style={{ textAlign: 'right' }}>
                  {l.status === 'DISBURSED' && <RepayButton loan={l} onDone={(text, ok) => setMsg({ text, ok })} />}
                </td>
              </tr>
            ))}
            {loans && loans.length === 0 && (
              <tr><td colSpan={6} style={{ color: 'var(--muted)' }}>No loans yet — apply from the sidebar.</td></tr>
            )}
          </tbody>
        </table>
        <ErrorNote error={error} />
      </Section>
    </>
  );
}
