import type { EventView } from '../../api/contract';
import { Section, StatusBadge, ErrorNote } from '../../components/ui';
import { TintedSpringMoney } from '../../components/Springs';
import { ago, eventLabel, eventTone, moneyINR, time } from '../../lib/format';
import { usePoll } from '../../lib/poll';
import { api } from '../../api/client';
import { mockApi } from '../../mock/server';
import { PageHeader, NoAccount, useMe } from './shared';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

export function Dashboard({ accountId }: { accountId: string }) {
  const { data: me } = useMe(accountId);
  const { data: events, error: evErr } = usePoll(() => live().listRecentEvents(undefined, 40), 2000);
  const { data: fraud } = usePoll(() => live().listFraudCases(accountId), 10000);
  const myFraud = fraud?.filter((f) => f.status === 'OPEN') ?? [];

  return (
    <>
      <PageHeader
        eyebrow="Customer portal · your ledger"
        title={me ? me.holderName : 'Accounts'}
        sub="Read model trails the write side by design: every row below is derived from committed events."
      />

      {me && (
        <section className="section">
          <div className="eyebrow">Available balance · {me.accountId}</div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 'var(--sp-xl)', flexWrap: 'wrap' }}>
            <TintedSpringMoney value={me.availableBalance} />
            <div style={{ display: 'flex', gap: 'var(--sp-sm)', flexWrap: 'wrap' }}>
              <span className="badge green"><span className="dot" />available {moneyINR(me.availableBalance)}</span>
              <span className="badge amber"><span className="dot" />reserved {moneyINR(me.reservedBalance)}</span>
              <span className="badge">{me.region}</span>
              <StatusBadge status={me.kycStatus} />
            </div>
          </div>
        </section>
      )}
      {!me && <NoAccount accountId={accountId} />}

      <div className="grid-2">
        <Section
          title="Live event stream"
          note="GET /api/events · appended events, newest first"
          right={<span className="live-dot">live</span>}
        >
          <div style={{ maxHeight: 420, overflowY: 'auto' }}>
            <table className="register">
              <tbody>
                {events?.map((e: EventView) => (
                  <tr key={e.eventId} className="row-in">
                    <td style={{ width: 76 }} className="mono" title={e.occurredAt}>{time(e.occurredAt)}</td>
                    <td style={{ width: 190 }}>
                      <span className={`badge ${eventTone(e.type)}`}>{eventLabel(e.type)}</span>
                    </td>
                    <td className="mono" style={{ color: 'var(--muted)' }}>{e.aggregateId}</td>
                    <td className="mono" style={{ color: 'var(--muted)', textAlign: 'right' }}>v{e.version}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <ErrorNote error={evErr} />
        </Section>

        <Section title="Fraud alerts on your account" note="GET /api/fraud/cases?accountId=… · contact the bank if flagged">
          <table className="register">
            <thead>
              <tr><th>Rule</th><th>Risk</th><th>Status</th><th>Flagged</th></tr>
            </thead>
            <tbody>
              {myFraud.map((f) => (
                <tr key={f.caseId}>
                  <td className="mono">{f.rule}</td>
                  <td className="num">{f.riskScore ?? '—'}</td>
                  <td><StatusBadge status={f.status} /></td>
                  <td className="mono" style={{ color: 'var(--muted)' }}>{ago(f.flaggedAt)}</td>
                </tr>
              ))}
              {myFraud.length === 0 && (
                <tr><td colSpan={4} style={{ color: 'var(--muted)' }}>
                  No open flags. Rule internals are never shown here — contact the bank for anything you don't recognise.
                </td></tr>
              )}
            </tbody>
          </table>
        </Section>
      </div>
    </>
  );
}
