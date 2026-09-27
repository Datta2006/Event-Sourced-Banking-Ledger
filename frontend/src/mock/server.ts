/**
 * Contract-faithful mock backend. The real services are scaffolding-only (see
 * README "Status"), so this simulator implements the same ApiShape as
 * src/api/client.ts with a scripted demo scenario: a live event stream, a
 * self-advancing transfer saga, a loan queue that reorders (FCFS + tier) and
 * pops the head via the "Lua" debit, reservations that expire, and Kafka lag
 * jitter. Swap to the real gateway by setting VITE_API_BASE and building with
 * MOCK=off.
 */
import type {
  AccountSummary,
  AdminPoolView,
  EventView,
  FraudCase,
  KafkaTopicView,
  LoanQueueEntry,
  LoanQueueView,
  LoanRequestView,
  NotificationView,
  ReservationView,
  ServiceInstanceView,
  ShardView,
  TransactionView,
  TransferStatusView,
  TrustScoreView,
} from '../api/contract';
import { TIER_NAMES } from '../api/contract';

const uuid = () => crypto.randomUUID();
const now = () => new Date().toISOString();

export type ApiShape = {
  listAccounts(): Promise<AccountSummary[]>;
  listRecentEvents(since?: string, limit?: number): Promise<EventView[]>;
  listTransactions(accountId?: string, page?: number, size?: number): Promise<TransactionView[]>;
  openAccount(body: { holderName: string; registrationIp: string; initialDeposit: number }): Promise<AccountSummary>;
  deposit(accountId: string, amount: number): Promise<void>;
  withdraw(accountId: string, amount: number): Promise<void>;
  transfer(body: { fromAccountId: string; toAccountId: string; amount: number }): Promise<{ transferId: string }>;
  getTransferStatus(transferId: string): Promise<TransferStatusView>;
  getLoanQueue(): Promise<LoanQueueView>;
  getAdminPool(): Promise<AdminPoolView>;
  requestLoan(accountId: string, amount: number): Promise<LoanRequestView>;
  createReservation(accountId: string, amount: number, ttlMinutes?: number): Promise<ReservationView>;
  getReservation(id: string): Promise<ReservationView>;
  captureReservation(id: string, offlineToken: string, merchantAccountId: string): Promise<{ capturedAt: string }>;
  getTrustScore(accountId: string): Promise<TrustScoreView>;
  listFraudCases(): Promise<FraudCase[]>;
  listNotifications(accountId?: string): Promise<NotificationView[]>;
  listServiceInstances(): Promise<ServiceInstanceView[]>;
  listShards(): Promise<ShardView[]>;
  listKafkaTopics(): Promise<KafkaTopicView[]>;
};

// ------------------------------------------------------------------ seed data
const ACCOUNTS: AccountSummary[] = [
  { accountId: 'a1', holderName: 'Aarav Sharma', region: 'APAC', availableBalance: 48250.5, reservedBalance: 2000, updatedAt: now() },
  { accountId: 'b2', holderName: 'Meera Iyer', region: 'EU', availableBalance: 12980.25, reservedBalance: 0, updatedAt: now() },
  { accountId: 'c3', holderName: 'Dmitri Volkov', region: 'AMER', availableBalance: 73400, reservedBalance: 1500, updatedAt: now() },
  { accountId: 'd4', holderName: 'Lucia Fernandez', region: 'EU', availableBalance: 9210.75, reservedBalance: 0, updatedAt: now() },
];

const REGION_IPS: Record<string, string> = { APAC: '103.21.244.10', EU: '51.15.208.14', AMER: '24.132.10.5' };
void REGION_IPS;

const TRUST: Record<string, TrustScoreView> = {
  a1: { accountId: 'a1', score: 94, tier: 3, lastChangedAt: now() },
  b2: { accountId: 'b2', score: 76, tier: 2, lastChangedAt: now() },
  c3: { accountId: 'c3', score: 63, tier: 1, lastChangedAt: now() },
  d4: { accountId: 'd4', score: 22, tier: 0, lastChangedAt: now() },
};

const TRANSACTIONS: TransactionView[] = [];
const TRANSFERS = new Map<string, TransferStatusView>();
const RESERVATIONS = new Map<string, ReservationView>();
const LOANS: LoanRequestView[] = [];
const EVENTS: EventView[] = [];
const FRAUD: FraudCase[] = [];
const NOTIFICATIONS: NotificationView[] = [];

let pool: AdminPoolView = { balance: 50000, currency: 'INR', updatedAt: now() };

function pushEvent(type: string, aggregateId: string, payload: Record<string, unknown>) {
  EVENTS.unshift({
    eventId: uuid(),
    aggregateId,
    version: EVENTS.length + 1,
    type,
    payload,
    occurredAt: now(),
    region: ACCOUNTS.find((a) => a.accountId === aggregateId)?.region ?? 'EU',
  });
  if (EVENTS.length > 400) EVENTS.pop();
}

function notify(accountId: string, subject: string, causeEvent: string) {
  NOTIFICATIONS.unshift({ notificationId: uuid(), accountId, channel: 'EMAIL', subject, causeEvent, sentAt: now() });
}

// Seed: some history so tables are not empty
pushEvent('AccountOpened', 'a1', { holderName: 'Aarav Sharma', initialDeposit: 45000 });
pushEvent('MoneyDeposited', 'a1', { amount: 3250.5 });
pushEvent('LoanDisbursed', 'a1', { amount: 15000 });
pushEvent('TrustScoreChanged', 'a1', { score: 94, tier: 3 });
pushEvent('MoneyDeposited', 'b2', { amount: 12980.25 });
pushEvent('FraudRuleTriggered', 'c3', { rule: 'VELOCITY_3_WITHDRAWALS_60S' });
FRAUD.push({ caseId: uuid(), accountId: 'c3', rule: 'VELOCITY_3_WITHDRAWALS_60S', triggeredByEvent: 'MoneyWithdrawn', status: 'OPEN', flaggedAt: now() });
notify('a1', 'Your loan was disbursed', 'LoanApproved');
notify('c3', 'Unusual activity on your account', 'FraudCaseOpened');

// ------------------------------------------------------------- loan queue sim
// score = (3 - tier) * 1e9 + requestedAtMillis  (tier first, FCFS second)
function loanScore(e: { trustTier?: number; requestedAt?: string }) {
  return (3 - (e.trustTier ?? 0)) * 1e9 + new Date(e.requestedAt ?? 0).getTime();
}

type LoanPhase = 'QUEUED' | 'WAITING_FOR_FUNDS' | 'APPROVED' | 'DISBURSED' | 'REJECTED';
const loanPhase = new Map<string, LoanPhase>();
LOANS.forEach((l) => loanPhase.set(l.loanId, 'QUEUED'));

async function loanTick() {
  const active = LOANS.filter((l) => loanPhase.get(l.loanId) === 'QUEUED');
  if (active.length >= 2 && Math.random() < 0.4) {
    active.sort((x, y) => loanScore(x) - loanScore(y));
    const head = active[0];
    if (pool.balance >= head.amount) {
      pool = { ...pool, balance: pool.balance - head.amount, updatedAt: now() };
      loanPhase.set(head.loanId, 'APPROVED');
      pushEvent('LoanApproved', head.accountId, { loanId: head.loanId, amount: head.amount });
      notify(head.accountId, 'Loan approved', 'LoanApproved');
      setTimeout(() => {
        loanPhase.set(head.loanId, 'DISBURSED');
        pushEvent('LoanDisbursed', head.accountId, { loanId: head.loanId, amount: head.amount });
      }, 4000);
    } else {
      loanPhase.set(head.loanId, 'WAITING_FOR_FUNDS');
      pushEvent('LoanDeferred', head.accountId, { loanId: head.loanId, reason: 'INSUFFICIENT_POOL' });
    }
  }
  // occasional new request arriving at the back
  if (Math.random() < 0.25) {
    const acct = ACCOUNTS[Math.floor(Math.random() * ACCOUNTS.length)];
    const tier = TRUST[acct.accountId].tier;
    const req: LoanRequestView = {
      loanId: uuid(),
      accountId: acct.accountId,
      amount: 2000 + Math.floor(Math.random() * 8) * 1000,
      trustTier: tier,
      status: 'QUEUED',
      requestedAt: now(),
    };
    LOANS.push(req);
    loanPhase.set(req.loanId, 'QUEUED');
    pushEvent('LoanRequested', acct.accountId, { loanId: req.loanId, amount: req.amount, tier: TIER_NAMES[tier] });
  }
}
setInterval(loanTick, 3000);

// ------------------------------------------------------------- transfer sim
setInterval(() => {
  TRANSFERS.forEach((t) => {
    if (t.status === 'INITIATED') {
      t.status = 'SOURCE_DEBITED';
      pushEvent('TransferSourceDebited', t.fromAccountId, { transferId: t.transferId, amount: t.amount });
    } else if (t.status === 'SOURCE_DEBITED') {
      t.status = 'TARGET_CREDITED';
      pushEvent('TransferTargetCredited', t.toAccountId, { transferId: t.transferId, amount: t.amount });
    } else if (t.status === 'TARGET_CREDITED') {
      t.status = 'COMPLETED';
      pushEvent('TransferCompleted', t.fromAccountId, { transferId: t.transferId });
    }
    t.updatedAt = now();
  });
}, 2500);

// ------------------------------------------------------- reservation expiry
setInterval(() => {
  RESERVATIONS.forEach((r) => {
    if (r.status === 'RESERVED' && new Date(r.expiresAt) < new Date()) {
      r.status = 'RELEASED';
      const acct = ACCOUNTS.find((a) => a.accountId === r.accountId);
      if (acct) {
        acct.availableBalance += r.amount;
        acct.reservedBalance -= r.amount;
      }
      pushEvent('FundsReleased', r.accountId, { reservationId: r.reservationId, amount: r.amount });
    }
  });
}, 2000);

// ------------------------------------------------------------ event stream
setInterval(() => {
  const acct = ACCOUNTS[Math.floor(Math.random() * ACCOUNTS.length)];
  const roll = Math.random();
  if (roll < 0.45) {
    const amt = Math.round(Math.random() * 400000) / 100;
    acct.availableBalance += amt;
    pushEvent('MoneyDeposited', acct.accountId, { amount: amt });
    TRANSACTIONS.unshift({ transactionId: uuid(), accountId: acct.accountId, type: 'DEPOSIT', amount: amt, occurredAt: now() });
  } else if (roll < 0.7) {
    const amt = Math.round(Math.random() * 150000) / 100;
    if (acct.availableBalance >= amt) {
      acct.availableBalance -= amt;
      pushEvent('MoneyWithdrawn', acct.accountId, { amount: amt });
      TRANSACTIONS.unshift({ transactionId: uuid(), accountId: acct.accountId, type: 'WITHDRAWAL', amount: amt, occurredAt: now() });
    }
  } else if (roll < 0.8) {
    const delta = Math.floor(Math.random() * 7) - 2;
    const t = TRUST[acct.accountId];
    t.score = Math.max(0, Math.min(100, t.score + delta));
    t.tier = t.score > 90 ? 3 : t.score > 70 ? 2 : t.score > 40 ? 1 : 0;
    t.lastChangedAt = now();
    pushEvent('TrustScoreChanged', acct.accountId, { score: t.score, tier: t.tier });
  } else if (roll < 0.9) {
    pool = { ...pool, balance: pool.balance + 10000, updatedAt: now() };
    pushEvent('AdminPoolReplenished', 'admin-pool', { amount: 10000 });
  } else {
    pushEvent('Heartbeat', acct.accountId, { note: 'RFairLock reaper ok' });
  }
}, 2200);

// ------------------------------------------------------------------ handler
export const mockApi: ApiShape = {
  async listAccounts() {
    return ACCOUNTS.map((a) => ({ ...a }));
  },

  async listRecentEvents(since, limit = 50) {
    const filtered = since ? EVENTS.filter((e) => e.occurredAt > since) : EVENTS;
    return filtered.slice(0, limit).map((e) => ({ ...e }));
  },

  async listTransactions(accountId, page = 0, size = 50) {
    const filtered = accountId ? TRANSACTIONS.filter((t) => t.accountId === accountId) : TRANSACTIONS;
    return filtered.slice(page * size, (page + 1) * size);
  },

  async openAccount(body) {
    const region = body.registrationIp.startsWith('103.') ? 'APAC' : body.registrationIp.startsWith('51.') ? 'EU' : 'AMER';
    const acct: AccountSummary = {
      accountId: uuid(),
      holderName: body.holderName,
      region,
      availableBalance: body.initialDeposit,
      reservedBalance: 0,
      updatedAt: now(),
    };
    ACCOUNTS.push(acct);
    TRUST[acct.accountId] = { accountId: acct.accountId, score: 50, tier: 1, lastChangedAt: now() };
    pushEvent('AccountOpened', acct.accountId, { holderName: body.holderName, region });
    TRANSACTIONS.unshift({ transactionId: uuid(), accountId: acct.accountId, type: 'DEPOSIT', amount: body.initialDeposit, occurredAt: now() });
    return acct;
  },

  async deposit(accountId, amount) {
    const acct = ACCOUNTS.find((a) => a.accountId === accountId);
    if (!acct) throw new Error('404 account not found');
    acct.availableBalance += amount;
    pushEvent('MoneyDeposited', accountId, { amount });
    TRANSACTIONS.unshift({ transactionId: uuid(), accountId, type: 'DEPOSIT', amount, occurredAt: now() });
  },

  async withdraw(accountId, amount) {
    const acct = ACCOUNTS.find((a) => a.accountId === accountId);
    if (!acct) throw new Error('404 account not found');
    if (acct.availableBalance < amount) throw new Error('400 insufficient balance');
    acct.availableBalance -= amount;
    pushEvent('MoneyWithdrawn', accountId, { amount });
    TRANSACTIONS.unshift({ transactionId: uuid(), accountId, type: 'WITHDRAWAL', amount, occurredAt: now() });
  },

  async transfer(body) {
    const from = ACCOUNTS.find((a) => a.accountId === body.fromAccountId);
    if (!from || from.availableBalance < body.amount) throw new Error('400 insufficient balance');
    from.availableBalance -= body.amount;
    const to = ACCOUNTS.find((a) => a.accountId === body.toAccountId);
    if (to) to.availableBalance += body.amount;
    const id = uuid();
    TRANSFERS.set(id, {
      transferId: id,
      fromAccountId: body.fromAccountId,
      toAccountId: body.toAccountId,
      amount: body.amount,
      status: 'INITIATED',
      startedAt: now(),
      updatedAt: now(),
      failureReason: null,
    });
    pushEvent('TransferInitiated', body.fromAccountId, { transferId: id, amount: body.amount });
    TRANSACTIONS.unshift({ transactionId: uuid(), accountId: body.fromAccountId, type: 'TRANSFER_OUT', amount: body.amount, occurredAt: now() });
    if (to) TRANSACTIONS.unshift({ transactionId: uuid(), accountId: to.accountId, type: 'TRANSFER_IN', amount: body.amount, occurredAt: now() });
    return { transferId: id };
  },

  async getTransferStatus(id) {
    const t = TRANSFERS.get(id);
    if (!t) throw new Error('404 transfer not found');
    return { ...t };
  },

  async getLoanQueue() {
    const entries: LoanQueueEntry[] = LOANS.filter((l) => ['QUEUED', 'WAITING_FOR_FUNDS'].includes(loanPhase.get(l.loanId) ?? ''))
      .sort((a, b) => loanScore(a) - loanScore(b))
      .map((l, i) => ({
        ...l,
        status: loanPhase.get(l.loanId) as LoanQueueEntry['status'],
        position: i + 1,
      }));
    return { entries, generatedAt: now() };
  },

  async getAdminPool() {
    return { ...pool };
  },

  async requestLoan(accountId, amount) {
    const acct = ACCOUNTS.find((a) => a.accountId === accountId);
    if (!acct) throw new Error('404 account not found');
    const tier = TRUST[accountId].tier;
    const loan: LoanRequestView = { loanId: uuid(), accountId, amount, trustTier: tier, status: 'QUEUED', requestedAt: now() };
    LOANS.push(loan);
    loanPhase.set(loan.loanId, 'QUEUED');
    pushEvent('LoanRequested', accountId, { loanId: loan.loanId, amount, tier: TIER_NAMES[tier] });
    return loan;
  },

  async createReservation(accountId, amount, ttlMinutes = 30) {
    const acct = ACCOUNTS.find((a) => a.accountId === accountId);
    if (!acct) throw new Error('404 account not found');
    if (acct.availableBalance < amount) throw new Error('400 insufficient funds');
    acct.availableBalance -= amount;
    acct.reservedBalance += amount;
    const r: ReservationView = {
      reservationId: uuid(),
      accountId,
      amount,
      status: 'RESERVED',
      expiresAt: new Date(Date.now() + ttlMinutes * 60_000).toISOString(),
      capturedAt: null,
      offlineToken: `eyJhbGciOiJSUzI1NiJ9.${btoa(JSON.stringify({ reservationId: uuid(), accountId, amount, expiresAt: ttlMinutes }))}.SIG`,
    };
    RESERVATIONS.set(r.reservationId, r);
    pushEvent('FundsReserved', accountId, { reservationId: r.reservationId, amount });
    TRANSACTIONS.unshift({ transactionId: uuid(), accountId, type: 'RESERVATION', amount, occurredAt: now() });
    return { ...r };
  },

  async getReservation(id) {
    const r = RESERVATIONS.get(id);
    if (!r) throw new Error('404 reservation not found');
    return { ...r, offlineToken: r.status === 'RESERVED' ? r.offlineToken : undefined };
  },

  async captureReservation(id, _offlineToken, merchantAccountId) {
    const r = RESERVATIONS.get(id);
    if (!r) throw new Error('404 reservation not found');
    if (r.status === 'CAPTURED') throw new Error('409 already captured (replay rejected)');
    if (r.status !== 'RESERVED') throw new Error('410 reservation expired');
    r.status = 'CAPTURED';
    r.capturedAt = now();
    const acct = ACCOUNTS.find((a) => a.accountId === r.accountId);
    if (acct) acct.reservedBalance -= r.amount;
    const merchant = ACCOUNTS.find((a) => a.accountId === merchantAccountId);
    if (merchant) merchant.availableBalance += r.amount;
    pushEvent('FundsCaptured', r.accountId, { reservationId: id, merchant: merchantAccountId });
    TRANSACTIONS.unshift({ transactionId: uuid(), accountId: r.accountId, type: 'CAPTURE', amount: r.amount, occurredAt: now() });
    notify(r.accountId, 'Offline payment captured', 'FundsCaptured');
    return { capturedAt: r.capturedAt };
  },

  async getTrustScore(accountId) {
    const t = TRUST[accountId];
    if (!t) throw new Error('404 no trust score');
    return { ...t };
  },

  async listFraudCases() {
    return FRAUD.map((f) => ({ ...f }));
  },

  async listNotifications(accountId) {
    return (accountId ? NOTIFICATIONS.filter((n) => n.accountId === accountId) : NOTIFICATIONS).map((n) => ({ ...n }));
  },

  async listServiceInstances() {
    const services = [
      ['api-gateway', 8080],
      ['account-command-service', 8081],
      ['ledger-query-service', 8082],
      ['fraud-detection-service', 8083],
      ['notification-service', 8084],
      ['loan-service', 8085],
      ['payment-reservation-service', 8086],
      ['trust-score-service', 8087],
    ] as const;
    return services.map(([serviceId, port], i) => ({
      serviceId,
      instanceId: `${serviceId}-${i}`,
      host: 'localhost',
      port,
      status: i === 5 && Math.random() < 0.06 ? 'STARTING' : 'UP',
      registeredAt: new Date(Date.now() - 3600_000).toISOString(),
    }));
  },

  async listShards() {
    return [
      { shardId: 'ledger_apac', region: 'APAC', host: 'postgres-apac', port: 5432, status: 'UP', rowCounts: { ledger_events: 18342 } },
      { shardId: 'ledger_eu', region: 'EU', host: 'postgres-eu', port: 5433, status: 'UP', rowCounts: { ledger_events: 9721 } },
      { shardId: 'ledger_amer', region: 'AMER', host: 'postgres-amer', port: 5434, status: 'UP', rowCounts: { ledger_events: 4188 } },
    ];
  },

  async listKafkaTopics() {
    const jitter = () => Math.max(0, Math.floor(Math.random() * 40));
    return [
      { topic: 'account.commands', partitions: 16, groups: [{ groupId: 'account-command', lag: jitter() }] },
      { topic: 'account.events', partitions: 8, groups: [{ groupId: 'ledger-query', lag: jitter() }, { groupId: 'trust-score', lag: jitter() }, { groupId: 'fraud-detection', lag: jitter() }] },
      { topic: 'loan.events', partitions: 4, groups: [{ groupId: 'trust-score', lag: jitter() }] },
      { topic: 'payment.events', partitions: 4, groups: [{ groupId: 'fraud-detection', lag: jitter() }] },
    ];
  },
};
