/**
 * Deterministic offline demo backend.
 *
 * The real services are scaffolding-only (see README "Status"), so this module
 * implements the exact ApiShape of src/api/client.ts with fixed seed data.
 * There is NO Math.random(), no background timers and no live customer. Every
 * table is prefilled; user actions mutate this in-memory copy and append
 * events. Transfers and approved loans advance lazily on read using elapsed
 * time, so a click still produces a believable saga without a moving clock.
 *
 * Swap to the real gateway by setting VITE_API_BASE for a production build.
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
  PoolReplenishmentView,
  ReservationView,
  ServiceInstanceView,
  ShardView,
  TransactionView,
  TransferStatusView,
  TrustScoreChangeView,
  TrustScoreView,
} from '../api/contract';
import { TIER_NAMES } from '../api/contract';

const BASE_TIME = Date.now();
const now = () => new Date().toISOString();
/** ISO timestamp `minutesAgo` before this page was opened. */
const ago = (minutesAgo: number) => new Date(BASE_TIME - minutesAgo * 60_000).toISOString();

let idSeq = 0;
const nextId = (prefix: string) => `${prefix}-${String(++idSeq).padStart(4, '0')}`;

export type ApiShape = {
  listAccounts(): Promise<AccountSummary[]>;
  listRecentEvents(since?: string, limit?: number): Promise<EventView[]>;
  listTransactions(accountId?: string, page?: number, size?: number): Promise<TransactionView[]>;
  listAccountEvents(accountId: string): Promise<EventView[]>;
  openAccount(body: { holderName: string; registrationIp: string; initialDeposit: number }): Promise<AccountSummary>;
  deposit(accountId: string, amount: number): Promise<void>;
  withdraw(accountId: string, amount: number): Promise<void>;
  closeAccount(accountId: string): Promise<AccountSummary>;
  setKycStatus(accountId: string, kycStatus: 'PENDING' | 'VERIFIED' | 'REJECTED', note?: string): Promise<AccountSummary>;
  transfer(body: { fromAccountId: string; toAccountId: string; amount: number }): Promise<{ transferId: string }>;
  getTransferStatus(transferId: string): Promise<TransferStatusView>;
  getLoanQueue(): Promise<LoanQueueView>;
  getAdminPool(): Promise<AdminPoolView>;
  requestLoan(accountId: string, amount: number, purpose?: string): Promise<LoanRequestView>;
  listLoans(accountId?: string, status?: LoanRequestView['status']): Promise<LoanRequestView[]>;
  decideLoan(loanId: string, decision: 'APPROVE' | 'REJECT', note?: string): Promise<LoanRequestView>;
  repayLoan(loanId: string, amount: number): Promise<LoanRequestView>;
  replenishPool(amount: number): Promise<AdminPoolView>;
  listPoolHistory(): Promise<PoolReplenishmentView[]>;
  listReservations(accountId?: string, status?: ReservationView['status']): Promise<ReservationView[]>;
  createReservation(accountId: string, amount: number, ttlMinutes?: number): Promise<ReservationView>;
  getReservation(id: string): Promise<ReservationView>;
  captureReservation(id: string, offlineToken: string, merchantAccountId: string): Promise<{ capturedAt: string }>;
  voidReservation(id: string): Promise<ReservationView>;
  getTrustScore(accountId: string): Promise<TrustScoreView>;
  listTrustScoreChanges(accountId?: string, limit?: number): Promise<TrustScoreChangeView[]>;
  listFraudCases(accountId?: string, status?: FraudCase['status']): Promise<FraudCase[]>;
  reviewFraudCase(caseId: string, status: 'REVIEWED' | 'DISMISSED', note?: string): Promise<FraudCase>;
  listNotifications(accountId?: string, causeEvent?: string): Promise<NotificationView[]>;
  listServiceInstances(): Promise<ServiceInstanceView[]>;
  listShards(): Promise<ShardView[]>;
  listKafkaTopics(): Promise<KafkaTopicView[]>;
};

// ------------------------------------------------------------------ seed data
const REGIONS = ['APAC', 'EU', 'AMER'] as const;

const HOLDER_NAMES = [
  'Aarav Sharma', 'Raju Reddy', 'Shreyas Iyer', 'Ankit Sharma', 'Chandan Kumar',
  'Priya Patel', 'Meera Krishnamurthy', 'Ojaswi Nair', 'Farhan Ahmed', 'Sneha Deshpande',
  'Vikram Rao', 'Neha Bhat', 'Sujata Iyer', 'Ravi Menon', 'Geeta Chaudhary',
  'Arjun Kapoor', 'Divya Menon', 'Kabir Malhotra', 'Isha Verma', 'Rohan Gupta',
  'Lakshmi Narayan', 'Aditya Kulkarni', 'Pooja Joshi', 'Sameer Khan',
  'Tanvi Shah', 'Manish Tiwari', 'Ritika Bose', 'Harish Pillai',
  'Ananya Sen', 'Dev Patel', 'Kavya Rajan', 'Naveen Sethi',
  'Shruti Mishra', 'Yash Thakur', 'Maya Fernandes', 'Imran Sheikh',
];

const PURPOSES = [
  'Home renovation', 'Laptop purchase', 'Debt consolidation', 'Vehicle financing', 'Medical expenses',
  'Education funding', 'Business expansion', 'Emergency savings', 'Property down payment', 'Family relocation',
  'Tourism investment', 'Hardware procurement', 'Network expansion', 'App development', 'Partnership setup',
];

const ACCOUNTS: AccountSummary[] = HOLDER_NAMES.map((name, i) => {
  const region = REGIONS[Math.floor(i / 12)];
  const isClosed = i % 6 === 5;
  const kycStatus: AccountSummary['kycStatus'] =
    i === 4 || i === 22 ? 'PENDING' : i === 9 ? 'REJECTED' : 'VERIFIED';
  const base = 21500 + ((i * 7831) % 168000);
  return {
    accountId: `a${String(i + 1).padStart(2, '0')}`,
    holderName: name,
    region,
    availableBalance: i === 0 ? 48250.5 : Math.round(base * 100) / 100,
    reservedBalance: i === 0 ? 0 : 0,
    status: isClosed ? 'CLOSED' : 'ACTIVE',
    kycStatus,
    updatedAt: ago(15 + i * 3),
  };
});

const byId = (id: string) => ACCOUNTS.find((a) => a.accountId === id);

const TRUST: Record<string, TrustScoreView> = {};
for (let i = 0; i < ACCOUNTS.length; i++) {
  const accountId = ACCOUNTS[i].accountId;
  const score = i === 0 ? 94 : 42 + ((i * 13) % 51);
  const tier = score > 90 ? 3 : score > 70 ? 2 : score > 40 ? 1 : 0;
  TRUST[accountId] = { accountId, score, tier, lastChangedAt: ago(60 + i * 7) };
}

const TRUST_CHANGES: TrustScoreChangeView[] = [];
const TRANSACTIONS: TransactionView[] = [];
const TRANSFERS = new Map<string, TransferStatusView>();
const RESERVATIONS = new Map<string, ReservationView>();
const LOANS: LoanRequestView[] = [];
const EVENTS: EventView[] = [];
const FRAUD: FraudCase[] = [];
const NOTIFICATIONS: NotificationView[] = [];
const POOL_HISTORY: PoolReplenishmentView[] = [];

/** Internal bookkeeping for lazy state transitions. */
const APPROVED_AT = new Map<string, number>();
const SHORTENED_EVENTS = new Set<string>();

let pool: AdminPoolView = { balance: 300000, currency: 'INR', updatedAt: ago(30) };

function appendEvent(type: string, aggregateId: string, payload: Record<string, unknown>, occurredAt = now()) {
  const version = EVENTS.filter((e) => e.aggregateId === aggregateId).length + 1;
  EVENTS.push({
    eventId: nextId('evt'),
    aggregateId,
    version,
    type,
    payload,
    occurredAt,
    region: byId(aggregateId)?.region ?? 'EU',
  });
  if (EVENTS.length > 400) EVENTS.shift();
}

function recordTrustChange(accountId: string, scoreBefore: number, tierBefore: number, reason: string, changedAt = now()) {
  const t = TRUST[accountId];
  TRUST_CHANGES.unshift({
    eventId: nextId('tsc'),
    accountId,
    scoreBefore,
    scoreAfter: t.score,
    tierBefore,
    tierAfter: t.tier,
    reason,
    changedAt,
  });
  if (TRUST_CHANGES.length > 200) TRUST_CHANGES.pop();
}

function notifyIfNew(
  accountId: string,
  subject: string,
  causeEvent: string,
  deliveryStatus: NotificationView['deliveryStatus'] = 'SENT',
  sentAt = now(),
  dedupe = true,
) {
  if (dedupe) {
    const duplicate = NOTIFICATIONS.some(
      (n) => n.accountId === accountId && n.subject === subject && n.causeEvent === causeEvent,
    );
    if (duplicate) return;
  }
  NOTIFICATIONS.unshift({
    notificationId: nextId('ntf'),
    accountId,
    channel: 'EMAIL',
    subject,
    causeEvent,
    deliveryStatus,
    sentAt,
  });
}

// --- accounts / trust history -------------------------------------------------
TRUST_CHANGES.push(
  { eventId: nextId('tsc'), accountId: 'a01', scoreBefore: 90, scoreAfter: 94, tierBefore: 3, tierAfter: 3, reason: 'LOAN_REPAYMENT', changedAt: ago(2600) },
  { eventId: nextId('tsc'), accountId: 'a02', scoreBefore: 71, scoreAfter: 76, tierBefore: 2, tierAfter: 2, reason: 'CLEAN_PERIOD', changedAt: ago(1800) },
  { eventId: nextId('tsc'), accountId: 'a08', scoreBefore: 68, scoreAfter: 62, tierBefore: 1, tierAfter: 1, reason: 'FRAUD_REVIEW', changedAt: ago(900) },
  { eventId: nextId('tsc'), accountId: 'a01', scoreBefore: 94, scoreAfter: 94, tierBefore: 3, tierAfter: 3, reason: 'PERIODIC_REVIEW', changedAt: ago(300) },
);

// --- events ------------------------------------------------------------------
const seedEvents: Array<[string, string, Record<string, unknown>, number]> = [
  ['AccountOpened', 'a01', { holderName: 'Aarav Sharma', initialDeposit: 45000 }, 10080],
  ['MoneyDeposited', 'a01', { amount: 3250.5 }, 8400],
  ['LoanDisbursed', 'a01', { amount: 15000 }, 2880],
  ['TrustScoreChanged', 'a01', { score: 94, tier: 3 }, 1560],
  ['MoneyDeposited', 'a02', { amount: 12980.25 }, 7200],
  ['TransferInitiated', 'a02', { transferId: 'tr-seed-1', amount: 4200 }, 4300],
  ['TransferCompleted', 'a03', { transferId: 'tr-seed-1' }, 4290],
  ['FraudRuleTriggered', 'a08', { rule: 'VELOCITY_3_WITHDRAWALS_60S' }, 2100],
  ['LoanRequested', 'a03', { amount: 10000, tier: 'High' }, 600],
  ['LoanRequested', 'a04', { amount: 30000, tier: 'Medium' }, 480],
  ['LoanRequested', 'a07', { amount: 45000, tier: 'Excellent' }, 360],
  ['FundsReserved', 'a01', { reservationId: 'r-live-1', amount: 2500 }, 120],
  ['AdminPoolReplenished', 'admin-pool', { amount: 10000 }, 55],
  ['TrustScoreChanged', 'a01', { score: 94, tier: 3 }, 25],
  ['Heartbeat', 'a01', { note: 'RFairLock reaper ok' }, 5],
];
for (const [type, aggregateId, payload, minutesAgo] of seedEvents) {
  appendEvent(type, aggregateId, payload, ago(minutesAgo));
}

// --- transactions ------------------------------------------------------------
for (let i = 0; i < ACCOUNTS.length; i++) {
  const acct = ACCOUNTS[i];
  const amount = (n: number) => Math.round((2500 + ((i * 1217 + n * 3571) % 90000)) * 100) / 100;
  if (acct.kycStatus === 'VERIFIED') {
    TRANSACTIONS.push(
      { transactionId: nextId('txn'), accountId: acct.accountId, type: 'DEPOSIT', amount: amount(1), occurredAt: ago(60 * 24 * 6 + i * 30) },
      { transactionId: nextId('txn'), accountId: acct.accountId, type: 'WITHDRAWAL', amount: amount(2), occurredAt: ago(60 * 24 * 3 + i * 20) },
      { transactionId: nextId('txn'), accountId: acct.accountId, type: 'TRANSFER_IN', amount: amount(3), occurredAt: ago(60 * 24 * 2 + i * 12) },
      { transactionId: nextId('txn'), accountId: acct.accountId, type: 'TRANSFER_OUT', amount: amount(4), occurredAt: ago(60 * 6 + i * 7) },
    );
  } else {
    TRANSACTIONS.push({
      transactionId: nextId('txn'),
      accountId: acct.accountId,
      type: 'DEPOSIT',
      amount: amount(5),
      occurredAt: ago(60 * 24 + i * 11),
    });
  }
}

// --- loans -------------------------------------------------------------------
const loanSeed: Array<[string, number, LoanRequestView['status'], number, number]> = [
  // account, amount, status, minutesAgo, purpose index
  ['a01', 20000, 'REPAID', 60 * 24 * 20, 0],
  ['a01', 15000, 'DISBURSED', 60 * 24 * 5, 1],
  ['a02', 50000, 'DISBURSED', 60 * 24 * 2, 2],
  ['a03', 10000, 'QUEUED', 600, 3],
  ['a04', 30000, 'QUEUED', 480, 4],
  ['a07', 45000, 'QUEUED', 360, 5],
  ['a09', 40000, 'WAITING_FOR_FUNDS', 60 * 24, 6],
  ['a13', 15000, 'REJECTED', 60 * 24, 7],
  ['a15', 25000, 'QUEUED', 240, 8],
  ['a17', 12000, 'QUEUED', 180, 9],
];
for (const [accountId, amount, status, minutesAgo, purposeIndex] of loanSeed) {
  LOANS.push({
    loanId: nextId('loan'),
    accountId,
    amount,
    purpose: PURPOSES[purposeIndex],
    trustTier: TRUST[accountId].tier,
    status,
    requestedAt: ago(minutesAgo),
  });
}

// --- fraud cases -------------------------------------------------------------
FRAUD.push(
  { caseId: nextId('fraud'), accountId: 'a08', rule: 'VELOCITY_3_WITHDRAWALS_60S', riskScore: 82, triggeredByEvent: 'MoneyWithdrawn', status: 'OPEN', flaggedAt: ago(2100), reviewedAt: null },
  { caseId: nextId('fraud'), accountId: 'a14', rule: 'GEO_VELOCITY_JUMP', riskScore: 34, triggeredByEvent: 'TransferInitiated', status: 'OPEN', flaggedAt: ago(1440), reviewedAt: null },
  { caseId: nextId('fraud'), accountId: 'a01', rule: 'GEO_VELOCITY_JUMP', riskScore: 34, triggeredByEvent: 'MoneyWithdrawn', status: 'REVIEWED', flaggedAt: ago(1440), reviewedAt: ago(1380) },
);

// --- notifications -----------------------------------------------------------
{
  let n = 0;
  for (const acct of ACCOUNTS) {
    if (acct.kycStatus === 'VERIFIED') {
      n++;
      notifyIfNew(
        acct.accountId,
        'Recent transaction alert',
        'MoneyDeposited',
        n % 17 === 0 ? 'RETRYING' : n % 29 === 0 ? 'DEAD_LETTERED' : 'SENT',
        ago(200 + n * 9),
      );
    }
  }
  notifyIfNew('a01', 'Your loan was disbursed', 'LoanApproved', 'SENT', ago(2880));
  notifyIfNew('a01', 'Trust tier reached Excellent', 'TrustScoreChanged', 'SENT', ago(1560));
  notifyIfNew('a08', 'Unusual activity on your account', 'FraudCaseOpened', 'SENT', ago(2100));
}

// --- reservations ------------------------------------------------------------
const seedReservations: Array<[string, string, number, ReservationView['status'], number]> = [
  ['r-hist-1', 'a03', 1200, 'CAPTURED', 60 * 24 * 3],
  ['r-hist-2', 'a07', 1500, 'EXPIRED', 60 * 24 * 5],
  ['r-hist-3', 'a11', 800, 'CAPTURED', 60 * 24 * 2],
];
for (const [reservationId, accountId, amount, status, minutesAgo] of seedReservations) {
  RESERVATIONS.set(reservationId, {
    reservationId,
    accountId,
    amount,
    status,
    expiresAt: ago(minutesAgo - 30),
    capturedAt: status === 'CAPTURED' ? ago(minutesAgo - 120) : null,
    offlineToken: undefined,
  });
}
function offlineToken(reservationId: string, accountId: string, amount: number, expiresAt: string) {
  const payload = { reservationId, accountId, amount, expiresAt };
  return `eyJhbGciOiJSUzI1NiJ9.${btoa(JSON.stringify(payload))}.DEMO_SIGNATURE`;
}
{
  const liveReservations: Array<[string, string, number]> = [
    ['r-live-1', 'a01', 2500],
    ['r-live-2', 'a02', 1800],
  ];
  for (const [reservationId, accountId, amount] of liveReservations) {
    const expiresAt = new Date(BASE_TIME + 30 * 60_000).toISOString();
    RESERVATIONS.set(reservationId, {
      reservationId,
      accountId,
      amount,
      status: 'RESERVED',
      expiresAt,
      capturedAt: null,
      offlineToken: offlineToken(reservationId, accountId, amount, expiresAt),
    });
    const acct = byId(accountId);
    if (acct) acct.reservedBalance += amount;
  }
}

// --- pool history ------------------------------------------------------------
for (let i = 0; i < 12; i++) {
  const balanceAfter = 180000 + (i + 1) * 10000;
  POOL_HISTORY.push({
    eventId: nextId('pool'),
    amount: 10000,
    balanceAfter,
    at: ago(60 * 24 * (12 - i) + 30),
  });
}

// ------------------------------------------------------------------ helpers
const decidable = (l: LoanRequestView) => l.status === 'QUEUED' || l.status === 'WAITING_FOR_FUNDS';

function loanScore(e: { trustTier?: number; requestedAt?: string }) {
  return (3 - (e.trustTier ?? 0)) * 1e9 + new Date(e.requestedAt ?? 0).getTime();
}

/** Lazily finish an approved loan after 2.5s so the demo shows APPROVED → DISBURSED. */
function advanceLoans() {
  for (const loan of LOANS) {
    if (loan.status !== 'APPROVED') continue;
    const approvedAt = APPROVED_AT.get(loan.loanId) ?? 0;
    if (Date.now() - approvedAt >= 2500) {
      loan.status = 'DISBURSED';
      appendEvent('LoanDisbursed', loan.accountId, { loanId: loan.loanId, amount: loan.amount });
      APPROVED_AT.delete(loan.loanId);
    }
  }
}

/** Lazily drive a transfer saga forward based on elapsed time since initiation. */
function advanceTransfer(t: TransferStatusView) {
  const started = new Date(t.startedAt).getTime();
  const elapsed = Date.now() - started;
  const step = elapsed >= 2100 ? 3 : elapsed >= 1400 ? 2 : elapsed >= 700 ? 1 : 0;
  const order = ['INITIATED', 'SOURCE_DEBITED', 'TARGET_CREDITED', 'COMPLETED'] as const;
  const target = order[step];
  const currentIndex = order.indexOf(t.status as (typeof order)[number]);

  for (let i = Math.max(0, currentIndex) + 1; i <= step; i++) {
    const next = order[i];
    t.status = next;
    t.updatedAt = now();
    if (next === 'SOURCE_DEBITED') {
      appendEvent('TransferSourceDebited', t.fromAccountId, { transferId: t.transferId, amount: t.amount });
    } else if (next === 'TARGET_CREDITED') {
      appendEvent('TransferTargetCredited', t.toAccountId, { transferId: t.transferId, amount: t.amount });
    } else if (next === 'COMPLETED') {
      appendEvent('TransferCompleted', t.fromAccountId, { transferId: t.transferId });
    }
  }

  // If we somehow lag behind (e.g. clock moved), snap to the computed step.
  if (currentIndex < step) {
    t.status = target;
    t.updatedAt = now();
  }
}

/** Reservations expire deterministically from their stored expiry. */
function expireReservations() {
  const nowMs = Date.now();
  for (const r of RESERVATIONS.values()) {
    if (r.status === 'RESERVED' && new Date(r.expiresAt).getTime() <= nowMs) {
      r.status = 'RELEASED';
      const acct = byId(r.accountId);
      if (acct) {
        acct.availableBalance += r.amount;
        acct.reservedBalance = Math.max(0, acct.reservedBalance - r.amount);
      }
      if (!SHORTENED_EVENTS.has(r.reservationId)) {
        SHORTENED_EVENTS.add(r.reservationId);
        appendEvent('FundsReleased', r.accountId, { reservationId: r.reservationId, amount: r.amount, reason: 'TTL_EXPIRED' });
      }
    }
  }
}

function requireAccount(accountId: string) {
  const acct = byId(accountId);
  if (!acct) throw new Error('404 account not found');
  return acct;
}

// ------------------------------------------------------------------ handler
export const mockApi: ApiShape = {
  async listAccounts() {
    return ACCOUNTS.map((a) => ({ ...a }));
  },

  async listRecentEvents(since, limit = 50) {
    const filtered = since ? EVENTS.filter((e) => e.occurredAt > since) : [...EVENTS];
    filtered.sort((a, b) => b.occurredAt.localeCompare(a.occurredAt));
    return filtered.slice(0, limit).map((e) => ({ ...e }));
  },

  async listTransactions(accountId, page = 0, size = 50) {
    const filtered = (accountId ? TRANSACTIONS.filter((t) => t.accountId === accountId) : [...TRANSACTIONS])
      .sort((a, b) => b.occurredAt.localeCompare(a.occurredAt));
    return filtered.slice(page * size, (page + 1) * size).map((t) => ({ ...t }));
  },

  async listAccountEvents(accountId) {
    return EVENTS.filter((e) => e.aggregateId === accountId)
      .sort((a, b) => b.occurredAt.localeCompare(a.occurredAt))
      .map((e) => ({ ...e }));
  },

  async openAccount(body) {
    const region = body.registrationIp.startsWith('49.')
      ? 'APAC'
      : body.registrationIp.startsWith('85.')
        ? 'EU'
        : 'AMER';
    const accountId = `a${String(ACCOUNTS.length + 1).padStart(2, '0')}`;
    const acct: AccountSummary = {
      accountId,
      holderName: body.holderName,
      region,
      availableBalance: body.initialDeposit,
      reservedBalance: 0,
      status: 'ACTIVE',
      kycStatus: 'PENDING',
      updatedAt: now(),
    };
    ACCOUNTS.push(acct);
    TRUST[accountId] = { accountId, score: 50, tier: 1, lastChangedAt: now() };
    appendEvent('AccountOpened', accountId, { holderName: body.holderName, region });
    TRANSACTIONS.unshift({
      transactionId: nextId('txn'),
      accountId,
      type: 'DEPOSIT',
      amount: body.initialDeposit,
      occurredAt: now(),
    });
    return { ...acct };
  },

  async deposit(accountId, amount) {
    const acct = requireAccount(accountId);
    if (acct.status === 'CLOSED') throw new Error('409 account is closed');
    acct.availableBalance += amount;
    acct.updatedAt = now();
    appendEvent('MoneyDeposited', accountId, { amount });
    TRANSACTIONS.unshift({ transactionId: nextId('txn'), accountId, type: 'DEPOSIT', amount, occurredAt: now() });
  },

  async withdraw(accountId, amount) {
    const acct = requireAccount(accountId);
    if (acct.status === 'CLOSED') throw new Error('409 account is closed');
    if (acct.availableBalance < amount) throw new Error('400 insufficient balance');
    acct.availableBalance -= amount;
    acct.updatedAt = now();
    appendEvent('MoneyWithdrawn', accountId, { amount });
    TRANSACTIONS.unshift({ transactionId: nextId('txn'), accountId, type: 'WITHDRAWAL', amount, occurredAt: now() });
  },

  async closeAccount(accountId) {
    const acct = requireAccount(accountId);
    if (acct.status === 'CLOSED') throw new Error('409 account already closed');
    if (acct.reservedBalance > 0) throw new Error('409 capture or release open reservations first');
    acct.status = 'CLOSED';
    acct.updatedAt = now();
    appendEvent('AccountClosed', accountId, { holderName: acct.holderName });
    return { ...acct };
  },

  async setKycStatus(accountId, kycStatus, _note) {
    const acct = requireAccount(accountId);
    acct.kycStatus = kycStatus;
    acct.updatedAt = now();
    appendEvent('AccountKycUpdated', accountId, { kycStatus });
    return { ...acct };
  },

  async transfer(body) {
    const from = requireAccount(body.fromAccountId);
    if (from.status === 'CLOSED') throw new Error('409 source account is closed');
    if (from.availableBalance < body.amount) throw new Error('400 insufficient balance');
    from.availableBalance -= body.amount;
    from.updatedAt = now();
    const to = byId(body.toAccountId);
    if (to) {
      to.availableBalance += body.amount;
      to.updatedAt = now();
    }
    const transferId = nextId('tr');
    TRANSFERS.set(transferId, {
      transferId,
      fromAccountId: body.fromAccountId,
      toAccountId: body.toAccountId,
      amount: body.amount,
      status: 'INITIATED',
      startedAt: now(),
      updatedAt: now(),
      failureReason: null,
    });
    appendEvent('TransferInitiated', body.fromAccountId, { transferId, amount: body.amount });
    TRANSACTIONS.unshift(
      { transactionId: nextId('txn'), accountId: body.fromAccountId, type: 'TRANSFER_OUT', amount: body.amount, occurredAt: now() },
    );
    if (to) {
      TRANSACTIONS.unshift(
        { transactionId: nextId('txn'), accountId: to.accountId, type: 'TRANSFER_IN', amount: body.amount, occurredAt: now() },
      );
    }
    return { transferId };
  },

  async getTransferStatus(transferId) {
    const t = TRANSFERS.get(transferId);
    if (!t) throw new Error('404 transfer not found');
    advanceTransfer(t);
    return { ...t };
  },

  async getLoanQueue() {
    advanceLoans();
    const entries: LoanQueueEntry[] = LOANS.filter(decidable)
      .sort((a, b) => loanScore(a) - loanScore(b))
      .map((l, i) => ({
        ...l,
        status: l.status as LoanQueueEntry['status'],
        position: i + 1,
      }));
    return { entries, generatedAt: now() };
  },

  async getAdminPool() {
    return { ...pool };
  },

  async requestLoan(accountId, amount, purpose) {
    const acct = requireAccount(accountId);
    if (acct.status === 'CLOSED') throw new Error('409 account is closed');
    const tier = TRUST[accountId]?.tier ?? 1;
    const loan: LoanRequestView = {
      loanId: nextId('loan'),
      accountId,
      amount,
      purpose,
      trustTier: tier,
      status: 'QUEUED',
      requestedAt: now(),
    };
    LOANS.push(loan);
    appendEvent('LoanRequested', accountId, { loanId: loan.loanId, amount, tier: TIER_NAMES[tier] });
    return { ...loan };
  },

  async listLoans(accountId, status) {
    advanceLoans();
    return LOANS.filter((l) => (!accountId || l.accountId === accountId) && (!status || l.status === status))
      .map((l) => ({ ...l }))
      .sort((a, b) => b.requestedAt.localeCompare(a.requestedAt));
  },

  async decideLoan(loanId, decision, _note) {
    advanceLoans();
    const loan = LOANS.find((l) => l.loanId === loanId);
    if (!loan) throw new Error('404 loan not found');
    if (!decidable(loan)) throw new Error('409 loan already decided or disbursed');
    if (decision === 'REJECT') {
      loan.status = 'REJECTED';
      appendEvent('LoanRejected', loan.accountId, { loanId, amount: loan.amount });
      notifyIfNew(loan.accountId, 'Your loan request was declined', 'LoanRejected', 'SENT', now(), false);
      return { ...loan };
    }
    if (pool.balance < loan.amount) {
      loan.status = 'WAITING_FOR_FUNDS';
      appendEvent('LoanDeferred', loan.accountId, { loanId, reason: 'INSUFFICIENT_POOL' });
      throw new Error('409 insufficient pool balance — loan moved to WAITING_FOR_FUNDS');
    }
    pool = { ...pool, balance: pool.balance - loan.amount, updatedAt: now() };
    loan.status = 'APPROVED';
    APPROVED_AT.set(loan.loanId, Date.now());
    appendEvent('LoanApproved', loan.accountId, { loanId, amount: loan.amount });
    notifyIfNew(loan.accountId, 'Loan approved', 'LoanApproved', 'SENT', now(), false);
    return { ...loan };
  },

  async repayLoan(loanId, amount) {
    advanceLoans();
    const loan = LOANS.find((l) => l.loanId === loanId);
    if (!loan) throw new Error('404 loan not found');
    if (loan.status !== 'DISBURSED') throw new Error('409 loan is not DISBURSED');
    const acct = requireAccount(loan.accountId);
    if (acct.availableBalance < amount) throw new Error('400 insufficient balance');
    acct.availableBalance -= amount;
    acct.updatedAt = now();
    pool = { ...pool, balance: pool.balance + amount, updatedAt: now() };
    loan.status = 'REPAID';
    appendEvent('LoanRepaid', loan.accountId, { loanId, amount });
    TRANSACTIONS.unshift({ transactionId: nextId('txn'), accountId: loan.accountId, type: 'LOAN_REPAYMENT', amount, occurredAt: now() });
    notifyIfNew(loan.accountId, 'Loan repayment received', 'LoanRepaid', 'SENT', now(), false);
    const t = TRUST[loan.accountId];
    const before = t.score;
    const tierBefore = t.tier;
    t.score = Math.min(100, t.score + 5);
    t.tier = t.score > 90 ? 3 : t.score > 70 ? 2 : t.score > 40 ? 1 : 0;
    t.lastChangedAt = now();
    appendEvent('TrustScoreChanged', loan.accountId, { score: t.score, tier: t.tier });
    recordTrustChange(loan.accountId, before, tierBefore, 'LOAN_REPAYMENT');
    return { ...loan };
  },

  async replenishPool(amount) {
    pool = { ...pool, balance: pool.balance + amount, updatedAt: now() };
    POOL_HISTORY.unshift({ eventId: nextId('pool'), amount, balanceAfter: pool.balance, at: now() });
    appendEvent('AdminPoolReplenished', 'admin-pool', { amount });
    for (const l of LOANS) {
      if (l.status === 'WAITING_FOR_FUNDS') l.status = 'QUEUED';
    }
    return { ...pool };
  },

  async listPoolHistory() {
    return POOL_HISTORY.map((p) => ({ ...p }));
  },

  async listReservations(accountId, status) {
    expireReservations();
    return Array.from(RESERVATIONS.values())
      .filter((r) => (!accountId || r.accountId === accountId) && (!status || r.status === status))
      .map((r) => ({ ...r }))
      .sort((a, b) => b.expiresAt.localeCompare(a.expiresAt));
  },

  async createReservation(accountId, amount, ttlMinutes = 30) {
    const acct = requireAccount(accountId);
    if (acct.status === 'CLOSED') throw new Error('409 account is closed');
    if (acct.availableBalance < amount) throw new Error('400 insufficient funds');
    acct.availableBalance -= amount;
    acct.reservedBalance += amount;
    acct.updatedAt = now();
    const reservationId = nextId('resv');
    const expiresAt = new Date(Date.now() + ttlMinutes * 60_000).toISOString();
    const r: ReservationView = {
      reservationId,
      accountId,
      amount,
      status: 'RESERVED',
      expiresAt,
      capturedAt: null,
      offlineToken: offlineToken(reservationId, accountId, amount, expiresAt),
    };
    RESERVATIONS.set(r.reservationId, r);
    appendEvent('FundsReserved', accountId, { reservationId: r.reservationId, amount });
    TRANSACTIONS.unshift({ transactionId: nextId('txn'), accountId, type: 'RESERVATION', amount, occurredAt: now() });
    return { ...r };
  },

  async getReservation(id) {
    expireReservations();
    const r = RESERVATIONS.get(id);
    if (!r) throw new Error('404 reservation not found');
    return { ...r, offlineToken: r.status === 'RESERVED' ? r.offlineToken : undefined };
  },

  async captureReservation(id, _offlineToken, merchantAccountId) {
    expireReservations();
    const r = RESERVATIONS.get(id);
    if (!r) throw new Error('404 reservation not found');
    if (r.status === 'CAPTURED') throw new Error('409 already captured (replay rejected)');
    if (r.status !== 'RESERVED') throw new Error('410 reservation expired');
    r.status = 'CAPTURED';
    r.capturedAt = now();
    const acct = byId(r.accountId);
    if (acct) acct.reservedBalance = Math.max(0, acct.reservedBalance - r.amount);
    const merchant = byId(merchantAccountId);
    if (merchant) merchant.availableBalance += r.amount;
    appendEvent('FundsCaptured', r.accountId, { reservationId: id, merchant: merchantAccountId });
    TRANSACTIONS.unshift({ transactionId: nextId('txn'), accountId: r.accountId, type: 'CAPTURE', amount: r.amount, occurredAt: now() });
    notifyIfNew(r.accountId, 'Offline payment captured', 'FundsCaptured', 'SENT', now(), false);
    return { capturedAt: r.capturedAt };
  },

  async voidReservation(id) {
    expireReservations();
    const r = RESERVATIONS.get(id);
    if (!r) throw new Error('404 reservation not found');
    if (r.status !== 'RESERVED') throw new Error('409 reservation is not active');
    r.status = 'RELEASED';
    const acct = byId(r.accountId);
    if (acct) {
      acct.availableBalance += r.amount;
      acct.reservedBalance = Math.max(0, acct.reservedBalance - r.amount);
    }
    appendEvent('FundsReleased', r.accountId, { reservationId: id, amount: r.amount, reason: 'MANUAL_VOID' });
    notifyIfNew(r.accountId, 'Offline reservation voided by the bank', 'FundsReleased', 'SENT', now(), false);
    return { ...r };
  },

  async getTrustScore(accountId) {
    const t = TRUST[accountId];
    if (!t) throw new Error('404 no trust score');
    return { ...t };
  },

  async listTrustScoreChanges(accountId, limit = 50) {
    return TRUST_CHANGES.filter((c) => !accountId || c.accountId === accountId)
      .slice(0, limit)
      .map((c) => ({ ...c }));
  },

  async listFraudCases(accountId, status) {
    return FRAUD.filter((f) => (!accountId || f.accountId === accountId) && (!status || f.status === status))
      .map((f) => ({ ...f }));
  },

  async reviewFraudCase(caseId, status, _note) {
    const c = FRAUD.find((f) => f.caseId === caseId);
    if (!c) throw new Error('404 case not found');
    if (c.status !== 'OPEN') throw new Error('409 case is not OPEN');
    c.status = status;
    c.reviewedAt = now();
    appendEvent('FraudCaseReviewed', c.accountId, { caseId, status });
    notifyIfNew(
      c.accountId,
      status === 'DISMISSED' ? 'Fraud flag cleared' : 'Fraud case reviewed by the bank',
      'FraudCaseReviewed',
      'SENT',
      now(),
      false,
    );
    return { ...c };
  },

  async listNotifications(accountId, causeEvent) {
    return NOTIFICATIONS.filter((n) => (!accountId || n.accountId === accountId) && (!causeEvent || n.causeEvent === causeEvent))
      .map((n) => ({ ...n }))
      .sort((a, b) => b.sentAt.localeCompare(a.sentAt));
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
      instanceId: `${serviceId}-0`,
      host: 'localhost',
      port,
      status: i === 5 ? 'STARTING' : 'UP',
      registeredAt: ago(60 + i),
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
    return [
      { topic: 'account.commands', partitions: 16, groups: [{ groupId: 'account-command', lag: 12 }] },
      { topic: 'account.events', partitions: 8, groups: [{ groupId: 'ledger-query', lag: 4 }, { groupId: 'trust-score', lag: 0 }, { groupId: 'fraud-detection', lag: 9 }] },
      { topic: 'loan.events', partitions: 4, groups: [{ groupId: 'trust-score', lag: 2 }] },
      { topic: 'payment.events', partitions: 4, groups: [{ groupId: 'fraud-detection', lag: 1 }] },
    ];
  },
};
