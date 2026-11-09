import { API_BASE } from './api/base';

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

/** Exchange demo credentials for a gateway-issued JWT. */
export async function login(username: string, password: string): Promise<Session> {
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
