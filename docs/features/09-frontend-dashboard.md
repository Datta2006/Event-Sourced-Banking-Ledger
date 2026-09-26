# Feature 09: Frontend Dashboard

## User Story

As a user, I want a web dashboard to view my accounts, balances, and transactions so that I can manage my money easily.

## Acceptance Criteria

- [ ] React (Vite + TypeScript) SPA at `http://localhost:5173`
- [ ] Account list page shows all accounts with balances
- [ ] Account detail page shows balance + transaction history (paginated)
- [ ] Deposit, withdrawal, transfer forms with validation
- [ ] Transfer status page shows saga status (INITIATED/COMPLETED/FAILED)
- [ ] Admin view shows fraud alerts (for demo)
- [ ] Real-time balance updates via polling (3s interval)
- [ ] Responsive design (mobile-friendly)
- [ ] Calls API Gateway at `http://localhost:8080/api`
- [ ] Loading states, error handling, toast notifications

## Tech Stack

- React 18 + TypeScript
- Vite for fast dev server
- Axios or fetch for API calls
- Tailwind CSS for styling (stretch)
- React Router for navigation

## Pages/Views

1. **Dashboard**: Account list, balances
2. **Account Detail**: Statements, balance history
3. **Transfer**: Source/dest selection, amount, confirmation
4. **Fraud Alerts**: Admin view with severity badges
5. **Notifications**: User notification history

## Edge Cases

- API error → show error banner, retry option
- Empty account list → onboarding prompt
- Long transaction history → pagination
- Concurrent balance update → poll until convergence
- Mobile view → hamburger menu, stacked layout

## Definition of Done

- [ ] Unit tests: component rendering, form validation
- [ ] Integration test: end-to-end flow (open account → deposit → transfer)
- [ ] E2E test with Playwright/Cypress (stretch)
- [ ] Responsive layout verified on mobile
- [ ] API gateway routing verified
- [ ] TypeScript strict mode enabled
- [ ] Manual: navigate all pages, complete full transfer flow