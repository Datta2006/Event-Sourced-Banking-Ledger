# Separate Portal UIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the shared customer/bank toggle from the app shell. Each portal becomes a fully self-contained entry point with its own header, account selector, nav menu, and branding — the two UIs look and behave like two separate products instead of one interface with a switch. Seed data is enriched substantially so every screen has content.

**Architecture:** One Vite build, two route trees (`/customer/*` and `/bank/*`). Each tree renders its own full-screen React root — no shared nav, no shared sidebar, no switch button. Login is split into `LoginPageCustomer` and `LoginPageBank`; each stores a separate role-scoped session in localStorage. The mock backend is expanded with 30+ accounts and a dense event/transaction history. Design tokens are split: customer keeps the current green/accent palette with Satoshi display font, bank gets a navy-purple/amber palette with IBM Plex Mono display. Both share the same structural primitives (panels, register tables, spring counters, status badges) so the component layer stays DRY, but each portal swaps the accent family, underlines, top-bar treatment, and nav glyph set.

**Tech Stack:** React 19, Vite 8, react-router-dom 7, motion (FX springs), openapi-typescript contract. No new dependencies.

**Spec:** HLD_LLD_SystemDesign_v2.md and DESIGN.md at repo root.

## Global Constraints

- TypeScript strict mode; the contract (`api/contract.ts`) is the source of truth for every data shape — no hand-written shapes.
- Every screen must poll its data independently (no shared state between portals except the JWT session).
- Mock transport must stay functional when `VITE_API_BASE` is unset; no production-only code paths in screens.
- No new npm dependencies.
- Reduced motion must collapse animations gracefully on `prefers-reduced-motion: reduce`.
- Review Focus: screen reload loses `accountId` state — every screen must show the dropdown/picker if accountId is stale or missing. (covered by Task 5, step 3)
- Review Focus: mock account IDs are random UUIDs after `openAccount` — a new account doesn't get pre-selected by the shell, so the shell picker must show ALL accounts (not just the acting one). (covered by Task 1, step 4)
- Review Focus: bank `AccountsManagement` table closes accounts on the `close()` action and then hides KYC verification because closed accounts can't be verified — but the UI doesn't surface this block. (covered by Task 6, step 4)
- Review Focus: open-account flow navigates to the new account's dashboard but doesn't refresh the panel — stale `me` data remains. (covered by Task 8, step 3)

## Review Focus

[above]

---

### Task 1: Split the shell — `/customer/*` and `/bank/*` render independent roots

**Files:**
- Modify: `frontend/src/App.tsx` — replace the shared `Portal` component with a router that picks the shell by path prefix.
- Create: `frontend/src/shells/CustomerShell.tsx`
- Create: `frontend/src/shells/BankShell.tsx`
- Remove: `frontend/src/App.tsx`'s `Portal`, `roleSwitch`, `nav`, `main` layout JSX (keep imports of the two shells).

**Interfaces:**
- `CustomerShell({ params, navigate })` — props minimal: reads `accountId` from URL or defaults to the first account, renders only customer screens, exposes a floating "switch to bank" link at bottom-right that clears the customer session and routes to `/bank/ops`.
- `BankShell({ params, navigate })` — similar, exposes a floating "switch to customer" link at bottom-right routing to `/customer/dashboard`.
- Shared: both shells render a top bar (not left sidebar) with brand, portal name/badge, account selector, a few icon buttons (theme toggle, logout), and the screen content area.

- [ ] **Step 1: Write the failing test**
  The test checks that navigating between `/customer/dashboard` and `/bank/ops` renders different shells — i.e., a CustomerShell component does not render a `BankNav` while a BankShell does not render a `CustomerNav`, and neither renders the old `roleSwitch` div. There is no existing test harness for this; write a snapshot test under `frontend/tests/` asserting both `App`'s route definitions exist and `customerRoutePath !== bankRoutePath`.

- [ ] **Step 2: Run test to verify it fails**
  Expected: FAIL because App.tsx still compiles the single Portal component.

- [ ] **Step 3: Implement `App`'s route split**
  Replace the single `<Route path="/:role/:screen">` with two child routes under `<Routes>`: `<Route path="/customer/*" element={<CustomerShell />} />` and `<Route path="/bank/*" element={<BankShell />} />`. Each shell extracts `{ accountId, screen }` from params via `useParams()` directly rather than receiving it as a prop. Default redirect: `/customer/dashboard` when visiting `/` and no session; `/bank/ops` otherwise.

- [ ] **Step 4: Implement `CustomerShell` and `BankShell`**
  Each shell is a stateless component that: reads params, fetches accounts once via `usePoll(() => live().listAccounts(), 3000)` (shared helper `makeAccountLoader`), picks `defaultAccountId` (first active account, or the one from the session if present), renders the top bar (portal brand + portal-name glyph + theme toggle + logout + a translucent "switch portal" chip linking to the other role's home), an account `<select>` bound to `defaultAccountId`, then a `<main>` rendering `{currentScreen}` based on `params.screen` lookup against the role's own nav list. Customer shell also renders the bank-switch chip. Both shells call their respective pages with the picked accountId via `navigate` children routes.

- [ ] **Step 5: Run test to verify it passes**
  Run: `npx tsc -b && vite build --mode development`. Expected: success, zero references to the removed `roleSwitch`/`Portal`/`nav` helpers.

- [ ] **Step 6: Commit**

---

### Task 2: Split auth into two login pages

**Files:**
- Create: `frontend/src/pages/LoginCustomer.tsx`
- Create: `frontend/src/pages/LoginBank.tsx`
- Modify: `frontend/src/LoginPage.tsx` — rename to `LoginCustomer`, remove the "Bank Admin Login" shortcut and the `fill('BANK')` helper; keep the demo-accounts box but split the two credential pairs into two rows.

**Interfaces:**
- `login(username, password)` and `setSession`, `clearSession`, `getSession` remain shared in `auth.ts` — unchanged.
- LoginCustomer fills defaults `customer / customer123` and posts to `${API_BASE}/api/auth/login`; navigates to `/customer/dashboard`.
- LoginBank fills defaults `bank / bank123`; posts to the same endpoint; navigates to `/bank/ops`.

- [ ] **Step 1: Write the failing test**
  Test that `LoginCustomer` and `LoginBank` export distinct component names and that their default fill handlers produce different username values. Write `tests/login-split.test.tsx`.

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement both login pages**
  Keep the same prose copy ("Sign in", "JWT issued by API gateway", "Demo Accounts") but replace the combined `<div>` shortcut row with two rows, one per login type. `LoginCustomer` is the default when landing on `/login?switch=customer`. Navigates to `/customer/dashboard` on success; `LoginBank` routes to `/bank/ops`. Show existing-session button at the bottom regardless of which login spawned it.

- [ ] **Step 4: Wire into App routes**
  Add `<Route path="/login" element={<LoginCustomer />} />` (or keep a single `/login` route but dispatch internally). For cleaner UX, keep one `/login` route and choose inside which login page to render based on whether `switch=customer` or `switch=bank` appears in query params. If no query param, auto-select based on the most recent session role.

- [ ] **Step 5: Run tests**

- [ ] **Step 6: Commit**

---

### Task 3: Splitting the design system — role-tuned tokens

**Files:**
- Modify: `frontend/src/styles.css` — append a `[data-portal="bank"]` override block that swaps `--accent`, `--accent-bg`, `--accent-ink`, `--hairline`, `--primary-underline`, top-bar `--surface` tint, and the `.brand-dot` color to a navy-purple palette; the existing tokens are untouched. Also set `--portal-name-bar` for the top bar height.
- Create: `frontend/src/shells/CustomerTopBar.tsx` — thin component shared, but styled with `data-portal="customer"` attributes inline on the wrapper; no role-specific CSS required since the base tokens already match.
- Create: `frontend/src/shells/BankTopBar.tsx` — inline `data-portal="bank"` styling so tokens swap automatically.

**Interfaces:**
- Existing CSS variables carry through unchanged; the extra `[data-portal]` attribute is the only hook.
- The top bar markup is identical structure across both portals except for the brand wordmark and a single portal-specific icon glyph next to the portal name.

- [ ] **Step 1: Write the failing test**
  Snapshot test asserting both `--accent` values are present and distinct in dark mode: customer's dark accent is green `#3ea574`; bank's dark accent is violet `#9d7ad6`. No visual snapshot, just token assertions.

- [ ] **Step 2: Run and verify fail**

- [ ] **Step 3: Implement the `[data-portal]` overrides in styles.css**
  Add:
  ```css
  [data-portal='bank'] {
    --accent: #7c3aed; /* violet */
    --accent-ink: #ffffff;
    --accent-bg: rgba(124, 58, 237, 0.07);
    --hairline: #b8c0d0;
    --surface: #fff;
    --paper: #fdfcf9;
  }
  [data-portal='bank'][data-theme='dark'] {
    --accent: #9d7ad6;
    --accent-bg: rgba(157, 122, 214, 0.1);
  }
  ```
  Keep all existing semantic tokens (`--credit`, `--debit`, `--warning`, `--info`, `--muted`) mapped to same hex values — only the structural accent family changes, preserving readability.

- [ ] **Step 4: Implement the top bar component pair**
  `CustomerTopBar` renders sat green dot + "Ledger Terminal · Customer Portal". `BankTopBar` renders amber dot + "Ledger Terminal · Bank Console". Both include theme toggle, logout, and the floating cross-portal link. Each sets `data-portal` on its root span so tokens swap.

- [ ] **Step 5: Snapshot + build pass**

- [ ] **Step 6: Commit**

---

### Task 4: Enriching the mock seed data

**Files:**
- Modify: `frontend/src/mock/server.ts`

**Interfaces:**
- Expand `ACCOUNTS` array from 4 to 36 entries: distribution `APAC` ×12, `EU` ×12, `AMER` ×12. Each entry gets a `holderName`, `region`, `availableBalance`, `reservedBalance`, `status: 'ACTIVE'` or `'CLOSED'`, `kycStatus: 'VERIFIED' | 'PENDING' | 'REJECTED'`. Closures: 4 accounts, 2 VERIFIED, 1 PENDING, 1 REJECTED. KYC batch assignment cycles round-robin by region.
- Expand `TRANSACTIONS` from ~5 items to ~120 items: at least one deposit/withdrawal/transfer/loan-repayment per account, spread over last 24 hours with varying amounts. More events (~200) across all aggregates.
- Expand `LOANS`: queue entries from 3 to at least 12 across tiers. Loans in QUEUED, WAITING_FOR_FUNDS, APPROVED, DISBURSED, REPAID, REJECTED states. Loan queue already handles reordering.
- Expand `RESERVATIONS`: historical reservations past expiry in CAPTURED/EXPIRED states plus live RESERVED entries active across several accounts.
- Expand `POOL_HISTORY`: 10+ replenishment events with timestamps and balances after each.
- Expand `FRAUD`: OPEN fraud cases (enough to show both OPEN and REVIEWED statuses, ≥2 per tier).
- Expand `NOTIFICATIONS`: notifications for each account on account-opened, deposits, fraud flags, loan events.
- Ensure each account has at least 4 transactions and ≥1 trusted score change so every trust-score screen has content.

- [ ] **Step 1: Write the failing test**
  Write `frontend/tests/seed-data.test.ts` that asserts: `ACCOUNTS.length === 36`; every `VERIFIED` account has at least 4 `TRANSACTIONS` entries pointing at it; `TRANSACTIONS.some(t => t.type === 'LOAN_REPAYMENT')`; `POOL_HISTORY.length >= 10`; `FRAUD.length >= 2`. The existing test file `frontend/src/__tests__/*.test.ts` doesn't exist yet — confirm and create if missing.

- [ ] **Step 2: Run and verify fail**

- [ ] **Step 3: Rewrite the seed arrays** in `server.ts`
  Use a region × name template generator: build names from first/last patterns with region suffixes. Generate accounts round-robin by region. Build transactions deterministically per account using templates: `[DEPOSIT 8000..50000], [WITHDRAWAL 1000..3000], [TRANSFER_OUT/IN 200..5000], [LOAN_REPAYMENT 1000..4000].` Generate ~200 event entries spanning all aggregates. Fill pool history with escalating replenishments.

- [ ] **Step 4: Verify all screens have non-empty content**
  Check each customer screen's main branch renders rows for the mocked data — Dashboard has fraud alerts, MyLoans has loans, TrustScore has changes. Check each bank screen's main branch has rows — OpsDashboard aggregate stats, AccountManagement full list, ReservationOverview table, PoolManagement waiting loans, LoanQueue entries. Run dev server and visually confirm no empty-state screenshots appear.

- [ ] **Step 5: Commit**

---

### Task 5: Adapt customer screens to independent shell

**Files:**
- Modify: every `screens/customer/*.tsx` (Dashboard, Statement, Transfer, OfflinePayment, ApplyLoan, MyLoans, TrustScore, Notifications, FraudAlerts, OpenAccount)
- Each is now rendered by `CustomerShell` which passes `accountId` as a prop — same contract as today, so this is mostly a rename task, but the `useMe` hook is redefined inside each file (no longer imported from `./shared`).

**Interfaces:**
- Drop the import of `PageHeader, NoAccount, useMe` from `./shared`. Each screen imports `PageHeader` locally from its own header export if desired, or defines its own. Reuse helpers from `components/ui.tsx` and `components/Springs.tsx` as before. Remove the dependency on `window` global (none used).
- Keep the `live()` helper lambda pattern intact — no change to API client usage.

- [ ] **Step 1: Update imports** in all nine customer screens — replace `from './shared'` with nothing or local imports. Update each `export function X({ accountId }: { accountId: string })` signature if needed; already matches.

- [ ] **Step 2: Step through each screen, run dev server, verify no crash** — easiest done as a single per-screen iteration loop. For Dashboard, assert the account dropdown still lists 36 accounts. For OpenAccount, ensure form submission creates a real new account and navigates into it. For MyLoans, assert the repayment prompt renders.

- [ ] **Step 3: Commit in batches** (one commit per screen)

---

### Task 6: Adapt bank screens to independent shell

**Files:**
- Same treatment as Task 5 but applied to `screens/bank/*.tsx`.
- `OpsDashboard`, `LoanQueue`, `PoolManagement`, `ReservationsOverview`, `AccountsManagement`, `TrustAudit`, `Fragmentation`, `NotificationsLog`, `SystemOps`.

**Review focus (already listed above):** the close-account / KYC-verify mutual exclusion needs to surface in the bank AccountsManagement screen.

- [ ] **Step 1: Same import updates per screen.** Drop `./shared` imports.
- [ ] **Step 2: Same iteration + dev-server verification.**
- [ ] **Step 3: Commit per screen**

---

### Task 7: Bank top bar accounts selector — all accounts with actions

**Files:**
- Create: `frontend/src/shells/BankShellAccountSelect.tsx` — extended version of the existing select in `BankShell`, supporting optional per-row action dropdowns (KYC verify, close account, open statement). Reuses `usePoll` for account list. This replaces the inline select in `BankShell`.

**Interfaces:**
- Input: accounts, excludeAccountId (null for bank which shows all), selectedAccountId.
- Output: controlled `<select>` plus a small expansion panel listing actions for the selected account.

- [ ] **Step 1: Write failing test**
  Assert the component renders three options when given three accounts.

- [ ] **Step 2–4: Implement + verify + commit**

---

### Task 8: Open-account flow refresh bug fix

**Files:**
- Modify: `frontend/src/screens/customer/OpenAccount.tsx`

**Interfaces:**
- After successful creation, navigate to the new account's dashboard, but the shell's cached `accountId` state is stale until the next poll cycle. Fix by having the shell pass a fresh `accountId` via URL param (`?new=1`) on navigation, or by reloading the shell state with a key that includes a freshly-generated UUID on open-account completion.

- [ ] **Step 1: Write failing test**
  Type-test the flow end to end through the UI mock: enter "Demo Fresh User" + initial deposit → submit → navigate to `/customer/dashboard?new=<uuid>` → assert shell renders the new account in the select. Test needs to exist before the fix.

- [ ] **Step 2: Verify fail**

- [ ] **Step 3: Implement**
  After `openAccount` resolves, navigate with `?new=<result.accountId>` flag. The shell watches `?new=` param on mount and picks up the new ID immediately without waiting for poll latency.

- [ ] **Step 4: Verify pass + commit**

---

### Task 9: Add remaining bank screens content if gaps exist

**Files:**
- Audit remaining bank screen branches for empty states and either populate or ship sensible defaults.
- `TrustAudit` and `Fragmentation` were recently added — verify they render content given the expanded seed data.

**Interfaces:**
- No new UI needed — just routing and content checks. All screens must be routable from `BANK_NAV` keys (`ops`, `queue`, `pool`, `fraud`, `accounts`, `trust`, `reservations`, `notiflog`, `frag`, `sysops`).

- [ ] **Step 1: Run dev server and open each route** — visually confirm no "No data" empty states on any of the ten bank screens.
- [ ] **Step 2: Commit**
