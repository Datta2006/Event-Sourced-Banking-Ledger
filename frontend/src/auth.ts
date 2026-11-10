import { API_BASE, isLive } from './api/base';

export type AuthRole = 'CUSTOMER' | 'BANK';

export type Session = {
  token: string;
  tokenType: string;
  role: AuthRole;
  accountId?: string;
  displayName: string;
  expiresAt: string;
};

const KEY = 'ddbs.session';

export function getSession(): Session | null {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return null;
    const session = JSON.parse(raw) as Session;
    if (session.expiresAt && new Date(session.expiresAt).getTime() <= Date.now()) {
      localStorage.removeItem(KEY);
      return null;
    }
    return session;
  } catch {
    return null;
  }
}

export function setSession(session: Session) {
  localStorage.setItem(KEY, JSON.stringify(session));
}

export function clearSession() {
  localStorage.removeItem(KEY);
}

/**
 * Demo credentials used by the offline mock transport. The real gateway is not
 * required for a course demo: when VITE_API_BASE is unset we authenticate
 * locally and sign a fixed, deterministic session.
 */
export const DEMO_LOGIN = {
  CUSTOMER: { username: 'customer', password: 'customer123' },
  BANK: { username: 'bank', password: 'bank123' },
} as const;

/** The seeded customer account that `customer` signs in as. */
export const DEMO_CUSTOMER_ACCOUNT = 'a01';

function mockLogin(username: string, password: string): Session {
  const role: AuthRole | undefined =
    username === DEMO_LOGIN.CUSTOMER.username && password === DEMO_LOGIN.CUSTOMER.password
      ? 'CUSTOMER'
      : username === DEMO_LOGIN.BANK.username && password === DEMO_LOGIN.BANK.password
        ? 'BANK'
        : undefined;

  if (!role) {
    throw new Error('Invalid demo credentials. Use customer / customer123 or bank / bank123.');
  }

  return {
    token: `demo.${role.toLowerCase()}.token`,
    tokenType: 'Bearer',
    role,
    accountId: role === 'CUSTOMER' ? DEMO_CUSTOMER_ACCOUNT : undefined,
    displayName: role === 'CUSTOMER' ? 'Aarav Sharma' : 'Bank Operations',
    expiresAt: new Date(Date.now() + 8 * 60 * 60 * 1000).toISOString(),
  };
}

/** Exchange demo credentials for a session (mock) or a gateway-issued JWT (live). */
export async function login(username: string, password: string): Promise<Session> {
  if (!isLive) return mockLogin(username, password);

  const res = await fetch(`${API_BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    let message = text;
    try {
      message = (JSON.parse(text) as { message?: string }).message ?? text;
    } catch {
      /* plain text */
    }
    throw new Error(message || `Login failed (${res.status})`);
  }
  const body = (await res.json()) as {
    token: string;
    tokenType?: string;
    role: AuthRole;
    accountId?: string;
    displayName?: string;
    expiresAt: string;
  };
  return {
    token: body.token,
    tokenType: body.tokenType ?? 'Bearer',
    role: body.role,
    accountId: body.accountId,
    displayName: body.displayName ?? username,
    expiresAt: body.expiresAt,
  };
}
