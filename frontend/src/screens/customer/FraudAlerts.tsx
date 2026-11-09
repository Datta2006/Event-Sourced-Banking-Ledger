import { Section, StatusBadge } from '../../components/ui';
import { ago } from '../../lib/format';
import type { AccountSummary } from '../../api/contract';
import { usePoll } from '../../lib/poll';
import { api } from '../../api/client';
import { mockApi } from '../../mock/server';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

/** Every customer screen scopes to this account. */
export function useMe(accountId: string) {
  return usePoll<AccountSummary | undefined>(async () => {
    const all = await live().listAccounts();
    return all.find((a) => a.accountId === accountId);
  }, 2500);
}

export function PageHeader({ eyebrow, title, sub }: { eyebrow: string; title: string; sub: string }) {
  return (
    <header>
      <div className="eyebrow">{eyebrow}</div>
      <h1 className="page-title">{title}</h1>
      <p className="page-sub">{sub}</p>
    </header>
  );
}

export function NoAccount({ accountId }: { accountId: string }) {
  return (
    <section className="section">
      <p style={{ color: 'var(--muted)' }}>
        Account <span className="mono">{accountId}</span> not found. Pick another
        account in the sidebar.
      </p>
    </section>
  );
}

export function FraudAlerts({ accountId }: { accountId: string }) {
  const { data: me } = useMe(accountId);
  const { data: cases } = usePoll(() => live().listFraudCases(accountId), 5000, [accountId]);

  return (
    <>
      <PageHeader
        eyebrow="Customer portal · fraud detection"
        title="Fraud alerts"
        sub="If your account was flagged, you'll see it here with a contact-the-bank prompt. Rule internals are deliberately not shown — that's the bank's side of the wall."
      />
      {!me && <NoAccount accountId={accountId} />}

      <Section title="Flags on your account" note="GET /api/fraud/cases?accountId=…">
        <table className="register">
          <thead>
            <tr><th>Flagged</th><th>Risk</th><th>Status</th><th>Action</th></tr>
          </thead>
          <tbody>
            {cases?.map((f) => (
              <tr key={f.caseId}>
                <td className="mono" style={{ color: 'var(--muted)' }}>{ago(f.flaggedAt)}</td>
                <td className="num">{f.riskScore ?? '—'}</td>
                <td><StatusBadge status={f.status} /></td>
                <td>
                  {f.status === 'OPEN' ? (
                    <span className="ok-text">
                      Contact the bank · a relationship manager will reach out — no action is needed online.
                    </span>
                  ) : (
                    <span className="section-note">handled by the bank</span>
                  )}
                </td>
              </tr>
            ))}
            {cases && cases.length === 0 && (
              <tr><td colSpan={4} style={{ color: 'var(--muted)' }}>No flags. All activity on this account looks clean.</td></tr>
            )}
          </tbody>
        </table>
      </Section>
    </>
  );
}
