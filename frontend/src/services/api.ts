import axios from 'axios';
import type { Task, Submission, SubmissionResult, AIAnalysisFull, UserStatistics, SubmissionSummary, UserStats } from '@/types';

const api = axios.create({
  baseURL: '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Dedicated instance for /auth endpoints (no /api/v1 prefix, no auth header needed)
export const authApi = axios.create({
  baseURL: '/auth',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor — attach Bearer token from localStorage
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('authToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor — on 401 clear auth state so RequireAuth redirects to /login
// TODO: automatic token refresh on 401 (MVP skipped — too many edge cases with concurrent requests)
api.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      // Import lazily to avoid circular dependency at module evaluation time
      import('@/store/authStore').then(({ useAuthStore }) => {
        useAuthStore.getState().logout();
      });
    }
    console.error('API Error:', axios.isAxiosError(error) ? error.response?.data ?? error.message : error);
    return Promise.reject(error);
  }
);

// Task services
export const taskService = {
  getAll: async (params?: { category?: string; difficulty?: 'Easy' | 'Medium' | 'Hard' }): Promise<Task[]> => {
    const response = await api.get<Task[]>('/tasks', { params });
    return response.data;
  },

  getById: async (id: string): Promise<Task> => {
    const response = await api.get<Task>(`/tasks/${id}`);
    return response.data;
  },

  getCategories: async (): Promise<string[]> => {
    const response = await api.get<string[]>('/tasks/categories');
    return response.data;
  },
};

// Admin services (требуют роль TEACHER на бэкенде)
export interface TaskImportError {
  line: number;
  message: string;
}
export interface TaskImportResult {
  importedCount: number;
  skippedCount: number;
  errors: TaskImportError[];
}

export const adminService = {
  importTasksFromCsv: async (file: File): Promise<TaskImportResult> => {
    const form = new FormData();
    form.append('file', file);
    const response = await api.post<TaskImportResult>('/admin/tasks/import', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },
};

// Submission services
export const submissionService = {
  submit: async (submission: Submission): Promise<{ id: string }> => {
    const response = await api.patch<{ id: string }>('/task-resolver/task/start', submission);
    return response.data;
  },

  getResult: async (taskId: string): Promise<SubmissionResult> => {
    const response = await api.get<SubmissionResult>(`/task-results/${taskId}`);
    return response.data;
  },

  getTestResults: async (testId: string): Promise<SubmissionResult[]> => {
    const response = await api.get<SubmissionResult[]>(`/task-results/test/${testId}`);
    return response.data;
  },
};

// AI Analysis services
export const aiService = {
  getAnalysis: async (submissionId: string): Promise<AIAnalysisFull | null> => {
    try {
      const response = await api.get<AIAnalysisFull>(`/ai-analysis/${submissionId}`);
      return response.data;
    } catch (e: unknown) {
      if (axios.isAxiosError(e) && e.response?.status === 404) return null;
      throw e;
    }
  },

  explainError: async (
    taskId: string,
    error: string,
    testInput: string
  ): Promise<{ errorType: string; explanation: string; fixSuggestions: string[] }> => {
    const response = await api.post(`/ai-analysis/${taskId}/explain`, { error, testInput });
    return response.data;
  },
};

// Statistics services
export const statsService = {
  getUserStats: async (userId: string): Promise<UserStatistics> => {
    const response = await api.get<UserStatistics>(`/users/${userId}/stats`);
    return response.data;
  },
};

// Me-service — authenticated user's own submissions and stats
export const meService = {
  getSubmissions: async (): Promise<SubmissionSummary[]> => {
    const r = await api.get<SubmissionSummary[]>('/me/submissions');
    return r.data;
  },
  getStats: async (): Promise<UserStats> => {
    const r = await api.get<UserStats>('/me/stats');
    return r.data;
  },
};

export default api;
