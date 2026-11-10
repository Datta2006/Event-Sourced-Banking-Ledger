import { Section, ErrorNote } from '../../components/ui';
import { ago } from '../../lib/format';
import { usePoll } from '../../lib/poll';
import { api } from '../../api/client';
import { mockApi } from '../../mock/server';
import { PageHeader } from './shared';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

function DeliveryBadge({ status }: { status?: string }) {
  const tone = status === 'SENT' ? 'green' : status === 'RETRYING' ? 'amber' : status === 'DEAD_LETTERED' ? 'red' : '';
  return <span className={`badge ${tone}`}>{(status ?? 'SENT').replaceAll('_', ' ').toLowerCase()}</span>;
}

export function NotificationsLog() {
  const { data: notes, error } = usePoll(() => live().listNotifications(), 3000);
  const { data: accounts } = usePoll(() => live().listAccounts(), 10000);
  const nameOf = (id: string) => accounts?.find((a) => a.accountId === id)?.holderName ?? id;

  const dead = notes?.filter((n) => n.deliveryStatus === 'DEAD_LETTERED').length ?? 0;
  const retrying = notes?.filter((n) => n.deliveryStatus === 'RETRYING').length ?? 0;

  return (
    <>
      <PageHeader
        eyebrow="Notification Service · MailHog in dev"
        title="Notifications log"
        sub="Every notification sent to any customer, with delivery state. DEAD_LETTERED items exhausted SMTP retries and surface the DLQ count the ops runbook watches."
      />

      <section className="section" style={{ display: 'flex', gap: 'var(--sp-3xl)' }}>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          <span className="eyebrow">Sent</span>
          <span className="mono" style={{ fontSize: 22, fontWeight: 600 }}>{notes?.length ?? 0}</span>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          <span className="eyebrow">Retrying</span>
          <span className="mono" style={{ fontSize: 22, fontWeight: 600, color: 'var(--warning)' }}>{retrying}</span>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          <span className="eyebrow">Dead-lettered</span>
          <span className="mono" style={{ fontSize: 22, fontWeight: 600, color: dead > 0 ? 'var(--debit)' : 'var(--muted)' }}>{dead}</span>
        </div>
      </section>

      <Section title="Log" note="GET /api/notifications · all customers">
        <table className="register">
          <thead>
            <tr><th>Sent</th><th>Customer</th><th>Subject</th><th>Cause</th><th>Channel</th><th>Delivery</th></tr>
          </thead>
          <tbody>
            {notes?.map((n) => (
              <tr key={n.notificationId} className="row-in">
                <td className="mono" style={{ color: 'var(--muted)' }}>{ago(n.sentAt)}</td>
                <td>{nameOf(n.accountId)}</td>
                <td>{n.subject}</td>
                <td><span className="badge">{n.causeEvent}</span></td>
                <td className="mono" style={{ color: 'var(--muted)' }}>{n.channel.toLowerCase()}</td>
                <td><DeliveryBadge status={n.deliveryStatus} /></td>
              </tr>
            ))}
            {notes && notes.length === 0 && (
              <tr><td colSpan={6} style={{ color: 'var(--muted)' }}>No notifications sent yet.</td></tr>
            )}
          </tbody>
        </table>
        <ErrorNote error={error} />
      </Section>
    </>
  );
}
