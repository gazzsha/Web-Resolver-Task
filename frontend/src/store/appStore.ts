import { create } from 'zustand';
import type { Task, SubmissionResult } from '@/types';

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
  darkMode: false,
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
  toggleDarkMode: () => set((state) => ({ darkMode: !state.darkMode })),
  toggleSidebar: () => set((state) => ({ sidebarOpen: !state.sidebarOpen })),
  setLoading: (loading) => set({ loading }),
  setError: (error) => set({ error }),
}));
