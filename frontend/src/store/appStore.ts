import { create } from 'zustand';
import type { Task, SubmissionResult } from '@/types';

// Resolve initial dark mode: localStorage → prefers-color-scheme → light
function resolveInitialDarkMode(): boolean {
  try {
    const stored = localStorage.getItem('themeMode');
    if (stored === 'dark') return true;
    if (stored === 'light') return false;
  } catch {
    // localStorage unavailable (e.g. SSR or private mode)
  }
  return typeof window !== 'undefined' && window.matchMedia('(prefers-color-scheme: dark)').matches;
}

interface AppState {
  // Tasks
  tasks: Task[];
  currentTask: Task | null;
  loading: boolean;
  error: string | null;

  // Submissions
  currentSubmission: SubmissionResult | null;
  submissionHistory: SubmissionResult[];

  // UI State
  darkMode: boolean;
  sidebarOpen: boolean;

  // Actions
  setTasks: (tasks: Task[]) => void;
  setCurrentTask: (task: Task | null) => void;
  setCurrentSubmission: (submission: SubmissionResult | null) => void;
  addSubmission: (submission: SubmissionResult) => void;
  toggleDarkMode: () => void;
  toggleSidebar: () => void;
  setLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
}

export const useAppStore = create<AppState>((set) => ({
  // Initial state
  tasks: [],
  currentTask: null,
  loading: false,
  error: null,
  currentSubmission: null,
  submissionHistory: [],
  darkMode: resolveInitialDarkMode(),
  sidebarOpen: true,

  // Actions
  setTasks: (tasks) => set({ tasks }),
  setCurrentTask: (task) => set({ currentTask: task }),
  setCurrentSubmission: (submission) => set({ currentSubmission: submission }),
  addSubmission: (submission) =>
    set((state) => ({
      submissionHistory: [submission, ...state.submissionHistory],
      currentSubmission: submission,
    })),
  toggleDarkMode: () =>
    set((state) => {
      const next = !state.darkMode;
      try {
        localStorage.setItem('themeMode', next ? 'dark' : 'light');
      } catch {
        // ignore
      }
      return { darkMode: next };
    }),
  toggleSidebar: () => set((state) => ({ sidebarOpen: !state.sidebarOpen })),
  setLoading: (loading) => set({ loading }),
  setError: (error) => set({ error }),
}));
