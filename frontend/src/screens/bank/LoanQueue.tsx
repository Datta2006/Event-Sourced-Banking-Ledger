import { useLayoutEffect, useRef, useState } from 'react';
import type { LoanQueueEntry } from '../../api/contract';
import { TIER_NAMES } from '../../api/contract';
import { Section, StatusBadge, ErrorNote } from '../../components/ui';
import { SpringMoney } from '../../components/Springs';
import { ago, moneyINR } from '../../lib/format';
import { captureRects, flipReorder, orderChanged, usePoll } from '../../lib/poll';
import { api } from '../../api/client';
import { mockApi } from '../../mock/server';
import { PageHeader } from './shared';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

function RowActions({ loan, onDone }: { loan: LoanQueueEntry; onDone: (msg: string, ok: boolean) => void }) {
  const [busy, setBusy] = useState(false);
  const act = async (decision: 'APPROVE' | 'REJECT') => {
    setBusy(true);
    try {
      const updated = await live().decideLoan(loan.loanId, decision);
      onDone(`${decision === 'APPROVE' ? 'Approved' : 'Rejected'} ${loan.loanId.slice(0, 8)}… → ${updated.status}`, true);
    } catch (e) {
      onDone(e instanceof Error ? e.message : String(e), false);
    } finally {
      setBusy(false);
    }
  };
  return (
    <span style={{ display: 'inline-flex', gap: 'var(--sp-xs)' }}>
      <button className="btn" disabled={busy} onClick={() => act('APPROVE')}>Approve</button>
      <button className="btn" disabled={busy} onClick={() => act('REJECT')}>Reject</button>
    </span>
  );
}

export function LoanQueue() {
  const { data: queue, error } = usePoll(() => live().getLoanQueue(), 2000);
  const { data: accounts } = usePoll(() => live().listAccounts(), 5000);
  const { data: pool } = usePoll(() => live().getAdminPool(), 2500);
  const tableRef = useRef<HTMLTableSectionElement>(null);
  const entries = queue?.entries ?? [];
  const nameOf = (id: string) => accounts?.find((a) => a.accountId === id)?.holderName ?? id;
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null);

  // FLIP reorder: rects captured before commit, compared after (lib/poll.ts)
  const lastRects = useRef<Map<string, DOMRect>>(new Map());
  const lastOrder = useRef<string[]>([]);
  useLayoutEffect(() => {
    const tbody = tableRef.current;
    if (!tbody) return;
    if (orderChanged(tbody, lastOrder.current)) {
      flipReorder(tbody, lastRects.current);
    }
    lastOrder.current = Array.from(tbody.querySelectorAll<HTMLElement>('[data-flip-key]')).map(
      (el) => el.dataset.flipKey ?? '',
    );
    lastRects.current = captureRects(tbody);
  }, [entries]);

  return (
    <>
      <PageHeader
        eyebrow="Loan Service · Valkey"
        title="Approval queue — live"
        sub="Score = (3 − tier) × 10⁹ + requestedAt: tier first, FCFS within tier. Approve runs the same atomic Lua pop-and-debit the worker would; reject appends LoanRejected."
      />

      {msg && <p className={msg.ok ? 'ok-text mono' : 'error-text mono'}>{msg.text}</p>}

      <section className="section">
        <div className="eyebrow">Admin pool balance</div>
        <SpringMoney value={pool?.balance ?? 0} className="mono balance-lg" />
      </section>

      <Section
        title="Queue"
        note="GET /api/loans/queue · priority order (tier, then arrival)"
        right={<span className="live-dot">live reorder</span>}
      >
        <table className="register">
          <thead>
            <tr>
              <th style={{ width: 40 }}>#</th>
              <th>Loan</th>
              <th>Customer</th>
              <th>Tier</th>
              <th className="num">Amount</th>
              <th>Status</th>
              <th>Requested</th>
              <th>Decision</th>
            </tr>
          </thead>
          <tbody ref={tableRef}>
            {entries.map((e: LoanQueueEntry) => (
              <tr key={e.loanId} data-flip-key={e.loanId} className="row-in">
                <td className="mono" style={{ color: 'var(--muted)' }}>{e.position}</td>
                <td className="mono">{e.loanId.slice(0, 8)}…</td>
                <td>{nameOf(e.accountId)}</td>
                <td>
                  <span className={`badge ${e.trustTier >= 2 ? 'green' : e.trustTier === 1 ? 'blue' : ''}`}>
                    {TIER_NAMES[e.trustTier as 0 | 1 | 2 | 3]}
                  </span>
                </td>
                <td className="num">{moneyINR(e.amount)}</td>
                <td><StatusBadge status={e.status} /></td>
                <td className="mono" style={{ color: 'var(--muted)' }}>{ago(e.requestedAt)}</td>
                <td><RowActions loan={e} onDone={(text, ok) => setMsg({ text, ok })} /></td>
              </tr>
            ))}
            {entries.length === 0 && (
              <tr><td colSpan={8} style={{ color: 'var(--muted)' }}>Queue empty. Requests appear as customers apply.</td></tr>
            )}
          </tbody>
        </table>
        <ErrorNote error={error} />
      </Section>
    </>
  );
}
