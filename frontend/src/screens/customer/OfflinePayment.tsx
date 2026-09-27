import { useState } from 'react';
import type { ReservationView } from '../../api/contract';
import { Section, StatusBadge, ErrorNote } from '../../components/ui';
import { moneyINR, time, ago } from '../../lib/format';
import { usePoll } from '../../lib/poll';
import { api } from '../../api/client';
import { mockApi } from '../../mock/server';
import { PageHeader, NoAccount, useMe } from './shared';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

function TokenCard({ reservation }: { reservation: ReservationView }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    await navigator.clipboard.writeText(reservation.offlineToken ?? '');
    setCopied(true);
    setTimeout(() => setCopied(false), 1600);
  };
  return (
    <div
      style={{
        border: '1px dashed var(--hairline-strong)',
        borderRadius: 'var(--r-md)',
        padding: 'var(--sp-lg)',
        background: 'var(--paper)',
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--sp-md)',
      }}
    >
      <div className="eyebrow">Signed offline token · Nimbus JOSE+JWT</div>
      <div
        className="mono"
        style={{
          fontSize: 11,
          lineHeight: 1.6,
          wordBreak: 'break-all',
          color: 'var(--muted)',
          maxHeight: 84,
          overflow: 'hidden',
          position: 'relative',
        }}
      >
        {reservation.offlineToken}
        <div
          style={{
            position: 'absolute',
            bottom: 0,
            left: 0,
            right: 0,
            height: 40,
            background: 'linear-gradient(transparent, var(--paper))',
          }}
        />
      </div>
      <div style={{ display: 'flex', gap: 'var(--sp-sm)', alignItems: 'center', flexWrap: 'wrap' }}>
        <button className="btn" onClick={copy}>{copied ? 'Copied ✓' : 'Copy token'}</button>
        <span className="section-note">merchant verifies signature offline, zero connectivity</span>
      </div>
    </div>
  );
}

export function OfflinePayment({ accountId }: { accountId: string }) {
  const { data: me } = useMe(accountId);
  const { data: accounts } = usePoll(() => live().listAccounts(), 5000);
  const [amount, setAmount] = useState('1200.00');
  const [ttl, setTtl] = useState('30');
  const [reservation, setReservation] = useState<ReservationView | null>(null);
  const [err, setErr] = useState<string | undefined>();
  const [captureMsg, setCaptureMsg] = useState<string | undefined>(undefined);
  const [captureTone, setCaptureTone] = useState<'ok' | 'err'>('ok');

  const { data: fresh } = usePoll(
    () => (reservation ? live().getReservation(reservation.reservationId) : Promise.resolve(undefined)),
    2000,
    [reservation?.reservationId],
  );
  const current = fresh ?? reservation;

  // customer's own reservation history
  const { data: mine } = usePoll(() => live().listReservations(accountId), 4000);

  const reserve = async () => {
    setErr(undefined);
    setCaptureMsg(undefined);
    try {
      const r = await live().createReservation(accountId, Number(amount), Number(ttl));
      setReservation(r);
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    }
  };

  const capture = async () => {
    if (!current) return;
    const merchant = accounts?.find((a) => a.accountId !== current.accountId && a.status === 'ACTIVE');
    try {
      await live().captureReservation(current.reservationId, current.offlineToken ?? '', merchant?.accountId ?? current.accountId);
      setCaptureMsg('FundsCaptured. Hold converted to a real debit, merchant credited.');
      setCaptureTone('ok');
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setCaptureMsg(msg.includes('409') ? '409 · replay rejected. First capture wins; double-spend prevented.' : msg);
      setCaptureTone('err');
    }
  };

  return (
    <>
      <PageHeader
        eyebrow="Customer portal · v2 §4"
        title="Offline payment"
        sub="Pessimistic pre-commit: FundsReserved moves available → reserved, a signed token is minted, and the first capture by reservationId wins. Uninvoked holds expire via FundsReleased."
      />
      {!me && <NoAccount accountId={accountId} />}

      <div className="grid-2" style={{ marginTop: 'var(--sp-xl)' }}>
        <section className="panel">
          <div className="panel-head">
            <span className="panel-title">Reserve funds</span>
            <span className="mono" style={{ fontSize: 10, color: 'var(--muted)' }}>POST /api/reservations</span>
          </div>
          <div className="panel-body" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-lg)' }}>
            <div className="field">
              <label htmlFor="from-acct">From</label>
              <div className="mono" style={{ fontSize: 13, padding: '8px 0' }}>
                {me ? `${me.holderName} · available ${moneyINR(me.availableBalance)} · reserved ${moneyINR(me.reservedBalance)}` : '…'}
              </div>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--sp-md)' }}>
              <div className="field">
                <label htmlFor="amt">Amount (₹)</label>
                <input id="amt" value={amount} onChange={(e) => setAmount(e.target.value)} inputMode="decimal" />
              </div>
              <div className="field">
                <label htmlFor="ttl">TTL (minutes)</label>
                <input id="ttl" value={ttl} onChange={(e) => setTtl(e.target.value)} inputMode="numeric" />
              </div>
            </div>
            <button className="btn btn-primary" onClick={reserve} disabled={!me}>
              Reserve &amp; mint token
            </button>
            <ErrorNote error={err} />
          </div>
        </section>

        <section className="panel">
          <div className="panel-head">
            <span className="panel-title">Reservation</span>
            {current && <StatusBadge status={current.status} />}
          </div>
          <div className="panel-body">
            {!current && (
              <p style={{ color: 'var(--muted)', fontSize: 13 }}>
                No active reservation. The token below is what a merchant device would scan offline.
              </p>
            )}
            {current && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-lg)' }}>
                <div style={{ display: 'flex', gap: 'var(--sp-xl)', flexWrap: 'wrap', alignItems: 'baseline' }}>
                  <span className="mono balance-lg">{moneyINR(current.amount)}</span>
                  <span className="mono" style={{ color: 'var(--muted)', fontSize: 12 }}>
                    expires {time(current.expiresAt)}
                  </span>
                </div>
                {current.status === 'RESERVED' && current.offlineToken && <TokenCard reservation={current} />}
                {current.status === 'RESERVED' && (
                  <div style={{ display: 'flex', gap: 'var(--sp-sm)', flexWrap: 'wrap' }}>
                    <button className="btn btn-primary" onClick={capture}>Capture (merchant online)</button>
                    <button
                      className="btn"
                      onClick={capture}
                      title="Second capture attempt, must be rejected"
                    >
                      Replay capture (should fail)
                    </button>
                  </div>
                )}
                {captureMsg && (
                  <p className={captureTone === 'ok' ? 'ok-text mono' : 'error-text mono'}>{captureMsg}</p>
                )}
              </div>
            )}
          </div>
        </section>
      </div>

      <Section title="Your reservations" note="GET /api/reservations?accountId=… · holds on this account only">
        <table className="register">
          <thead>
            <tr><th>Reservation</th><th className="num">Amount</th><th>Status</th><th>Expires</th></tr>
          </thead>
          <tbody>
            {mine?.map((r) => (
              <tr key={r.reservationId}>
                <td className="mono">{r.reservationId.slice(0, 8)}…</td>
                <td className="num">{moneyINR(r.amount)}</td>
                <td><StatusBadge status={r.status} /></td>
                <td className="mono" style={{ color: 'var(--muted)' }}>{ago(r.expiresAt)}</td>
              </tr>
            ))}
            {mine && mine.length === 0 && (
              <tr><td colSpan={4} style={{ color: 'var(--muted)' }}>No reservations yet.</td></tr>
            )}
          </tbody>
        </table>
      </Section>
    </>
  );
}
