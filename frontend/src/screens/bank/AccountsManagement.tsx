import { useState } from 'react';
import { Section, StatusBadge, ErrorNote } from '../../components/ui';
import { moneyINR, time } from '../../lib/format';import { usePoll } from '../../lib/poll';
import { api } from '../../api/client';
import { mockApi } from '../../mock/server';
import { PageHeader } from './shared';
import type { AccountSummary, EventView } from '../../api/contract';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

function StatementDrawer({ account, onClose }: { account: AccountSummary; onClose: () => void }) {
  const { data: events } = usePoll(() => live().listAccountEvents(account.accountId), 3000);
  const { data: txns } = usePoll(() => live().listTransactions(account.accountId), 3000);
  return (
    <Section
      title={`Statement · ${account.holderName}`}
      note={`GET /api/accounts/${account.accountId}/events · the fold over these is the balance`}
      right={<button className="btn" onClick={onClose}>Close</button>}
    >
      <div className="grid-2">
        <div>
          <div className="eyebrow" style={{ marginBottom: 'var(--sp-sm)' }}>Transactions</div>
          <table className="register">
            <tbody>
              {txns?.slice(0, 12).map((t) => (
                <tr key={t.transactionId}>
                  <td className="mono" style={{ color: 'var(--muted)' }}>{time(t.occurredAt)}</td>
                  <td>{t.type.replaceAll('_', ' ').toLowerCase()}</td>
                  <td className="num">{moneyINR(t.amount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div>
          <div className="eyebrow" style={{ marginBottom: 'var(--sp-sm)' }}>Events</div>
          <table className="register">
            <tbody>
              {events?.slice(0, 12).map((e: EventView) => (
                <tr key={e.eventId}>
                  <td className="mono" style={{ color: 'var(--muted)' }}>{time(e.occurredAt)}</td>
                  <td className="mono" style={{ fontSize: 11 }}>{e.type}</td>
                  <td className="mono" style={{ color: 'var(--muted)' }}>v{e.version}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </Section>
  );
}

export function AccountsManagement() {
  const { data: accounts, error } = usePoll(() => live().listAccounts(), 3000);
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null);
  const [openStatement, setOpenStatement] = useState<string | null>(null);

  const act = async (fn: () => Promise<unknown>, okText: string) => {
    setMsg(null);
    try {
      await fn();
      setMsg({ text: okText, ok: true });
    } catch (e) {
      setMsg({ text: e instanceof Error ? e.message : String(e), ok: false });
    }
  };

  return (
    <>
      <PageHeader
        eyebrow="Bank portal · account command"
        title="Accounts"
        sub="Every customer account across all region shards. Close appends AccountClosed (blocked while holds are open); KYC updates are audited as events."
      />

      {msg && <p className={msg.ok ? 'ok-text mono' : 'error-text mono'}>{msg.text}</p>}

      <Section title="All customers" note="GET /api/accounts · read model across shards">
        <table className="register">
          <thead>
            <tr><th>Holder</th><th>Account</th><th>Region</th><th className="num">Available</th><th className="num">Reserved</th><th>KYC</th><th>Status</th><th>Actions</th></tr>
          </thead>
          <tbody>
            {accounts?.map((a) => (
              <tr key={a.accountId}>
                <td style={{ fontWeight: 600 }}>{a.holderName}</td>
                <td className="mono">{a.accountId.slice(0, 8)}…</td>
                <td><span className="badge">{a.region}</span></td>
                <td className="num amount-credit">{moneyINR(a.availableBalance)}</td>
                <td className="num">{moneyINR(a.reservedBalance)}</td>
                <td>
                  <span className={`badge ${a.kycStatus === 'VERIFIED' ? 'green' : a.kycStatus === 'REJECTED' ? 'red' : 'amber'}`}>
                    {(a.kycStatus ?? 'PENDING').toLowerCase()}
                  </span>
                </td>
                <td><StatusBadge status={a.status ?? 'ACTIVE'} /></td>
                <td>
                  <span style={{ display: 'inline-flex', gap: 'var(--sp-xs)' }}>
                    {(a.kycStatus ?? 'PENDING') !== 'VERIFIED' && (
                      <button
                        className="btn"
                        onClick={() => act(() => live().setKycStatus(a.accountId, 'VERIFIED'), `KYC verified for ${a.holderName}`)}
                      >
                        Verify KYC
                      </button>
                    )}
                    <button className="btn" onClick={() => setOpenStatement(a.accountId)}>Statement</button>
                    {(a.status ?? 'ACTIVE') === 'ACTIVE' && (
                      <button
                        className="btn"
                        onClick={() =>
                          act(
                            () => live().closeAccount(a.accountId),
                            `AccountClosed appended for ${a.holderName}`,
                          )
                        }
                        title="Rejected while reserved balance > 0"
                      >
                        Close
                      </button>
                    )}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <ErrorNote error={error} />
      </Section>

      {openStatement && accounts && (
        <StatementDrawer
          account={accounts.find((a) => a.accountId === openStatement)!}
          onClose={() => setOpenStatement(null)}
        />
      )}
    </>
  );
}
