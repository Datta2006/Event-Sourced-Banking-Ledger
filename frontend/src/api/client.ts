/**
 * Typed API client. Every call's request/response is typed from the generated
 * contract. Base URL: the API Gateway (port 8080) routes /api/* to services.
 * With no backend running, swap in the mock transport (src/mock/server.ts).
 */
import type {
  AccountSnapshot,
  AccountSummary,
  AdminPoolView,
  EventView,
  FraudCase,
  KafkaTopicView,
  LoanQueueView,
  LoanRequestView,
  NotificationView,
  ReservationView,
  ServiceInstanceView,
  ShardView,
  TransactionView,
  TransferStatusView,
  TrustScoreView,
} from './contract';

const BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080';

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

async function request<T>(
  path: string,
  init?: RequestInit & { json?: unknown },
): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'Idempotency-Key': crypto.randomUUID(),
    ...(init?.headers as Record<string, string> | undefined),
  };
  let body: string | undefined;
  if (init?.json !== undefined) {
    body = JSON.stringify(init.json);
  }
  const res = await fetch(`${BASE}${path}`, { ...init, headers, body });
  if (!res.ok) {
    throw new ApiError(res.status, await res.text().catch(() => res.statusText));
  }
  if (res.status === 204) return undefined as T;
  return (await res.json().catch(() => undefined)) as T;
}

export const api = {
  // ledger-query (read model)
  listAccounts: () => request<AccountSummary[]>('/api/accounts'),
  listRecentEvents: (since?: string, limit = 50) =>
    request<EventView[]>(
      `/api/events?limit=${limit}${since ? `&since=${encodeURIComponent(since)}` : ''}`,
    ),
  listTransactions: (accountId?: string, page = 0, size = 50) =>
    request<TransactionView[]>(
      `/api/transactions?page=${page}&size=${size}${accountId ? `&accountId=${accountId}` : ''}`,
    ),
  listAccountEvents: (accountId: string) =>
    request<EventView[]>(`/api/accounts/${accountId}/events`),

  // account-command (write side)
  openAccount: (body: {
    holderName: string;
    registrationIp: string;
    initialDeposit: number;
  }) => request<AccountSnapshot>('/api/accounts', { method: 'POST', json: body }),
  deposit: (accountId: string, amount: number) =>
    request<unknown>(`/api/accounts/${accountId}/deposits`, {
      method: 'POST',
      json: { amount },
    }),
  withdraw: (accountId: string, amount: number) =>
    request<unknown>(`/api/accounts/${accountId}/withdrawals`, {
      method: 'POST',
      json: { amount },
    }),
  transfer: (body: {
    fromAccountId: string;
    toAccountId: string;
    amount: number;
  }) =>
    request<{ transferId?: string } | undefined>('/api/transfers', {
      method: 'POST',
      json: body,
    }),
  getTransferStatus: (transferId: string) =>
    request<TransferStatusView>(`/api/transfers/${transferId}`),

  // loan-service
  getLoanQueue: () => request<LoanQueueView>('/api/loans/queue'),
  getAdminPool: () => request<AdminPoolView>('/api/loans/pool'),
  requestLoan: (accountId: string, amount: number) =>
    request<LoanRequestView>('/api/loans', {
      method: 'POST',
      json: { accountId, amount },
    }),

  // payment-reservation
  createReservation: (accountId: string, amount: number, ttlMinutes = 30) =>
    request<ReservationView>('/api/reservations', {
      method: 'POST',
      json: { accountId, amount, ttlMinutes },
    }),
  getReservation: (reservationId: string) =>
    request<ReservationView>(`/api/reservations/${reservationId}`),
  captureReservation: (
    reservationId: string,
    offlineToken: string,
    merchantAccountId: string,
  ) =>
    request<unknown>(`/api/reservations/${reservationId}/capture`, {
      method: 'POST',
      json: { offlineToken, merchantAccountId },
    }),

  // trust-score
  getTrustScore: (accountId: string) =>
    request<TrustScoreView>(`/api/trust-score/${accountId}`),

  // fraud / notifications
  listFraudCases: () => request<FraudCase[]>('/api/fraud/cases'),
  listNotifications: (accountId?: string) =>
    request<NotificationView[]>(
      `/api/notifications${accountId ? `?accountId=${accountId}` : ''}`,
    ),

  // system-ops
  listServiceInstances: () =>
    request<ServiceInstanceView[]>('/api/ops/services'),
  listShards: () => request<ShardView[]>('/api/ops/shards'),
  listKafkaTopics: () => request<KafkaTopicView[]>('/api/ops/kafka'),
};
