import React, { createContext, useContext } from 'react';
import { useAppStore } from '@/store/appStore';

interface ThemeModeContextValue {
  mode: 'light' | 'dark';
  toggleMode: () => void;
}

const ThemeModeContext = createContext<ThemeModeContextValue>({
  mode: 'light',
  toggleMode: () => undefined,
});

/**
 * Provides theme mode (light/dark) via React Context, backed by Zustand appStore.
 * Persistence (localStorage) and prefers-color-scheme detection are handled
 * by the store initialiser in appStore.ts.
 */
export const ThemeModeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const darkMode = useAppStore((s) => s.darkMode);
  const toggleDarkMode = useAppStore((s) => s.toggleDarkMode);

  const value: ThemeModeContextValue = {
    mode: darkMode ? 'dark' : 'light',
    toggleMode: toggleDarkMode,
  };

  return <ThemeModeContext.Provider value={value}>{children}</ThemeModeContext.Provider>;
};

export function useThemeMode(): ThemeModeContextValue {
  return useContext(ThemeModeContext);
}
