import { Section, StatusBadge, ErrorNote } from '../../components/ui';
import { usePoll } from '../../lib/poll';
import { api } from '../../api/client';
import { mockApi } from '../../mock/server';
import { PageHeader } from './shared';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

const COLUMN_SPLIT = [
  { table: 'accounts', core: 'account_id, holder_name, region, status, kyc_status', pii: 'registration_ip, contact_email', why: 'PII columns live in a restricted-access table; the read model never selects them' },
  { table: 'loan_requests', core: 'loan_id, account_id, amount, tier, status', pii: 'purpose (free text)', why: 'free text is quarantined; approvers see it only in the review UI' },
  { table: 'notifications', core: 'notification_id, account_id, cause_event, sent_at', pii: 'recipient_address', why: 'addresses are write-only for the app, readable only by the mail job' },
];

export function Fragmentation() {
  const { data: shards, error } = usePoll(() => live().listShards(), 4000);
  const { data: accounts } = usePoll(() => live().listAccounts(), 5000);

  return (
    <>
      <PageHeader
        eyebrow="System & data · v2 §7"
        title="Fragmentation"
        sub="Two independent axes, two independent reasons: horizontal region sharding routes each customer's ledger by registration IP; vertical column-split quarantines PII from the columns the read model touches."
      />

      <Section
        title="Horizontal — region shards"
        note="GET /api/ops/shards · ShardingSphere-JDBC inside Account Command & Ledger Query (not a service)"
      >
        <table className="register">
          <thead>
            <tr><th>Shard</th><th>Region</th><th>Endpoint</th><th className="num">ledger_events</th><th>Customers</th><th>Status</th></tr>
          </thead>
          <tbody>
            {shards?.map((sh) => {
              const customers = accounts?.filter((a) => a.region === sh.region).length ?? 0;
              return (
                <tr key={sh.shardId}>
                  <td className="mono">{sh.shardId}</td>
                  <td><span className="badge">{sh.region}</span></td>
                  <td className="mono" style={{ color: 'var(--muted)' }}>{sh.host}:{sh.port}</td>
                  <td className="num">{sh.rowCounts.ledger_events.toLocaleString('en-IN')}</td>
                  <td className="num">{customers}</td>
                  <td><StatusBadge status={sh.status} /></td>
                </tr>
              );
            })}
          </tbody>
        </table>
        <p className="section-note" style={{ marginTop: 'var(--sp-md)' }}>
          Registration IP → region via ip-location-db (103.x → APAC, 51.x → EU, else AMER) → shard. Every
          account event commits to exactly one shard; no cross-shard transactions exist by design.
        </p>
        <ErrorNote error={error} />
      </Section>

      <Section title="Vertical — column split (core vs PII)" note="FRAGMENTATION_DESIGN.md · access-pattern-driven, not convenience-driven">
        <table className="register">
          <thead>
            <tr><th>Table</th><th>Core columns (read model)</th><th>PII columns (restricted)</th><th>Why split</th></tr>
          </thead>
          <tbody>
            {COLUMN_SPLIT.map((row) => (
              <tr key={row.table}>
                <td className="mono">{row.table}</td>
                <td className="mono" style={{ fontSize: 11 }}>{row.core}</td>
                <td className="mono" style={{ fontSize: 11, color: 'var(--warning)' }}>{row.pii}</td>
                <td style={{ color: 'var(--muted)' }}>{row.why}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Section>
    </>
  );
}
