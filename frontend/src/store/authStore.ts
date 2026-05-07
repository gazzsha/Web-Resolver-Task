import { create } from 'zustand';
import type { AuthUser, JwtResponse } from '@/types/auth';

interface AuthState {
  user: AuthUser | null;
  accessToken: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  /** Display label read from JwtResponse.username. Not used for login. */
  username: string | null;

  setSession: (resp: JwtResponse) => void;
  logout: () => void;
}

const LS_ACCESS_TOKEN = 'authToken';
const LS_REFRESH_TOKEN = 'refreshToken';
const LS_AUTH_USER = 'authUser';
const LS_USERNAME = 'authUsername';

/**
 * Fallback: base64-decode the JWT payload segment and extract the `username` claim.
 * Used only when the response object does not carry the field (e.g. old token in localStorage).
 */
function decodeUsernameFromJwt(token: string): string | null {
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    const claims = JSON.parse(json) as Record<string, unknown>;
    return typeof claims['username'] === 'string' ? claims['username'] : null;
  } catch {
    return null;
  }
}

function loadFromStorage(): Pick<AuthState, 'user' | 'accessToken' | 'refreshToken' | 'isAuthenticated' | 'username'> {
  const accessToken = localStorage.getItem(LS_ACCESS_TOKEN);
  const refreshToken = localStorage.getItem(LS_REFRESH_TOKEN);
  const rawUser = localStorage.getItem(LS_AUTH_USER);
  const storedUsername = localStorage.getItem(LS_USERNAME);

  let user: AuthUser | null = null;
  if (rawUser) {
    try {
      user = JSON.parse(rawUser) as AuthUser;
    } catch {
      // Corrupted JSON — treat as logged out
    }
  }

  // Resolve username: explicit storage → JWT claim fallback
  let username: string | null = storedUsername;
  if (!username && accessToken) {
    username = decodeUsernameFromJwt(accessToken);
  }

  return {
    accessToken,
    refreshToken,
    user,
    isAuthenticated: accessToken !== null,
    username,
  };
}

export const useAuthStore = create<AuthState>((set) => ({
  ...loadFromStorage(),

  setSession(resp: JwtResponse) {
    const username = resp.username ?? null;

    localStorage.setItem(LS_ACCESS_TOKEN, resp.accessToken);
    localStorage.setItem(LS_REFRESH_TOKEN, resp.refreshToken);
    localStorage.setItem(LS_AUTH_USER, JSON.stringify({ email: resp.email, role: resp.role, username }));
    if (username !== null) {
      localStorage.setItem(LS_USERNAME, username);
    } else {
      localStorage.removeItem(LS_USERNAME);
    }

    set({
      accessToken: resp.accessToken,
      refreshToken: resp.refreshToken,
      user: { email: resp.email, role: resp.role, username: username ?? '' },
      isAuthenticated: true,
      username,
    });
  },

  logout() {
    localStorage.removeItem(LS_ACCESS_TOKEN);
    localStorage.removeItem(LS_REFRESH_TOKEN);
    localStorage.removeItem(LS_AUTH_USER);
    localStorage.removeItem(LS_USERNAME);

    set({
      accessToken: null,
      refreshToken: null,
      user: null,
      isAuthenticated: false,
      username: null,
    });
  },
}));
