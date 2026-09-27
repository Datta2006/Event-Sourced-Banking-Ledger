import { useLayoutEffect, useRef } from 'react';
import { api } from '../api/client';
import { mockApi } from '../mock/server';
import type { LoanQueueEntry } from '../api/contract';
import { TIER_NAMES } from '../api/contract';
import { Section, StatusBadge } from '../components/ui';
import { SpringMoney } from '../components/Springs';
import { ago, moneyINR } from '../lib/format';
import { captureRects, flipReorder, orderChanged, usePoll } from '../lib/poll';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

function PoolGauge() {
  const { data: pool } = usePoll(() => live().getAdminPool(), 2500);
  return (
    <span style={{ display: 'inline-flex', alignItems: 'baseline', gap: 'var(--sp-sm)', flexWrap: 'wrap' }}>
      <SpringMoney value={pool?.balance ?? 0} className="mono balance-lg" />
      <span className="section-note">admin pool · Lua debit</span>
    </span>
  );
}

export function LoanQueue() {
  const { data: queue } = usePoll(() => live().getLoanQueue(), 2000);
  const tableRef = useRef<HTMLTableSectionElement>(null);
  const entries = queue?.entries ?? [];

  // Rects from the last committed render = the "first" frame for the next FLIP.
  const lastRects = useRef<Map<string, DOMRect>>(new Map());
  const lastOrder = useRef<string[]>([]);

  useLayoutEffect(() => {
    const tbody = tableRef.current;
    if (!tbody) return;
    // The original bug: it compared a render-count number, so it fired on
    // every poll even when nothing moved. Compare the actual key order.
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
      <header>
        <div className="eyebrow">Loan Service · Valkey</div>
        <h1 className="page-title">Priority queue</h1>
        <p className="page-sub">
          Score = (3 − tier) × 10⁹ + requestedAt: tier first, FCFS within tier. The worker pops the head and
          runs the atomic Lua debit; insufficient pool defers, never double-approves.
        </p>
      </header>

      <section className="section">
        <div className="eyebrow">Admin pool balance</div>
        <PoolGauge />
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
              <th>Account</th>
              <th>Tier</th>
              <th style={{ textAlign: 'right' }}>Amount</th>
              <th>Status</th>
              <th>Requested</th>
            </tr>
          </thead>
          <tbody ref={tableRef}>
            {entries.map((e: LoanQueueEntry) => (
              <tr key={e.loanId} data-flip-key={e.loanId} className="row-in">
                <td className="mono" style={{ color: 'var(--muted)' }}>{e.position}</td>
                <td className="mono">{e.loanId.slice(0, 8)}</td>
                <td className="mono">{e.accountId}</td>
                <td>
                  <span className={`badge ${e.trustTier >= 2 ? 'green' : e.trustTier === 1 ? 'blue' : ''}`}>
                    {TIER_NAMES[e.trustTier as 0 | 1 | 2 | 3]}
                  </span>
                </td>
                <td className="num">{moneyINR(e.amount)}</td>
                <td><StatusBadge status={e.status} /></td>
                <td className="mono" style={{ color: 'var(--muted)' }}>{ago(e.requestedAt)}</td>
              </tr>
            ))}
            {entries.length === 0 && (
              <tr><td colSpan={7} style={{ color: 'var(--muted)' }}>Queue empty. Requests will appear here as they arrive.</td></tr>
            )}
          </tbody>
        </table>
      </Section>
    </>
  );
}
