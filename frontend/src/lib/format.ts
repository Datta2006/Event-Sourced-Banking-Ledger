/** Formatting helpers — every rendered number goes through these (DESIGN.md rule 5). */

const fmt2 = new Intl.NumberFormat('en-IN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function money(n: number): string {
  return fmt2.format(n);
}

export function moneyINR(n: number): string {
  return `₹${fmt2.format(n)}`;
}

export function time(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-GB', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

export function ago(iso: string): string {
  const s = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000));
  if (s < 60) return `${s}s ago`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}m ago`;
  return `${Math.floor(m / 60)}h ago`;
}

export function shortId(id: string): string {
  return id.length <= 10 ? id : `${id.slice(0, 8)}…`;
}

/** Human label for a contract event type, e.g. MoneyDeposited → money.deposited */
export function eventLabel(type: string): string {
  const known: Record<string, string> = {
    AccountOpened: 'account.opened',
    MoneyDeposited: 'money.deposited',
    MoneyWithdrawn: 'money.withdrawn',
    TransferInitiated: 'transfer.initiated',
    TransferSourceDebited: 'transfer.source.debited',
    TransferTargetCredited: 'transfer.target.credited',
    TransferCompleted: 'transfer.completed',
    TransferFailed: 'transfer.failed',
    TransferSourceReimbursed: 'transfer.source.reimbursed',
    FundsReserved: 'funds.reserved',
    FundsCaptured: 'funds.captured',
    FundsReleased: 'funds.released',
    LoanRequested: 'loan.requested',
    LoanApproved: 'loan.approved',
    LoanDisbursed: 'loan.disbursed',
    LoanDeferred: 'loan.deferred',
    LoanRepaid: 'loan.repaid',
    LoanDefaulted: 'loan.defaulted',
    AdminPoolReplenished: 'admin_pool.replenished',
    TrustScoreChanged: 'trust.changed',
    FraudRuleTriggered: 'fraud.rule_triggered',
    Heartbeat: 'system.heartbeat',
  };
  return known[type] ?? type.toLowerCase().replace(/([a-z])([A-Z])/g, '$1.$2').toLowerCase();
}

/** Severity tint class for an event type, per DESIGN.md semantic palette. */
export function eventTone(type: string): 'green' | 'red' | 'amber' | 'blue' | 'green-accent' | '' {
  if (/Deposit|Replenish|Credited|Approved|Disbursed|Captured/.test(type)) return 'green';
  if (/Withdraw|Failed|Defaulted|Reimbursed/.test(type)) return 'red';
  if (/Fraud|Deferred|Released/.test(type)) return 'amber';
  if (/Transfer|Reservation|Reserved/.test(type)) return 'blue';
  if (/Trust/.test(type)) return 'green-accent';
  return '';
}
