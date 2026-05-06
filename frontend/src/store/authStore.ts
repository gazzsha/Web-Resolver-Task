import { create } from 'zustand';
import type { AuthUser, JwtResponse } from '@/types/auth';

interface AuthState {
  user: AuthUser | null;
  accessToken: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;

  setSession: (resp: JwtResponse) => void;
  logout: () => void;
}

const LS_ACCESS_TOKEN = 'authToken';
const LS_REFRESH_TOKEN = 'refreshToken';
const LS_AUTH_USER = 'authUser';

function loadFromStorage(): Pick<AuthState, 'user' | 'accessToken' | 'refreshToken' | 'isAuthenticated'> {
  const accessToken = localStorage.getItem(LS_ACCESS_TOKEN);
  const refreshToken = localStorage.getItem(LS_REFRESH_TOKEN);
  const rawUser = localStorage.getItem(LS_AUTH_USER);

  let user: AuthUser | null = null;
  if (rawUser) {
    try {
      user = JSON.parse(rawUser) as AuthUser;
    } catch {
      // Corrupted JSON — treat as logged out
    }
  }

  return {
    accessToken,
    refreshToken,
    user,
    isAuthenticated: accessToken !== null,
  };
}

export const useAuthStore = create<AuthState>((set) => ({
  ...loadFromStorage(),

  setSession(resp: JwtResponse) {
    localStorage.setItem(LS_ACCESS_TOKEN, resp.accessToken);
    localStorage.setItem(LS_REFRESH_TOKEN, resp.refreshToken);
    localStorage.setItem(LS_AUTH_USER, JSON.stringify({ email: resp.email, role: resp.role }));

    set({
      accessToken: resp.accessToken,
      refreshToken: resp.refreshToken,
      user: { email: resp.email, role: resp.role },
      isAuthenticated: true,
    });
  },

  logout() {
    localStorage.removeItem(LS_ACCESS_TOKEN);
    localStorage.removeItem(LS_REFRESH_TOKEN);
    localStorage.removeItem(LS_AUTH_USER);

    set({
      accessToken: null,
      refreshToken: null,
      user: null,
      isAuthenticated: false,
    });
  },
}));
