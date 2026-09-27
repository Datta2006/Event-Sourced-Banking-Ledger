import { api } from '../api/client';
import { mockApi } from '../mock/server';
import { Section, StatusBadge, ErrorNote } from '../components/ui';
import { ago } from '../lib/format';
import { usePoll } from '../lib/poll';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

function LagBar({ lag }: { lag: number }) {
  // visual scale: 0-200+ messages
  const pct = Math.min(100, (lag / 200) * 100);
  const tone = lag > 100 ? 'var(--debit)' : lag > 40 ? 'var(--warning)' : 'var(--credit)';
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--sp-sm)', minWidth: 160 }}>
      <div style={{ flex: 1, height: 4, background: 'var(--hairline)', borderRadius: 2, overflow: 'hidden' }}>
        {/* transform-only: scaleX with left origin, never width (animate skill §4) */}
        <div
          style={{
            width: '100%',
            height: '100%',
            background: tone,
            transform: `scaleX(${pct / 100})`,
            transformOrigin: '0 50%',
            transition: 'transform var(--dur-medium) var(--ease-enter)',
          }}
        />
      </div>
      <span className="mono" style={{ fontSize: 11, color: 'var(--muted)', minWidth: 30, textAlign: 'right' }}>{lag}</span>
    </div>
  );
}

export function SystemOps() {
  const { data: services, error: svcErr } = usePoll(() => live().listServiceInstances(), 4000);
  const { data: shards, error: shardErr } = usePoll(() => live().listShards(), 6000);
  const { data: topics, error: kafkaErr } = usePoll(() => live().listKafkaTopics(), 3000);

  return (
    <>
      <header>
        <div className="eyebrow">Infrastructure · ARCHITECTURE.md §1</div>
        <h1 className="page-title">System ops</h1>
        <p className="page-sub">
          Eureka registry, region-sharded ledger (ShardingSphere-JDBC), and Kafka consumer lag: the
          plumbing the event stream depends on.
        </p>
      </header>

      <Section
        title="Service registry"
        note="Eureka · 8 services + gateway"
        right={<span className="live-dot">poll 4s</span>}
      >
        <table className="register">
          <thead>
            <tr><th>Service</th><th>Instance</th><th>Endpoint</th><th>Status</th><th>Registered</th></tr>
          </thead>
          <tbody>
            {services?.map((s) => (
              <tr key={s.instanceId}>
                <td style={{ fontWeight: 600 }}>{s.serviceId}</td>
                <td className="mono" style={{ color: 'var(--muted)' }}>{s.instanceId}</td>
                <td className="mono">{s.host}:{s.port}</td>
                <td><StatusBadge status={s.status} /></td>
                <td className="mono" style={{ color: 'var(--muted)' }}>{ago(s.registeredAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <ErrorNote error={svcErr} />
      </Section>

      <div className="grid-2">
        <Section title="Ledger shards" note="PostgreSQL region shards · registration IP → region → shard (v2 §7.1)">
          <table className="register">
            <thead>
              <tr><th>Shard</th><th>Region</th><th>Endpoint</th><th className="num">ledger_events</th><th>Status</th></tr>
            </thead>
            <tbody>
              {shards?.map((sh) => (
                <tr key={sh.shardId}>
                  <td className="mono">{sh.shardId}</td>
                  <td><span className="badge">{sh.region}</span></td>
                  <td className="mono" style={{ color: 'var(--muted)' }}>{sh.host}:{sh.port}</td>
                  <td className="num">{sh.rowCounts.ledger_events.toLocaleString('en-IN')}</td>
                  <td><StatusBadge status={sh.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
          <ErrorNote error={shardErr} />
        </Section>

        <Section title="Kafka consumer lag" note="partition-per-account ordering · lag is the read-side backlog">
          <table className="register">
            <thead>
              <tr><th>Topic</th><th>Partitions</th><th>Consumer group</th><th>Lag</th></tr>
            </thead>
            <tbody>
              {topics?.flatMap((t) =>
                t.groups.map((g) => (
                  <tr key={`${t.topic}-${g.groupId}`}>
                    <td className="mono">{t.topic}</td>
                    <td className="mono">{t.partitions}</td>
                    <td className="mono" style={{ color: 'var(--muted)' }}>{g.groupId}</td>
                    <td><LagBar lag={g.lag} /></td>
                  </tr>
                )),
              )}
            </tbody>
          </table>
          <ErrorNote error={kafkaErr} />
        </Section>
      </div>
    </>
  );
}
