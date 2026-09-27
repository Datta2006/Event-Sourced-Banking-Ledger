import { Section, ErrorNote } from '../../components/ui';
import { TintedSpringMoney } from '../../components/Springs';
import { eventLabel, eventTone, moneyINR, time } from '../../lib/format';
import { usePoll } from '../../lib/poll';
import { api } from '../../api/client';
import { mockApi } from '../../mock/server';
import type { EventView } from '../../api/contract';
import { PageHeader } from './shared';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

function Stat({ label, value, tone }: { label: string; value: string; tone?: string }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--sp-xs)' }}>
      <span className="eyebrow">{label}</span>
      <span className="mono" style={{ fontSize: 22, fontWeight: 600, color: tone }}>{value}</span>
    </div>
  );
}

export function OpsDashboard() {
  const { data: accounts } = usePoll(() => live().listAccounts(), 2500);
  const { data: pool } = usePoll(() => live().getAdminPool(), 2500);
  const { data: queue } = usePoll(() => live().getLoanQueue(), 2000);
  const { data: fraud } = usePoll(() => live().listFraudCases(), 5000);
  const { data: events, error } = usePoll(() => live().listRecentEvents(undefined, 40), 2000);

  const totalAvailable = accounts?.reduce((s, a) => s + a.availableBalance, 0) ?? 0;
  const totalReserved = accounts?.reduce((s, a) => s + a.reservedBalance, 0) ?? 0;
  const openFraud = fraud?.filter((f) => f.status === 'OPEN').length ?? 0;
  const queueDepth = queue?.entries.length ?? 0;
  const waiting = queue?.entries.filter((e) => e.status === 'WAITING_FOR_FUNDS').length ?? 0;

  return (
    <>
      <PageHeader
        eyebrow="Bank portal · operations"
        title="Ops dashboard"
        sub="The whole book at a glance: balances across every region shard, the loan pipeline, and open fraud exposure."
      />

      <section className="section">
        <div style={{ display: 'flex', gap: 'var(--sp-3xl)', flexWrap: 'wrap', alignItems: 'flex-end' }}>
          <div>
            <div className="eyebrow">Total balance across all accounts</div>
            <TintedSpringMoney value={totalAvailable} />
          </div>
          <Stat label="Reserved (holds)" value={moneyINR(totalReserved)} tone="var(--warning)" />
          <Stat label="Admin pool" value={moneyINR(pool?.balance ?? 0)} tone="var(--accent)" />
          <Stat label="Loan queue" value={`${queueDepth}${waiting ? ` (${waiting} waiting)` : ''}`} />
          <Stat label="Open fraud flags" value={String(openFraud)} tone={openFraud > 0 ? 'var(--debit)' : 'var(--credit)'} />
          <Stat label="Accounts" value={String(accounts?.length ?? 0)} />
        </div>
      </section>

      <Section
        title="Live event stream"
        note="GET /api/events · every append across all aggregates, newest first"
        right={<span className="live-dot">live</span>}
      >
        <div style={{ maxHeight: 460, overflowY: 'auto' }}>
          <table className="register">
            <tbody>
              {events?.map((e: EventView) => (
                <tr key={e.eventId} className="row-in">
                  <td style={{ width: 76 }} className="mono" title={e.occurredAt}>{time(e.occurredAt)}</td>
                  <td style={{ width: 190 }}>
                    <span className={`badge ${eventTone(e.type)}`}>{eventLabel(e.type)}</span>
                  </td>
                  <td className="mono" style={{ color: 'var(--muted)' }}>{e.aggregateId}</td>
                  <td><span className="badge">{e.region}</span></td>
                  <td className="mono" style={{ color: 'var(--muted)', textAlign: 'right' }}>v{e.version}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <ErrorNote error={error} />
      </Section>
    </>
  );
}
