import { useState } from 'react';
import type { FraudCase } from '../../api/contract';
import { Section, StatusBadge, ErrorNote } from '../../components/ui';
import { ago } from '../../lib/format';
import { usePoll } from '../../lib/poll';
import { api } from '../../api/client';
import { mockApi } from '../../mock/server';
import { PageHeader } from './shared';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

function CaseActions({ c, onDone }: { c: FraudCase; onDone: (msg: string, ok: boolean) => void }) {
  const [busy, setBusy] = useState(false);
  const act = async (status: 'REVIEWED' | 'DISMISSED') => {
    setBusy(true);
    try {
      await live().reviewFraudCase(c.caseId, status);
      onDone(`Case ${c.caseId.slice(0, 8)}… → ${status.toLowerCase()}`, true);
    } catch (e) {
      onDone(e instanceof Error ? e.message : String(e), false);
    } finally {
      setBusy(false);
    }
  };
  return (
    <span style={{ display: 'inline-flex', gap: 'var(--sp-xs)' }}>
      <button className="btn" disabled={busy} onClick={() => act('REVIEWED')}>Mark reviewed</button>
      <button className="btn" disabled={busy} onClick={() => act('DISMISSED')}>Dismiss</button>
    </span>
  );
}

export function FraudReview() {
  const { data: cases, error } = usePoll(() => live().listFraudCases(), 4000);
  const { data: accounts } = usePoll(() => live().listAccounts(), 10000);
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null);
  const nameOf = (id: string) => accounts?.find((a) => a.accountId === id)?.holderName ?? id;

  const sorted = [...(cases ?? [])].sort((a, b) => {
    const rank = (s: FraudCase['status']) => (s === 'OPEN' ? 0 : 1);
    return rank(a.status) - rank(b.status) || b.flaggedAt.localeCompare(a.flaggedAt);
  });

  return (
    <>
      <PageHeader
        eyebrow="Fraud Detection Service"
        title="Fraud review"
        sub="Every flag across all customers, with the rule and triggering event that fired it. Review or dismiss appends FraudCaseReviewed and clears the customer-facing alert."
      />

      {msg && <p className={msg.ok ? 'ok-text mono' : 'error-text mono'}>{msg.text}</p>}

      <Section title="Cases" note="GET /api/fraud/cases · OPEN first">
        <table className="register">
          <thead>
            <tr><th>Rule</th><th>Customer</th><th>Trigger</th><th className="num">Risk</th><th>Status</th><th>Flagged</th><th>Decision</th></tr>
          </thead>
          <tbody>
            {sorted.map((c) => (
              <tr key={c.caseId}>
                <td className="mono">{c.rule}</td>
                <td>{nameOf(c.accountId)}</td>
                <td><span className="badge">{c.triggeredByEvent}</span></td>
                <td className="num">{c.riskScore ?? '—'}</td>
                <td><StatusBadge status={c.status} /></td>
                <td className="mono" style={{ color: 'var(--muted)' }}>{ago(c.flaggedAt)}</td>
                <td>
                  {c.status === 'OPEN'
                    ? <CaseActions c={c} onDone={(text, ok) => setMsg({ text, ok })} />
                    : <span className="section-note">closed {c.reviewedAt ? ago(c.reviewedAt) : ''}</span>}
                </td>
              </tr>
            ))}
            {sorted.length === 0 && (
              <tr><td colSpan={7} style={{ color: 'var(--muted)' }}>No fraud cases. All events clean.</td></tr>
            )}
          </tbody>
        </table>
        <ErrorNote error={error} />
      </Section>
    </>
  );
}
