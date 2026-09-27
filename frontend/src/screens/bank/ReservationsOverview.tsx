import { useState } from 'react';
import type { ReservationView } from '../../api/contract';
import { Section, StatusBadge, ErrorNote } from '../../components/ui';
import { moneyINR, time } from '../../lib/format';
import { usePoll } from '../../lib/poll';
import { api } from '../../api/client';
import { mockApi } from '../../mock/server';
import { PageHeader } from './shared';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

export function ReservationsOverview() {
  const { data: reservations, error } = usePoll(() => live().listReservations(), 3000);
  const { data: accounts } = usePoll(() => live().listAccounts(), 10000);
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null);
  const nameOf = (id: string) => accounts?.find((a) => a.accountId === id)?.holderName ?? id;

  const totalHeld = reservations?.filter((r) => r.status === 'RESERVED').reduce((s, r) => s + r.amount, 0) ?? 0;

  const voidIt = async (r: ReservationView) => {
    setMsg(null);
    try {
      await live().voidReservation(r.reservationId);
      setMsg({ text: `FundsReleased (MANUAL_VOID) · ${moneyINR(r.amount)} returned to ${nameOf(r.accountId)}`, ok: true });
    } catch (e) {
      setMsg({ text: e instanceof Error ? e.message : String(e), ok: false });
    }
  };

  return (
    <>
      <PageHeader
        eyebrow="Payment & Reservation Service"
        title="Reservations"
        sub="Every offline-payment hold across all customers. A hold is money that has left available but not yet landed anywhere — void releases it back with a FundsReleased audit event."
      />

      {msg && <p className={msg.ok ? 'ok-text mono' : 'error-text mono'}>{msg.text}</p>}

      <section className="section">
        <div className="eyebrow">Currently held across all accounts</div>
        <span className="mono balance-lg">{moneyINR(totalHeld)}</span>
      </section>

      <Section title="All reservations" note="GET /api/reservations · newest first">
        <table className="register">
          <thead>
            <tr><th>Reservation</th><th>Customer</th><th className="num">Amount</th><th>Status</th><th>Expires</th><th>Captured</th><th></th></tr>
          </thead>
          <tbody>
            {reservations?.map((r) => (
              <tr key={r.reservationId} className="row-in">
                <td className="mono">{r.reservationId.slice(0, 8)}…</td>
                <td>{nameOf(r.accountId)}</td>
                <td className="num">{moneyINR(r.amount)}</td>
                <td><StatusBadge status={r.status} /></td>
                <td className="mono" style={{ color: 'var(--muted)' }}>{time(r.expiresAt)}</td>
                <td className="mono" style={{ color: 'var(--muted)' }}>{r.capturedAt ? time(r.capturedAt) : '—'}</td>
                <td>
                  {r.status === 'RESERVED' && (
                    <button className="btn" onClick={() => voidIt(r)}>Void</button>
                  )}
                </td>
              </tr>
            ))}
            {reservations && reservations.length === 0 && (
              <tr><td colSpan={7} style={{ color: 'var(--muted)' }}>No reservations on record.</td></tr>
            )}
          </tbody>
        </table>
        <ErrorNote error={error} />
      </Section>
    </>
  );
}
