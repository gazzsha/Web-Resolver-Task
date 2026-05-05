import axios from 'axios';
import type { Task, Submission, SubmissionResult, AIAnalysis, UserStatistics } from '@/types';

const api = axios.create({
  baseURL: '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor for adding auth token
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

// Response interceptor for error handling
api.interceptors.response.use(
  (response) => response,
  (error) => {
    console.error('API Error:', error.response?.data || error.message);
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
    // Mock implementation - replace with actual API
    const response = await api.get<UserStatistics>(`/users/${userId}/stats`);
    return response.data;
  },
};

export default api;
