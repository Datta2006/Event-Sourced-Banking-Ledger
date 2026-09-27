import { useEffect, useState } from 'react';
import { api } from '../api/client';
import { mockApi } from '../mock/server';
import type { TransferStatus, TransferStatusView } from '../api/contract';
import { Section, StatusBadge, ErrorNote } from '../components/ui';
import { SpringMoney, SpringProgress } from '../components/Springs';
import { moneyINR, shortId, time } from '../lib/format';
import { usePoll } from '../lib/poll';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

const SAGA_STEPS: { key: TransferStatus; label: string; note: string }[] = [
  { key: 'INITIATED', label: 'Initiated', note: 'TransferInitiated appended' },
  { key: 'SOURCE_DEBITED', label: 'Source debited', note: 'MoneyWithdrawn · RFairLock held' },
  { key: 'TARGET_CREDITED', label: 'Target credited', note: 'MoneyDeposited appended' },
  { key: 'COMPLETED', label: 'Completed', note: 'TransferCompleted appended' },
];

const STEP_ORDER: Record<TransferStatus, number> = {
  INITIATED: 0,
  SOURCE_DEBITED: 1,
  TARGET_CREDITED: 2,
  COMPLETED: 3,
  FAILED: 3,
  COMPENSATED: 3,
};

function SagaStepper({ status }: { status: TransferStatus }) {
  const failed = status === 'FAILED' || status === 'COMPENSATED';
  const current = STEP_ORDER[status];
  const progress = Math.max(0, Math.min(1, current / (SAGA_STEPS.length - 1)));

  return (
    <div>
      <div className="saga-track">
        <SpringProgress value={progress} failed={failed} />
      </div>
      <div style={{ display: 'flex', marginTop: 'var(--sp-sm)' }}>
        {SAGA_STEPS.map((s, i) => {
          const done = i < current || (!failed && status === 'COMPLETED');
          const active = i === current && !done;
          const isFailedStep = failed && i === current;
          return (
            <div key={s.key} style={{ flex: 1 }}>
              <div
                style={{
                  width: 9,
                  height: 9,
                  borderRadius: 9999,
                  margin: '0 auto',
                  background: isFailedStep ? 'var(--debit)' : done ? 'var(--accent)' : active ? 'var(--accent)' : 'var(--hairline-strong)',
                  animation: active ? 'pulse 1.4s ease-in-out infinite' : undefined,
                }}
              />
              <div style={{ textAlign: 'center', marginTop: 'var(--sp-xs)' }}>
                <div
                  style={{
                    fontSize: 12,
                    fontWeight: done || active ? 600 : 500,
                    color: isFailedStep ? 'var(--debit)' : done || active ? 'var(--ink)' : 'var(--muted)',
                  }}
                >
                  {s.label}
                </div>
                <div className="mono" style={{ fontSize: 10, color: 'var(--muted)', marginTop: 2 }}>{s.note}</div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function AmountSpring({ amount }: { amount: number }) {
  return <SpringMoney className="mono balance-lg amount-debit" prefix="−" value={amount} />;
}

export function Transfer() {
  const { data: accounts } = usePoll(() => live().listAccounts(), 5000);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [amount, setAmount] = useState('250.00');
  const [transferId, setTransferId] = useState<string | null>(null);
  const [submitErr, setSubmitErr] = useState<string | undefined>();

  const { data: status } = usePoll(
    () => (transferId ? live().getTransferStatus(transferId) : Promise.resolve(undefined)),
    1200,
    [transferId],
  );

  // poll picks up the mock's status object by identity; keep last non-undefined
  const [lastStatus, setLastStatus] = useState<TransferStatusView | undefined>();
  useEffect(() => {
    if (status) setLastStatus(status);
  }, [status]);

  const submit = async () => {
    setSubmitErr(undefined);
    if (!from || !to || from === to) {
      setSubmitErr('Pick two different accounts.');
      return;
    }
    try {
      const res = await live().transfer({ fromAccountId: from, toAccountId: to, amount: Number(amount) });
      setTransferId(res.transferId);
    } catch (e) {
      setSubmitErr(e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <>
      <header>
        <div className="eyebrow">Account Command · orchestrated saga</div>
        <h1 className="page-title">Transfer</h1>
        <p className="page-sub">
          Two local transactions, each appending events. Failure after the source leg triggers
          TransferSourceReimbursed: compensation is just more events, never a deletion.
        </p>
      </header>

      <div className="grid-2" style={{ marginTop: 'var(--sp-xl)' }}>
        <section className="panel">
          <div className="panel-head">
            <span className="panel-title">New transfer</span>
            <span className="mono" style={{ fontSize: 10, color: 'var(--muted)' }}>POST /api/transfers</span>
          </div>
          <div className="panel-body" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-lg)' }}>
            <div className="field">
              <label htmlFor="from">From account</label>
              <select id="from" value={from} onChange={(e) => setFrom(e.target.value)}>
                <option value="">Select…</option>
                {accounts?.map((a) => (
                  <option key={a.accountId} value={a.accountId}>
                    {a.holderName} · {a.accountId.slice(0, 8)} · {moneyINR(a.availableBalance)}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="to">To account</label>
              <select id="to" value={to} onChange={(e) => setTo(e.target.value)}>
                <option value="">Select…</option>
                {accounts?.map((a) => (
                  <option key={a.accountId} value={a.accountId}>
                    {a.holderName} · {a.accountId.slice(0, 8)}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="amount">Amount (₹)</label>
              <input id="amount" value={amount} onChange={(e) => setAmount(e.target.value)} inputMode="decimal" />
            </div>
            <button className="btn btn-primary" onClick={submit} disabled={!from || !to}>
              Start saga
            </button>
            <ErrorNote error={submitErr} />
          </div>
        </section>

        <section className="panel">
          <div className="panel-head">
            <span className="panel-title">Saga progress</span>
            {lastStatus && <StatusBadge status={lastStatus.status} />}
          </div>
          <div className="panel-body">
            {!lastStatus && (
              <p style={{ color: 'var(--muted)', fontSize: 13 }}>
                Start a transfer · steps map 1:1 to appended events (SAGA_DESIGN.md §1).
              </p>
            )}
            {lastStatus && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-xl)' }}>
                <div style={{ display: 'flex', gap: 'var(--sp-lg)', alignItems: 'baseline', flexWrap: 'wrap' }}>
                  <AmountSpring amount={lastStatus.amount} />
                  <span className="mono" style={{ color: 'var(--muted)' }}>
                    {shortId(lastStatus.transferId)} · started {time(lastStatus.startedAt)}
                  </span>
                </div>
                <SagaStepper status={lastStatus.status} />
                {lastStatus.failureReason && <ErrorNote error={lastStatus.failureReason} />}
              </div>
            )}
          </div>
        </section>
      </div>

      <Section
        title="Accounts"
        note="balances update as the mock consumes the saga legs"
      >
        <table className="register">
          <thead>
            <tr>
              <th>Holder</th>
              <th className="num">Available</th>
              <th>Region</th>
            </tr>
          </thead>
          <tbody>
            {accounts?.map((a) => (
              <tr key={a.accountId}>
                <td>{a.holderName} <span className="mono" style={{ color: 'var(--muted)', fontSize: 11 }}>{a.accountId.slice(0, 8)}</span></td>
                <td className="num">{moneyINR(a.availableBalance)}</td>
                <td><span className="badge">{a.region}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      </Section>
    </>
  );
}
