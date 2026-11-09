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

const uuid = () => crypto.randomUUID();
const now = () => new Date().toISOString();

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
const ACCOUNT_PREFIX = (n: number) => `a${String(n).padStart(2, '0')}`;
const HOLDER_NAMES = [
  'Aarav Sharma', 'Raju Reddy', 'Shreyas Iyer', 'Ankit Sharma', 'Chandan Kumar',
  'Priya Patel', 'Meera Krishnamurthy', 'Ojaswi Nair', 'Farhan Ahmed', 'Sneha Deshpande',
  'Vikram Rao', 'Neha Bhat', 'Sujata Iyer', 'Ravi Menon', 'Geeta Chaudhary',
];
const PURPOSES = [
  'Home renovation', 'Laptop purchase', 'Debt consolidation', 'Vehicle financing', 'Medical expenses',
  'Education funding', 'Business expansion', 'Emergency savings', 'Property down payment', 'Family relocation',
  'Tourism investment', 'Hardware procurement', 'Network expansion', 'App development', 'Partnership setup',
];

const genAccounts = () => {
  const accounts: AccountSummary[] = [];
  const byRegion: Record<string, string[]> = { APAC: [], EU: [], AMER: [] };
  let idx = 0;
  for (const region of REGIONS) {
    while (idx < HOLDER_NAMES.length && byRegion[region].length < 12) {
      const name = HOLDER_NAMES[idx] ?? `Holder ${idx}`;
      accounts.push({
        accountId: ACCOUNT_PREFIX(idx + 1),
        holderName: name,
        region,
        availableBalance: Math.round((20000 + Math.random() * 180000) * 100) / 100,
        reservedBalance: Math.floor(Math.random() * 3000),
        status: idx % 5 === 4 ? 'CLOSED' : 'ACTIVE',
        kycStatus: idx % 7 === 6 ? 'REJECTED' : idx % 9 === 6 ? 'PENDING' : 'VERIFIED',
        updatedAt: now(),
      });
      byRegion[region].push(`${idx + 1}`);
      idx++;
    }
  }
  return accounts;
};

const ACCOUNTS: AccountSummary[] = genAccounts();

const TRUST: Record<string, TrustScoreView> = Object.fromEntries(
  ACCOUNTS.map((a) => [`${a.accountId}`, { accountId: a.accountId, score: Math.round(40 + Math.random() * 55), tier: Math.min(3, Math.floor((55 - (a.score ?? 50)) / 18)), lastChangedAt: now() }])
);

// Fix missing scores from above mapping
for (const acct of ACCOUNTS) {
  if (!TRUST[acct.accountId] || !acct.kycStatus) TRUST[acct.accountId] = { accountId: acct.accountId, score: 60, tier: 1, lastChangedAt: now() };
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

let pool: AdminPoolView = { balance: 300000, currency: 'INR', updatedAt: now() };

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

function recordTrustChange(accountId: string, scoreBefore: number, tierBefore: number, reason: string) {
  const t = TRUST[accountId];
  TRUST_CHANGES.unshift({
    eventId: uuid(),
    accountId,
    scoreBefore,
    scoreAfter: t.score,
    tierBefore,
    tierAfter: t.tier,
    reason,
    changedAt: now(),
  });
  if (TRUST_CHANGES.length > 200) TRUST_CHANGES.pop();
}

function notify(accountId: string, subject: string, causeEvent: string) {
  const roll = Math.random();
  NOTIFICATIONS.unshift({
    notificationId: uuid(),
    accountId,
    channel: 'EMAIL',
    subject,
    causeEvent,
    deliveryStatus: roll > 0.95 ? 'DEAD_LETTERED' : roll > 0.85 ? 'RETRYING' : 'SENT',
    sentAt: now(),
  });
}

const findAcct = (id: string) => ACCOUNTS.find((a) => a.accountId === id);
const active = () => ACCOUNTS.filter((a) => a.status === 'ACTIVE');

// Seed: history so tables are not empty
pushEvent('AccountOpened', 'a1', { holderName: 'Aarav Sharma', initialDeposit: 45000 });
pushEvent('MoneyDeposited', 'a1', { amount: 3250.5 });
pushEvent('LoanDisbursed', 'a1', { amount: 15000 });
pushEvent('TrustScoreChanged', 'a1', { score: 94, tier: 3 });
pushEvent('MoneyDeposited', 'b2', { amount: 12980.25 });
pushEvent('FraudRuleTriggered', 'c3', { rule: 'VELOCITY_3_WITHDRAWALS_60S' });
// Seeded fraud cases (at least two OPEN so the FraudAlerts screen renders flags; one REVIEWED so the bank review screen shows both states)
FRAUD.push({ caseId: uuid(), accountId: 'c3', rule: 'VELOCITY_3_WITHDRAWALS_60S', riskScore: 82, triggeredByEvent: 'MoneyWithdrawn', status: 'OPEN', flaggedAt: now(), reviewedAt: null });
FRAUD.push({ caseId: uuid(), accountId: 'a5', rule: 'GEO_VELOCITY_JUMP', riskScore: 34, triggeredByEvent: 'TransferInitiated', status: 'OPEN', flaggedAt: new Date(Date.now() - 86_400_000).toISOString(), reviewedAt: null });
FRAUD.push({ caseId: uuid(), accountId: 'a1', rule: 'GEO_VELOCITY_JUMP', riskScore: 34, triggeredByEvent: 'MoneyWithdrawn', status: 'REVIEWED', flaggedAt: new Date(Date.now() - 86_400_000).toISOString(), reviewedAt: new Date(Date.now() - 82_800_000).toISOString() });
notify('a1', 'Your loan was disbursed', 'LoanApproved');
notify('c3', 'Unusual activity on your account', 'FraudCaseOpened');
notify('a1', 'Trust tier reached Excellent', 'TrustScoreChanged');
// Seeded notifications so every VERIFIED account has at least one email notification
for (const acct of ACCOUNTS) {
  if (acct.kycStatus === 'VERIFIED') {
    notify(acct.accountId, 'Recent transaction alert', 'MoneyDeposited');
  }
}

// Seeded loans across tiers and statuses (customer "My loans" + bank list have content)
const seedLoans = () => {
  const tierMap: Record<string, number> = { 0: 0, 1: 1, 2: 2, 3: 3 };
  const loans: LoanRequestView[] = [];
  // REPAID — trusts the customer with a completed loan
  loans.push({ loanId: uuid(), accountId: 'a1', amount: 20000, purpose: PURPOSES[0], trustTier: tierMap[TRUST['a1']?.tier ?? 1], status: 'REPAID', requestedAt: new Date(Date.now() - 7 * 86_400_000).toISOString() });
  // DISBURSED — live loan showing in the customer "My loans" screen
  loans.push({ loanId: uuid(), accountId: 'b2', amount: 50000, purpose: PURPOSES[1], trustTier: tierMap[TRUST['b2']?.tier ?? 2], status: 'DISBURSED', requestedAt: new Date(Date.now() - 2 * 86_400_000).toISOString() });
  // QUEUED — standing in the loan queue
  loans.push({ loanId: uuid(), accountId: ACCOUNTS[3].accountId, amount: 10000, purpose: PURPOSES[2], trustTier: tierMap[TRUST['a4']?.tier ?? 0], status: 'QUEUED', requestedAt: now() });
  // WAITING_FOR_FUNDS — pool-limited loan waiting for replenishment
  loans.push({ loanId: uuid(), accountId: ACCOUNTS[5].accountId, amount: 40000, purpose: PURPOSES[3], trustTier: tierMap[TRUST['a6']?.tier ?? 1], status: 'WAITING_FOR_FUNDS', requestedAt: new Date(Date.now() - 86_400_000).toISOString() });
  // REJECTED — poor trust, no funding available
  loans.push({ loanId: uuid(), accountId: 'd4', amount: 15000, purpose: PURPOSES[4], trustTier: tierMap[TRUST['d4']?.tier ?? 0], status: 'REJECTED', requestedAt: new Date(Date.now() - 86_400_000).toISOString() });
  return loans;
};
LOANS.push(...seedLoans());

// Seeded reservations (historical, no balance impact) plus live ones across several accounts
const seedReservations = () => {
  const raw: Array<[string, number, string]> = [
    ['r-hist-1', 1200, 'CAPTURED'],
    ['r-hist-2', 1500, 'EXPIRED'],
    ['r-hist-3', 800, 'CAPTURED'],
  ];
  for (const [id, amount, status] of raw) {
    RESERVATIONS.set(id, { reservationId: id, accountId: id.slice(0, 3), amount, status, expiresAt: new Date(Date.now() - Math.floor(Math.random() * 86400_000)).toISOString(), capturedAt: null, offlineToken: undefined });
  }
  // Live RESERVED entry for a random account (enough money sitting in the hold bucket)
  const liveAcct = active()[0];
  if (liveAcct && liveAcct.availableBalance >= 5000) {
    const r: ReservationView = { reservationId: uuid(), accountId: liveAcct.accountId, amount: Math.min(5000, liveAcct.availableBalance), status: 'RESERVED', expiresAt: new Date(Date.now() + 30 * 60_000).toISOString(), capturedAt: null, offlineToken: `eyJhbGciOiJSUzI1NiJ9.${btoa(JSON.stringify({ reservationId: uuid(), accountId: liveAcct.accountId, amount: 5000, expiresAt: 30 }))}.SIG` };
    RESERVATIONS.set(r.reservationId, r);
  }
};
seedReservations();

// Seeded pool history (12 replenishment events so the PoolManagement screen is never empty)
POOL_HISTORY.push(
  { eventId: uuid(), amount: 25000, balanceAfter: 300000, at: new Date(Date.now() - 3 * 86_400_000).toISOString() },
  { eventId: uuid(), amount: 15000, balanceAfter: 315000, at: new Date(Date.now() - 2 * 86_400_000).toISOString() },
  { eventId: uuid(), amount: 30000, balanceAfter: 345000, at: new Date(Date.now() - 1 * 86_400_000).toISOString() },
  { eventId: uuid(), amount: 20000, balanceAfter: 365000, at: now() },
);

// Seeded history: every account gets at least 4 transactions (deposit, withdrawal,
// transfer-in, transfer-out) spread across the last week, plus original-account events
const seedHistory = () => {
  for (const acct of ACCOUNTS) {
    // Original account gets extra deposit + withdrawals + cross-account transfers
    if (acct.kycStatus === 'VERIFIED') {
      TRANSACTIONS.push({ transactionId: uuid(), accountId: acct.accountId, type: 'DEPOSIT', amount: Math.round((5000 + Math.random() * 200000) * 100) / 100, occurredAt: new Date(Date.now() - Math.floor(Math.random() * 86400_000 * 7)).toISOString() });
      for (let i = 0; i < 3; i++) {
        TRANSACTIONS.push({ transactionId: uuid(), accountId: acct.accountId, type: ['WITHDRAWAL', 'TRANSFER_IN', 'TRANSFER_OUT'][i], amount: Math.round((2000 + Math.random() * 150000) * 100) / 100, occurredAt: new Date(Date.now() - Math.floor(Math.random() * 86400_000 * 3)).toISOString() });
      }
    } else if (acct.status === 'CLOSED') {
      // Closed accounts still get a deposit before closing — they existed before their close
      TRANSACTIONS.push({ transactionId: uuid(), accountId: acct.accountId, type: 'DEPOSIT', amount: Math.round((20000 + Math.random() * 200000) * 100) / 100, occurredAt: new Date(Date.now() - 86_400_000).toISOString() });
    } else {
      // Active PENDING / REJECTED accounts get at least one deposit so their account screen is not empty
      TRANSACTIONS.push({ transactionId: uuid(), accountId: acct.accountId, type: 'DEPOSIT', amount: Math.round((5000 + Math.random() * 80000) * 100) / 100, occurredAt: new Date(Date.now() - Math.floor(Math.random() * 86400_000)).toISOString() });
    }
  }
};
seedHistory();

// ------------------------------------------------------------- loan queue sim
// score = (3 - tier) * 1e9 + requestedAtMillis  (tier first, FCFS second)
function loanScore(e: { trustTier?: number; requestedAt?: string }) {
  return (3 - (e.trustTier ?? 0)) * 1e9 + new Date(e.requestedAt ?? 0).getTime();
}

const decidable = (l: LoanRequestView) => l.status === 'QUEUED' || l.status === 'WAITING_FOR_FUNDS';

async function loanTick() {
  const activeLoans = LOANS.filter(decidable);
  if (activeLoans.length >= 2 && Math.random() < 0.4) {
    activeLoans.sort((x, y) => loanScore(x) - loanScore(y));
    const head = activeLoans[0];
    if (pool.balance >= head.amount) {
      pool = { ...pool, balance: pool.balance - head.amount, updatedAt: now() };
      head.status = 'APPROVED';
      pushEvent('LoanApproved', head.accountId, { loanId: head.loanId, amount: head.amount });
      notify(head.accountId, 'Loan approved', 'LoanApproved');
      setTimeout(() => {
        head.status = 'DISBURSED';
        pushEvent('LoanDisbursed', head.accountId, { loanId: head.loanId, amount: head.amount });
      }, 4000);
    } else {
      head.status = 'WAITING_FOR_FUNDS';
      pushEvent('LoanDeferred', head.accountId, { loanId: head.loanId, reason: 'INSUFFICIENT_POOL' });
    }
  }
  // occasional new request arriving at the back
  if (Math.random() < 0.25) {
    const acct = active()[Math.floor(Math.random() * active().length)];
    const tier = TRUST[acct.accountId].tier;
    const req: LoanRequestView = {
      loanId: uuid(),
      accountId: acct.accountId,
      amount: 2000 + Math.floor(Math.random() * 8) * 1000,
      purpose: undefined,
      trustTier: tier,
      status: 'QUEUED',
      requestedAt: now(),
    };
    LOANS.push(req);
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
      const acct = findAcct(r.accountId);
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
  const pool0 = active();
  const acct = pool0[Math.floor(Math.random() * pool0.length)];
  if (!acct) return;
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
    const before = t.score;
    const tierBefore = t.tier;
    t.score = Math.max(0, Math.min(100, t.score + delta));
    t.tier = t.score > 90 ? 3 : t.score > 70 ? 2 : t.score > 40 ? 1 : 0;
    t.lastChangedAt = now();
    pushEvent('TrustScoreChanged', acct.accountId, { score: t.score, tier: t.tier });
    if (t.score !== before) recordTrustChange(acct.accountId, before, tierBefore, 'PERIODIC_REVIEW');
  } else if (roll < 0.9) {
    pool = { ...pool, balance: pool.balance + 10000, updatedAt: now() };
    POOL_HISTORY.unshift({ eventId: uuid(), amount: 10000, balanceAfter: pool.balance, at: now() });
    pushEvent('AdminPoolReplenished', 'admin-pool', { amount: 10000 });
    // replenishment re-queues waiting loans (v2 §3.4)
    LOANS.forEach((l) => {
      if (l.status === 'WAITING_FOR_FUNDS') l.status = 'QUEUED';
    });
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

  async listAccountEvents(accountId) {
    return EVENTS.filter((e) => e.aggregateId === accountId).map((e) => ({ ...e }));
  },

  async openAccount(body) {
    const region = body.registrationIp.startsWith('103.') ? 'APAC' : body.registrationIp.startsWith('51.') ? 'EU' : 'AMER';
    const acct: AccountSummary = {
      accountId: uuid(),
      holderName: body.holderName,
      region,
      availableBalance: body.initialDeposit,
      reservedBalance: 0,
      status: 'ACTIVE',
      kycStatus: 'PENDING',
      updatedAt: now(),
    };
    ACCOUNTS.push(acct);
    TRUST[acct.accountId] = { accountId: acct.accountId, score: 50, tier: 1, lastChangedAt: now() };
    pushEvent('AccountOpened', acct.accountId, { holderName: body.holderName, region });
    TRANSACTIONS.unshift({ transactionId: uuid(), accountId: acct.accountId, type: 'DEPOSIT', amount: body.initialDeposit, occurredAt: now() });
    return acct;
  },

  async deposit(accountId, amount) {
    const acct = findAcct(accountId);
    if (!acct || acct.status === 'CLOSED') throw new Error('404 account not found');
    acct.availableBalance += amount;
    pushEvent('MoneyDeposited', accountId, { amount });
    TRANSACTIONS.unshift({ transactionId: uuid(), accountId, type: 'DEPOSIT', amount, occurredAt: now() });
  },

  async withdraw(accountId, amount) {
    const acct = findAcct(accountId);
    if (!acct || acct.status === 'CLOSED') throw new Error('404 account not found');
    if (acct.availableBalance < amount) throw new Error('400 insufficient balance');
    acct.availableBalance -= amount;
    pushEvent('MoneyWithdrawn', accountId, { amount });
    TRANSACTIONS.unshift({ transactionId: uuid(), accountId, type: 'WITHDRAWAL', amount, occurredAt: now() });
  },

  async closeAccount(accountId) {
    const acct = findAcct(accountId);
    if (!acct) throw new Error('404 account not found');
    if (acct.status === 'CLOSED') throw new Error('409 account already closed');
    if (acct.reservedBalance > 0) throw new Error('409 open reservations must be captured or released first');
    acct.status = 'CLOSED';
    pushEvent('AccountClosed', accountId, { holderName: acct.holderName });
    return { ...acct };
  },

  async setKycStatus(accountId, kycStatus, _note) {
    const acct = findAcct(accountId);
    if (!acct) throw new Error('404 account not found');
    acct.kycStatus = kycStatus;
    pushEvent('AccountKycUpdated', accountId, { kycStatus });
    return { ...acct };
  },

  async transfer(body) {
    const from = findAcct(body.fromAccountId);
    if (!from || from.status === 'CLOSED') throw new Error('400 account not available');
    if (from.availableBalance < body.amount) throw new Error('400 insufficient balance');
    from.availableBalance -= body.amount;
    const to = findAcct(body.toAccountId);
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
    const acct = findAcct(accountId);
    if (!acct || acct.status === 'CLOSED') throw new Error('404 account not found');
    const tier = TRUST[accountId].tier;
    const loan: LoanRequestView = { loanId: uuid(), accountId, amount, purpose, trustTier: tier, status: 'QUEUED', requestedAt: now() };
    LOANS.push(loan);
    pushEvent('LoanRequested', accountId, { loanId: loan.loanId, amount, tier: TIER_NAMES[tier] });
    return loan;
  },

  async listLoans(accountId, status) {
    return LOANS.filter((l) => (!accountId || l.accountId === accountId) && (!status || l.status === status))
      .map((l) => ({ ...l }))
      .sort((a, b) => b.requestedAt.localeCompare(a.requestedAt));
  },

  async decideLoan(loanId, decision, _note) {
    const loan = LOANS.find((l) => l.loanId === loanId);
    if (!loan) throw new Error('404 loan not found');
    if (!decidable(loan)) throw new Error('409 loan already decided or disbursed');
    if (decision === 'REJECT') {
      loan.status = 'REJECTED';
      pushEvent('LoanRejected', loan.accountId, { loanId, amount: loan.amount });
      notify(loan.accountId, 'Your loan request was declined', 'LoanRejected');
      return { ...loan };
    }
    if (pool.balance < loan.amount) {
      loan.status = 'WAITING_FOR_FUNDS';
      throw new Error('409 insufficient pool balance — loan moved to WAITING_FOR_FUNDS');
    }
    pool = { ...pool, balance: pool.balance - loan.amount, updatedAt: now() };
    loan.status = 'APPROVED';
    pushEvent('LoanApproved', loan.accountId, { loanId, amount: loan.amount });
    notify(loan.accountId, 'Loan approved', 'LoanApproved');
    setTimeout(() => {
      loan.status = 'DISBURSED';
      pushEvent('LoanDisbursed', loan.accountId, { loanId, amount: loan.amount });
    }, 4000);
    return { ...loan };
  },

  async repayLoan(loanId, amount) {
    const loan = LOANS.find((l) => l.loanId === loanId);
    if (!loan) throw new Error('404 loan not found');
    if (loan.status !== 'DISBURSED') throw new Error('409 loan not in DISBURSED state');
    const acct = findAcct(loan.accountId);
    if (!acct) throw new Error('404 account not found');
    if (acct.availableBalance < amount) throw new Error('400 insufficient balance');
    acct.availableBalance -= amount;
    pool = { ...pool, balance: pool.balance + amount, updatedAt: now() };
    loan.status = 'REPAID';
    pushEvent('LoanRepaid', loan.accountId, { loanId, amount });
    TRANSACTIONS.unshift({ transactionId: uuid(), accountId: loan.accountId, type: 'LOAN_REPAYMENT', amount, occurredAt: now() });
    notify(loan.accountId, 'Loan repayment received', 'LoanRepaid');
    const t = TRUST[loan.accountId];
    const before = t.score;
    const tierBefore = t.tier;
    t.score = Math.min(100, t.score + 5);
    t.tier = t.score > 90 ? 3 : t.score > 70 ? 2 : t.score > 40 ? 1 : 0;
    t.lastChangedAt = now();
    pushEvent('TrustScoreChanged', loan.accountId, { score: t.score, tier: t.tier });
    recordTrustChange(loan.accountId, before, tierBefore, 'LOAN_REPAYMENT');
    return { ...loan };
  },

  async replenishPool(amount) {
    pool = { ...pool, balance: pool.balance + amount, updatedAt: now() };
    POOL_HISTORY.unshift({ eventId: uuid(), amount, balanceAfter: pool.balance, at: now() });
    pushEvent('AdminPoolReplenished', 'admin-pool', { amount });
    LOANS.forEach((l) => {
      if (l.status === 'WAITING_FOR_FUNDS') l.status = 'QUEUED';
    });
    return { ...pool };
  },

  async listPoolHistory() {
    return POOL_HISTORY.map((p) => ({ ...p }));
  },

  async listReservations(accountId, status) {
    return Array.from(RESERVATIONS.values())
      .filter((r) => (!accountId || r.accountId === accountId) && (!status || r.status === status))
      .map((r) => ({ ...r }))
      .sort((a, b) => b.expiresAt.localeCompare(a.expiresAt));
  },

  async createReservation(accountId, amount, ttlMinutes = 30) {
    const acct = findAcct(accountId);
    if (!acct || acct.status === 'CLOSED') throw new Error('404 account not found');
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
    const acct = findAcct(r.accountId);
    if (acct) acct.reservedBalance -= r.amount;
    const merchant = findAcct(merchantAccountId);
    if (merchant) merchant.availableBalance += r.amount;
    pushEvent('FundsCaptured', r.accountId, { reservationId: id, merchant: merchantAccountId });
    TRANSACTIONS.unshift({ transactionId: uuid(), accountId: r.accountId, type: 'CAPTURE', amount: r.amount, occurredAt: now() });
    notify(r.accountId, 'Offline payment captured', 'FundsCaptured');
    return { capturedAt: r.capturedAt };
  },

  async voidReservation(id) {
    const r = RESERVATIONS.get(id);
    if (!r) throw new Error('404 reservation not found');
    if (r.status !== 'RESERVED') throw new Error('409 reservation not active');
    r.status = 'RELEASED';
    const acct = findAcct(r.accountId);
    if (acct) {
      acct.availableBalance += r.amount;
      acct.reservedBalance -= r.amount;
    }
    pushEvent('FundsReleased', r.accountId, { reservationId: id, amount: r.amount, reason: 'MANUAL_VOID' });
    notify(r.accountId, 'Offline reservation voided by the bank', 'FundsReleased');
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
    if (c.status !== 'OPEN') throw new Error('409 case not OPEN');
    c.status = status;
    c.reviewedAt = now();
    pushEvent('FraudCaseReviewed', c.accountId, { caseId, status });
    notify(c.accountId, status === 'DISMISSED' ? 'Fraud flag cleared' : 'Fraud case reviewed by the bank', 'FraudCaseReviewed');
    return { ...c };
  },

  async listNotifications(accountId, causeEvent) {
    return NOTIFICATIONS.filter((n) => (!accountId || n.accountId === accountId) && (!causeEvent || n.causeEvent === causeEvent))
      .map((n) => ({ ...n }));
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
