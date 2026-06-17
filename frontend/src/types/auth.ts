export type Role = 'STUDENT' | 'TEACHER';

export interface RegisterRequest {
  email: string;
  password: string;
  username: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface JwtResponse {
  accessToken: string;
  refreshToken: string;
  role: Role;
  email: string;
  username: string;
}

export interface AuthUser {
  email: string;
  role: Role;
  username: string;
}
