# Meridian Banking Portal

A modern React frontend for the Event-Sourced Banking Ledger (DDBS) project.

## 🚀 Quick Start

```bash
# Install dependencies
npm install

# Start development server (http://localhost:5173)
npm run dev

# Build for production
npm run build
```

## 🔐 Login Credentials

### Customer Portal
- **Username:** `customer`
- **Password:** `customer123`
- **Account:** `a01` (Aarav Sharma)
- **Initial Balance:** ₹48,250.50

### Bank Admin Portal
- **Username:** `bank`
- **Password:** `bank123`
- **Role:** BANK
- **Access:** Full system administration (all accounts, loan queue, fraud review, system ops)

## 🌐 Portal URLs

| Portal | URL | Description |
|--------|-----|-------------|
| Customer Login | http://localhost:5173/login | Customer-facing portal |
| Customer Dashboard | http://localhost:5173/customer/dashboard | Account overview |
| Bank Admin | http://localhost:5173/bank/ops | Full system visibility |

## 🔧 Configuration

### Environment Variables

Set these in your `.env` file or pass to Docker:

```bash
# API Gateway base URL (for real backend)
VITE_API_BASE=http://localhost:8080

# Without VITE_API_BASE, the app runs in mock mode with demo data
```

### Mock Mode (Default)

When `VITE_API_BASE` is not set:
- The app runs fully offline with a deterministic in-memory backend
- 36 fixed accounts across APAC, EU and AMER, with fixed events, loans and reservations
- Clicking actions (transfer, loan, reservation, fraud review) updates the demo data immediately
- No random data and no background simulation — every reload starts from the same seed
- Perfect for demonstrations without running Docker

### Live Mode

When `VITE_API_BASE=http://localhost:8080`:
- Connects to real backend services
- Requires Docker Compose stack to be running
- Real-time data from Kafka and PostgreSQL

## 🏗️ Project Structure

```
src/
├── api/           # API client and type definitions
│   ├── client.ts  # Real API calls
│   ├── base.ts    # API_BASE configuration
│   └── specs/     # Generated OpenAPI types
├── screens/       # Page components
│   ├── customer/  # Customer-facing screens
│   └── bank/      # Bank admin screens
├── components/    # Reusable UI components
├── lib/           # Utility libraries
├── auth.ts        # JWT session management
└── App.tsx        # Main router and layout
```

## 🎮 Navigation (Keyboard Shortcuts)

In the customer portal:
- `1` - Accounts (Dashboard)
- `0` - Open Account
- `2` - Statement
- `3` - Transfer
- `4` - Offline Payment
- `5` - Apply for Loan
- `6` - My Loans
- `7` - Trust Score
- `8` - Fraud Alerts
- `9` - Notifications

In the bank portal:
- `1-0` - Navigate to respective admin screens

## 📱 Features

### Customer Features
- View account balance and transaction history
- Transfer money between accounts
- Apply for and manage loans
- View fraud alerts
- Generate offline payment tokens
- Track trust score and KYC status

### Bank Features
- System operations dashboard
- Loan queue management (trust-tier based priority)
- Admin pool management
- Fraud case review
- Account management
- Reservations overview
- Service health monitoring

## 🧪 Demo Mode

Run without backend:
```bash
npm run dev
# Visit http://localhost:5173
# Click \"Fill customer demo\" or \"Fill bank demo\" on login page
```

Run with backend:
```bash
# Terminal 1: Start backend
docker compose up -d

# Terminal 2: Start frontend  
cd frontend && npm run dev
# Visit http://localhost:5173
# Use demo credentials above
```

## 🎨 Design System

- **Theme:** Light/Dark toggle
- **Typography:** Satoshi (display), IBM Plex Sans (body), IBM Plex Mono (numbers)
- **Colors:** Green/amber status badges, amber warnings, credit/debit color coding
- **Motion:** Smooth transitions with CSS variables
- **Layout:** Fixed sidebar, main content area, 2-column grid on wide screens
