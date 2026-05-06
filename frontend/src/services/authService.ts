import axios from 'axios';
import { authApi } from '@/services/api';
import type { LoginRequest, RegisterRequest, RefreshRequest, JwtResponse } from '@/types/auth';

function extractMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError(error)) {
    const serverMessage = (error.response?.data as { errorMessage?: string } | undefined)?.errorMessage;
    if (serverMessage) return serverMessage;
  }
  return fallback;
}

export const authService = {
  async register(req: RegisterRequest): Promise<JwtResponse> {
    try {
      const response = await authApi.post<JwtResponse>('/register', req);
      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        throw new Error('Email уже занят');
      }
      throw new Error(extractMessage(error, 'Ошибка регистрации'));
    }
  },

  async login(req: LoginRequest): Promise<JwtResponse> {
    try {
      const response = await authApi.post<JwtResponse>('/login', req);
      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 401) {
        throw new Error('Неверный email или пароль');
      }
      throw new Error(extractMessage(error, 'Ошибка входа'));
    }
  },

  async refresh(req: RefreshRequest): Promise<JwtResponse> {
    try {
      const response = await authApi.post<JwtResponse>('/refresh', req);
      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 401) {
        throw new Error('Сессия истекла, войдите снова');
      }
      throw new Error(extractMessage(error, 'Ошибка обновления токена'));
    }
  },
};
