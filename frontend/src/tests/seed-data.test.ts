/**
 * Seed data test: the mock backend must carry enough accounts and events
 * that every screen renders real content, never an empty state.
 */
import { mockApi } from '../mock/server';

describe('seed data density', () => {
  it('has 36+ accounts across three regions', async () => {
    const accounts = await mockApi.listAccounts();
    expect(accounts.length).toBeGreaterThanOrEqual(36);
    expect(accounts.some((a) => a.region === 'APAC')).toBe(true);
    expect(accounts.some((a) => a.region === 'EU')).toBe(true);
    expect(accounts.some((a) => a.region === 'AMER')).toBe(true);
  });

  it('every VERIFIED account has at least four transactions', async () => {
    const accounts = await mockApi.listAccounts();
    const verified = accounts.filter((a) => a.kycStatus === 'VERIFIED');
    for (const acct of verified) {
      const txns = await mockApi.listTransactions(acct.accountId);
      expect(txns.length).toBeGreaterThanOrEqual(4);
    }
  });

  it('carries at least one loan-repayment and one fraud case', async () => {
    const loans = await mockApi.listLoans();
    expect(loans.some((l) => l.status === 'REPAID')).toBe(true);
    const fraud = await mockApi.listFraudCases();
    expect(fraud.length).toBeGreaterThanOrEqual(2);
    expect(fraud.some((f) => f.status === 'OPEN')).toBe(true);
  });

  it('pool history has ten replenishment events', async () => {
    const history = await mockApi.listPoolHistory();
    expect(history.length).toBeGreaterThanOrEqual(10);
  });

  it('each bank screen sees the expected aggregates', async () => {
    const opsDashboard = await mockApi.getAdminPool();
    expect(opsDashboard.balance > 0).toBe(true);
    const queue = await mockApi.getLoanQueue();
    expect(queue.entries.length).toBeGreaterThanOrEqual(4);
    const reservations = await mockApi.listReservations();
    expect(reservations.some((r) => r.status === 'RESERVED')).toBe(true);
    const accounts = await mockApi.listAccounts();
    expect(accounts.length).toBeGreaterThanOrEqual(36);
  });
});
