import { Section } from '../../components/ui';
import { TIER_NAMES } from '../../api/contract';
import { ago } from '../../lib/format';
import { usePoll } from '../../lib/poll';
import { api } from '../../api/client';
import { mockApi } from '../../mock/server';
import { PageHeader, NoAccount, useMe } from './shared';
import { useTrustTier } from './useTrustTier';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

export function TrustScore({ accountId }: { accountId: string }) {
  const { data: me } = useMe(accountId);
  const { data: trust } = useTrustTier(accountId);
  const { data: changes } = usePoll(() => live().listTrustScoreChanges(accountId, 30), 4000, [accountId]);

  return (
    <>
      <PageHeader
        eyebrow="Customer portal · trust score service"
        title="Trust score"
        sub="0–100, maintained from the events on your account: deposits and repayments raise it, defaults sink it. Your tier decides where you land in the loan queue."
      />
      {!me && <NoAccount accountId={accountId} />}

      {trust && (
        <section className="section">
          <div className="eyebrow">Current score</div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 'var(--sp-xl)', flexWrap: 'wrap' }}>
            <span className="mono balance-lg">{trust.score}</span>
            <span className={`badge ${trust.tier >= 2 ? 'green' : trust.tier === 1 ? 'blue' : 'amber'}`}>
              tier {trust.tier} · {TIER_NAMES[trust.tier]}
            </span>
            <span className="section-note">last changed {ago(trust.lastChangedAt)}</span>
          </div>
        </section>
      )}

      <Section title="What changed it" note="GET /api/trust-score/changes?accountId=… · your TrustScoreChanged feed">
        <table className="register">
          <thead>
            <tr><th>When</th><th className="num">Score</th><th>Tier</th><th>Reason</th></tr>
          </thead>
          <tbody>
            {changes?.map((c) => (
              <tr key={c.eventId} className="row-in">
                <td className="mono" style={{ color: 'var(--muted)' }}>{ago(c.changedAt)}</td>
                <td className="num">
                  {c.scoreBefore} → <strong>{c.scoreAfter}</strong>
                </td>
                <td>
                  <span className={`badge ${c.tierAfter >= 2 ? 'green' : c.tierAfter === 1 ? 'blue' : 'amber'}`}>
                    {TIER_NAMES[c.tierAfter as 0 | 1 | 2 | 3]}
                  </span>
                </td>
                <td className="mono" style={{ color: 'var(--muted)' }}>{c.reason}</td>
              </tr>
            ))}
            {changes && changes.length === 0 && (
              <tr><td colSpan={4} style={{ color: 'var(--muted)' }}>No changes recorded yet.</td></tr>
            )}
          </tbody>
        </table>
      </Section>
    </>
  );
}
