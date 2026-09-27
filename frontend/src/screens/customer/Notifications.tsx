import type { NotificationView } from '../../api/contract';
import { Section } from '../../components/ui';
import { ago } from '../../lib/format';
import { usePoll } from '../../lib/poll';
import { api } from '../../api/client';
import { mockApi } from '../../mock/server';
import { PageHeader, NoAccount, useMe } from './shared';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

function DeliveryBadge({ status }: { status?: NotificationView['deliveryStatus'] }) {
  if (!status) return <span className="badge">sent</span>;
  const tone = status === 'SENT' ? 'green' : status === 'RETRYING' ? 'amber' : 'red';
  return <span className={`badge ${tone}`}>{status.replaceAll('_', ' ').toLowerCase()}</span>;
}

export function Notifications({ accountId }: { accountId: string }) {
  const { data: me } = useMe(accountId);
  const { data: notes } = usePoll(() => live().listNotifications(accountId), 4000, [accountId]);

  return (
    <>
      <PageHeader
        eyebrow="Customer portal · notification service"
        title="Notifications"
        sub="Every email the bank sent you, derived from events on your account. Delivery status included — if something says retrying, it will arrive."
      />
      {!me && <NoAccount accountId={accountId} />}

      <Section title="Your log" note="GET /api/notifications?accountId=…">
        <table className="register">
          <thead>
            <tr><th>Sent</th><th>Subject</th><th>Cause</th><th>Channel</th><th>Delivery</th></tr>
          </thead>
          <tbody>
            {notes?.map((n) => (
              <tr key={n.notificationId} className="row-in">
                <td className="mono" style={{ color: 'var(--muted)' }}>{ago(n.sentAt)}</td>
                <td>{n.subject}</td>
                <td><span className="badge">{n.causeEvent}</span></td>
                <td className="mono" style={{ color: 'var(--muted)' }}>{n.channel.toLowerCase()}</td>
                <td><DeliveryBadge status={n.deliveryStatus} /></td>
              </tr>
            ))}
            {notes && notes.length === 0 && (
              <tr><td colSpan={5} style={{ color: 'var(--muted)' }}>Nothing yet.</td></tr>
            )}
          </tbody>
        </table>
      </Section>
    </>
  );
}
