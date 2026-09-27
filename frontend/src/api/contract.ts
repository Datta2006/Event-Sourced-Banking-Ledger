/**
 * THE CONTRACT — single source of truth for every data shape in the UI.
 *
 * Generated from docs/api_spec/*.yaml (openapi-typescript). Regenerate:
 *   cd frontend && ./scripts/gen-contract.sh
 *
 * No screen may hand-write a response shape from memory. Every request/response
 * type must trace back to one of the generated per-service modules in ./specs/.
 * If an endpoint the UI needs is missing, add it to the OpenAPI spec first and
 * regenerate — never invent an ad-hoc shape here.
 */
import type { components as accountCmd } from './specs/account-command-service';
import type { components as ledgerQuery } from './specs/ledger-query-service';
import type { components as loanSvc } from './specs/loan-service';
import type { components as paymentSvc } from './specs/payment-reservation-service';
import type { components as trustSvc } from './specs/trust-score-service';
import type { components as fraudSvc } from './specs/fraud-detection-service';
import type { components as notificationSvc } from './specs/notification-service';
import type { components as systemOps } from './specs/system-ops';

// ---------------------------------------------------------------------------
// Shared enums (derived from contract enums — not re-declared by hand)
// ---------------------------------------------------------------------------
export type Region = NonNullable<accountCmd['schemas']['AccountSnapshot']['region']>;
export type TransferStatus = NonNullable<
  accountCmd['schemas']['TransferStatusView']['status']
>;
export type LoanStatus = NonNullable<loanSvc['schemas']['LoanRequestView']['status']>;
export type ReservationStatus = NonNullable<
  paymentSvc['schemas']['ReservationView']['status']
>;
export type TransactionType = NonNullable<
  ledgerQuery['schemas']['TransactionView']['type']
>;
export type TrustTier = NonNullable<trustSvc['schemas']['TrustScoreView']['tier']>;

/** Tier names per trust-score-service.yaml: 0=Low, 1=Medium, 2=High, 3=Excellent */
export const TIER_NAMES: Record<TrustTier, string> = {
  0: 'Low',
  1: 'Medium',
  2: 'High',
  3: 'Excellent',
};

// ---------------------------------------------------------------------------
// Re-exported view models (all trace to a spec schema)
// ---------------------------------------------------------------------------
export type AccountSnapshot = accountCmd['schemas']['AccountSnapshot'];
export type AccountSummary = ledgerQuery['schemas']['AccountSummaryView'];
export type TransactionView = ledgerQuery['schemas']['TransactionView'];
export type EventView = ledgerQuery['schemas']['EventView'];
export type LoanRequestView = loanSvc['schemas']['LoanRequestView'];
export type LoanQueueEntry = loanSvc['schemas']['LoanQueueEntry'];
export type LoanQueueView = loanSvc['schemas']['LoanQueueView'];
export type AdminPoolView = loanSvc['schemas']['AdminPoolView'];
export type ReservationView = paymentSvc['schemas']['ReservationView'];
export type TrustScoreView = trustSvc['schemas']['TrustScoreView'];
export type TransferStatusView = accountCmd['schemas']['TransferStatusView'];
export type FraudCase = fraudSvc['schemas']['FraudCase'];
export type NotificationView = notificationSvc['schemas']['NotificationView'];
export type ServiceInstanceView = systemOps['schemas']['ServiceInstanceView'];
export type ShardView = systemOps['schemas']['ShardView'];
export type KafkaTopicView = systemOps['schemas']['KafkaTopicView'];
export type KafkaGroupLag = systemOps['schemas']['KafkaGroupLag'];

// ---------------------------------------------------------------------------
// Aggregate bundle of all generated components (escape hatch for exotic shapes)
// ---------------------------------------------------------------------------
export type AllComponents = accountCmd &
  ledgerQuery &
  loanSvc &
  paymentSvc &
  trustSvc &
  fraudSvc &
  notificationSvc &
  systemOps;
