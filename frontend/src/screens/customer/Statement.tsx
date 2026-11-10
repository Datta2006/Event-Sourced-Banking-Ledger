import type { EventView, TransactionView } from '../../api/contract';
import { Section, ErrorNote } from '../../components/ui';
import { eventLabel, eventTone, moneyINR, time } from '../../lib/format';
import { usePoll } from '../../lib/poll';
import { api } from '../../api/client';
import { mockApi } from '../../mock/server';
import { PageHeader, NoAccount, useMe } from './shared';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

const TXN_SIGN: Record<TransactionView['type'], 1 | -1 | 0> = {
  DEPOSIT: 1,
  TRANSFER_IN: 1,
  LOAN_DISBURSEMENT: 1,
  WITHDRAWAL: -1,
  TRANSFER_OUT: -1,
  LOAN_REPAYMENT: -1,
  RESERVATION: 0,
  CAPTURE: 0,
  RELEASE: 0,
};

export function Statement({ accountId }: { accountId: string }) {
  const { data: me } = useMe(accountId);
  const { data: txns, error: txnErr } = usePoll(() => live().listTransactions(accountId), 3000);
  const { data: events, error: evErr } = usePoll(() => live().listAccountEvents(accountId), 3000);

  return (
    <>
      <PageHeader
        eyebrow="Customer portal · statement"
        title="Statement"
        sub="Every row is a transaction from the read model; every event below is the write side it was derived from."
      />
      {!me && <NoAccount accountId={accountId} />}

      <Section
        title="Transactions"
        note="GET /api/transactions?accountId=…"
        right={me && <span className="badge green"><span className="dot" />balance {moneyINR(me.availableBalance)}</span>}
      >
        <table className="register">
          <thead>
            <tr><th>When</th><th>Type</th><th className="num">Amount</th></tr>
          </thead>
          <tbody>
            {txns?.map((t) => {
              const sign = TXN_SIGN[t.type];
              return (
                <tr key={t.transactionId} className="row-in">
                  <td className="mono" style={{ color: 'var(--muted)' }}>{time(t.occurredAt)}</td>
                  <td>{t.type.replaceAll('_', ' ').toLowerCase()}</td>
                  <td className={`num ${sign > 0 ? 'amount-credit' : sign < 0 ? 'amount-debit' : ''}`}>
                    {sign > 0 ? '+' : sign < 0 ? '−' : ''}{moneyINR(t.amount)}
                  </td>
                </tr>
              );
            })}
            {txns && txns.length === 0 && (
              <tr><td colSpan={3} style={{ color: 'var(--muted)' }}>No transactions yet.</td></tr>
            )}
          </tbody>
        </table>
        <ErrorNote error={txnErr} />
      </Section>

      <Section
        title="Events behind it"
        note={`GET /api/accounts/${accountId}/events · "the balance is just a fold over these"`}
      >
        <table className="register">
          <thead>
            <tr><th>When</th><th>Event</th><th>v</th><th>Payload</th></tr>
          </thead>
          <tbody>
            {events?.slice(0, 60).map((e: EventView) => (
              <tr key={e.eventId}>
                <td className="mono" style={{ color: 'var(--muted)' }}>{time(e.occurredAt)}</td>
                <td><span className={`badge ${eventTone(e.type)}`}>{eventLabel(e.type)}</span></td>
                <td className="mono" style={{ color: 'var(--muted)' }}>v{e.version}</td>
                <td className="mono" style={{ color: 'var(--muted)', fontSize: 11 }}>
                  {JSON.stringify(e.payload)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <ErrorNote error={evErr} />
      </Section>
    </>
  );
}
