# Design System — Ledger Terminal (Event-Sourced Banking Ledger UI)

> Approved by user sign-off (D1, this session). Product name: **Ledger Terminal**.

## Product Context
- **What this is:** Web dashboard for an event-sourced banking backend — accounts, ledger events, transfer saga, loan priority queue, offline payments, ops view.
- **Who it's for:** Engineers/auditors demonstrating and verifying distributed-systems behavior (event sourcing, FCFS, saga compensation, sharding).
- **Space/industry:** Fintech infrastructure / event-sourcing demos; deliberately anti-SaaS.
- **Project type:** Dashboard (web app).
- **Memorable thing:** "This screen is evidence" — every pixel argues the system is precise and auditable.

## Aesthetic Direction
- **Direction:** Industrial/Utilitarian — precise, audit-grade banking terminal / ledger book. Not a generic SaaS dashboard.
- **Decoration level:** minimal — typography and hairline rules do the work; color only where it carries state.
- **Mood:** Calm, machined, trustworthy. A real statement/register, set by someone who cares about proof.
- **Reference sites:** none researched (per user brief: concept fixed, work from design knowledge).

## Typography
- **Display/Hero:** Satoshi (via Fontshare CDN) — grotesk with a voice; not Inter/Roboto/system.
- **Body/UI:** IBM Plex Sans (Google Fonts) — engineered heritage, reads as instrumentation.
- **UI/Labels:** IBM Plex Sans, small caps-ish letter-spaced uppercase labels.
- **Data/Tables:** IBM Plex Mono, `font-variant-numeric: tabular-nums` on every amount/ID/timestamp/event-type. **Signature move: mono numerals everywhere** (approved risk).
- **Code:** IBM Plex Mono.
- **Loading:** Fontshare `api.fontshare.com` + Google Fonts `fonts.googleapis.com` preconnect.
- **Scale (modular, 1.2 minor-third up to display):** 11/12/13/14/16/19/23/28/34/41/50/60px. Base body 14px (dense dashboard); data rows 13px mono.

## Color
- **Approach:** restrained — ink on paper, one signal accent, semantic colors only for credit/debit/status.
- **Paper (bg):** `#FAFAF7` · **Surface:** `#FFFFFF` · **Ink (text):** `#191C1F` · **Muted:** `#8A8578` · **Hairline:** `#E4E1D9` · **Hairline-strong:** `#D6D2C6`
- **Accent (signal/live/actions):** banker's green `#17603F`
- **Semantic:** credit `#1E7A46` · debit `#B3402F` · warning `#9A7B2D` · info `#3D5A80`
- **Dark "console" theme (Ops view + dark toggle):** bg `#0E1116`, surface `#161B22`, ink `#E6E1D6`, muted `#8B949E`, hairline `#262C36`, accent lifted to `#3EA574`; semantic hues lifted for contrast.
- **Dark mode strategy:** re-surfaced tokens (not inverted); reduce saturation ~15%.

## Spacing
- **Base unit:** 4px · **Density:** compact (dashboard-grade, calm not cramped).
- **Scale:** 2xs(2) xs(4) sm(8) md(12) lg(16) xl(24) 2xl(32) 3xl(48) 4xl(64)

## Layout
- **Approach:** grid-disciplined, register-first. The ruled table is the primary primitive; cards only for isolated metrics. **Approved risk: ruled register tables, hairline row rules, no drop shadows.**
- **Grid:** fixed 232px sidebar; fluid main; content max-width 1440px.
- **Border radius:** sm 4px, md 6px, lg 8px, pill only for status dots. (User softened the proposed 2px "hard edges" risk.)

## Motion
- **Approach:** intentional — every animation must *explain the system* (event arrival, saga step, queue reorder, balance change). Spring-based, never decorative.
- **Easing:** enter `cubic-bezier(0.22, 1, 0.36, 1)` · exit `cubic-bezier(0.4, 0, 1, 1)` · move `cubic-bezier(0.65, 0, 0.35, 1)` · springs via JS-driven transforms (FLIP).
- **Duration:** micro 80–120ms · short 150–250ms · medium 250–400ms · long 400–700ms (saga beats).

## Anti-Slop Rules (binding)
1. No purple gradients, no gradient CTAs, no 3-column icon-feature grids.
2. No Inter/Roboto/system-ui as primary; no Space Grotesk.
3. No uniform bubbly radii; no centered-everything.
4. Never invent a data shape in a screen — everything traces to the generated contract (`frontend/src/api/contract.ts`).
5. Mono numerals on every number; tabular alignment in every table.

## Decisions Log
| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-09-27 | Initial design system created | design-consultation: industrial/utilitarian ledger-terminal direction; user approved proposal + 2 of 3 risks (mono numerals, ruled registers); softened hard-edge radius risk |
