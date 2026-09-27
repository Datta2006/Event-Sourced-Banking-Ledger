import type { TrustScoreView } from '../../api/contract';
import { usePoll } from '../../lib/poll';
import { api } from '../../api/client';
import { mockApi } from '../../mock/server';

const live = () => (import.meta.env.VITE_API_BASE ? api : mockApi);

/** Poll the acting account's trust score/tier. */
export function useTrustTier(accountId: string) {
  return usePoll<TrustScoreView | undefined>(
    () => live().getTrustScore(accountId).catch(() => undefined),
    4000,
    [accountId],
  );
}
