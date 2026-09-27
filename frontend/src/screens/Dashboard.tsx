import { api } from '../api/client';
import { mockApi } from '../mock/server';
import type { AccountSummary, EventView } from '../api/contract';
import { Section, StatusBadge, ErrorNote } from '../components/ui';
import { TintedSpringMoney } from '../components/Springs';
import { ago, eventLabel, eventTone, moneyINR, time } from '../lib/format';
import { usePoll } from '../lib/poll';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

function AccountRow({ account }: { account: AccountSummary }) {
  return (
    <tr>
      <td className="mono">{account.accountId}</td>
      <td>{account.holderName}</td>
      <td><span className="badge">{account.region}</span></td>
      <td className="num amount-credit">{moneyINR(account.availableBalance)}</td>
      <td className="num" style={{ color: account.reservedBalance > 0 ? 'var(--warning)' : 'var(--muted)' }}>
        {moneyINR(account.reservedBalance)}
      </td>
      <td className="mono" style={{ color: 'var(--muted)' }}>{ago(account.updatedAt)}</td>
    </tr>
  );
}

export function Dashboard() {
  const { data: accounts, error: acctErr } = usePoll(() => live().listAccounts(), 2500);
  const { data: events, error: evErr } = usePoll(() => live().listRecentEvents(undefined, 40), 2000);
  const { data: fraud } = usePoll(() => live().listFraudCases(), 10000);

  const featured = accounts?.[0];
  const openFraud = fraud?.filter((f) => f.status === 'OPEN') ?? [];

  return (
    <>
      <header>
        <div className="eyebrow">Ledger Terminal</div>
        <h1 className="page-title">Accounts &amp; live ledger</h1>
        <p className="page-sub">
          Read model trails the write side by design: every row below is derived from committed events.
        </p>
      </header>

      {featured && (
        <section className="section">
          <div className="eyebrow">Featured account · {featured.accountId}</div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 'var(--sp-xl)', flexWrap: 'wrap' }}>
            <TintedSpringMoney value={featured.availableBalance} />
            <div style={{ display: 'flex', gap: 'var(--sp-sm)', flexWrap: 'wrap' }}>
              <span className="badge green"><span className="dot" />available {moneyINR(featured.availableBalance)}</span>
              <span className="badge amber"><span className="dot" />reserved {moneyINR(featured.reservedBalance)}</span>
              <span className="badge">{featured.region}</span>
            </div>
          </div>
        </section>
      )}

      <Section
        title="Accounts"
        note="Read-model snapshot · GET /api/accounts"
        right={<span className="live-dot">poll 2.5s</span>}
      >
        <table className="register">
          <thead>
            <tr>
              <th>Account</th>
              <th>Holder</th>
              <th>Region</th>
              <th style={{ textAlign: 'right' }}>Available</th>
              <th style={{ textAlign: 'right' }}>Reserved</th>
              <th>Updated</th>
            </tr>
          </thead>
          <tbody>
            {accounts?.map((a) => <AccountRow key={a.accountId} account={a} />)}
          </tbody>
        </table>
        <ErrorNote error={acctErr} />
      </Section>

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

        <Section
          title="Fraud cases"
          note="GET /api/fraud/cases · OPEN cases first"
        >
          <table className="register">
            <thead>
              <tr>
                <th>Rule</th>
                <th>Account</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {openFraud.map((f) => (
                <tr key={f.caseId}>
                  <td className="mono">{f.rule}</td>
                  <td className="mono">{f.accountId}</td>
                  <td><StatusBadge status={f.status} /></td>
                </tr>
              ))}
              {openFraud.length === 0 && (
                <tr><td colSpan={3} style={{ color: 'var(--muted)' }}>No open cases. All events clean.</td></tr>
              )}
            </tbody>
          </table>
        </Section>
      </div>
    </>
  );
}
