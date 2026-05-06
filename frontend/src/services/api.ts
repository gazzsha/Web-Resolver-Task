import axios from 'axios';
import type { Task, Submission, SubmissionResult, AIAnalysis, UserStatistics } from '@/types';

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
  getAll: async (): Promise<Task[]> => {
    const response = await api.get<Task[]>('/tasks');
    return response.data;
  },

  getById: async (id: string): Promise<Task> => {
    const response = await api.get<Task>(`/tasks/${id}`);
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
  getAnalysis: async (taskId: string): Promise<AIAnalysis> => {
    const response = await api.get<AIAnalysis>(`/ai-analysis/${taskId}`);
    return response.data;
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

export default api;
