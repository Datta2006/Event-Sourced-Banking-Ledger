import { Section, ErrorNote } from '../../components/ui';
import { TIER_NAMES } from '../../api/contract';
import { ago } from '../../lib/format';
import { usePoll } from '../../lib/poll';
import { api } from '../../api/client';
import { mockApi } from '../../mock/server';
import { PageHeader } from './shared';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

export function TrustAudit() {
  const { data: changes, error } = usePoll(() => live().listTrustScoreChanges(undefined, 100), 3000);
  const { data: accounts } = usePoll(() => live().listAccounts(), 10000);
  const nameOf = (id: string) => accounts?.find((a) => a.accountId === id)?.holderName ?? id;

  return (
    <>
      <PageHeader
        eyebrow="Trust Score Service"
        title="Trust score audit"
        sub="Cross-customer feed of TrustScoreChanged: exactly why a tier moved, when, and what it moved from. This is the ledger the loan queue orders against."
      />

      <Section title="Changes" note="GET /api/trust-score/changes · newest first">
        <table className="register">
          <thead>
            <tr><th>When</th><th>Customer</th><th className="num">Score</th><th>Tier</th><th>Reason</th></tr>
          </thead>
          <tbody>
            {changes?.map((c) => {
              const moved = c.tierBefore !== c.tierAfter;
              const dir = c.scoreAfter > c.scoreBefore ? 'amount-credit' : c.scoreAfter < c.scoreBefore ? 'amount-debit' : '';
              return (
                <tr key={c.eventId} className="row-in">
                  <td className="mono" style={{ color: 'var(--muted)' }}>{ago(c.changedAt)}</td>
                  <td>{nameOf(c.accountId)}</td>
                  <td className={`num ${dir}`}>{c.scoreBefore} → {c.scoreAfter}</td>
                  <td>
                    {moved && (
                      <span className={`badge ${c.tierAfter > c.tierBefore ? 'green' : 'red'}`}>
                        {TIER_NAMES[c.tierBefore as 0 | 1 | 2 | 3]} → {TIER_NAMES[c.tierAfter as 0 | 1 | 2 | 3]}
                      </span>
                    )}
                    {!moved && <span className="section-note">within {TIER_NAMES[c.tierAfter as 0 | 1 | 2 | 3]}</span>}
                  </td>
                  <td className="mono" style={{ color: 'var(--muted)' }}>{c.reason}</td>
                </tr>
              );
            })}
            {changes && changes.length === 0 && (
              <tr><td colSpan={5} style={{ color: 'var(--muted)' }}>No trust changes recorded yet.</td></tr>
            )}
          </tbody>
        </table>
        <ErrorNote error={error} />
      </Section>
    </>
  );
}
