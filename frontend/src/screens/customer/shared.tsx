import type { AccountSummary } from '../../api/contract';
import { usePoll } from '../../lib/poll';
import { api } from '../../api/client';
import { mockApi } from '../../mock/server';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

/** Poll the acting account's summary; every customer screen scopes to this. */
export function useMe(accountId: string) {
  return usePoll<AccountSummary | undefined>(async () => {
    const all = await live().listAccounts();
    return all.find((a) => a.accountId === accountId);
  }, 2500);
}

export function PageHeader({
  eyebrow,
  title,
  sub,
}: {
  eyebrow: string;
  title: string;
  sub: string;
}) {
  return (
    <header>
      <div className="eyebrow">{eyebrow}</div>
      <h1 className="page-title">{title}</h1>
      <p className="page-sub">{sub}</p>
    </header>
  );
}

/** Empty state when the acting account is missing (e.g. closed or removed). */
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
