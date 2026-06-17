import React, { useState, useMemo } from 'react';
import { Navigate, Link as RouterLink, useNavigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  LinearProgress,
  Link,
  TextField,
  Typography,
  useTheme,
} from '@mui/material';
import { useAuthStore } from '@/store/authStore';
import { authService } from '@/services/authService';
import AuthLayout from '@/components/auth/AuthLayout';
import { authInputSx as buildAuthInputSx } from '@/styles/authInputSx';

const USERNAME_REGEX = /^[a-zA-Z0-9_]+$/;

function validateUsername(value: string): string | null {
  if (value.length === 0) return 'Поле обязательно';
  if (value.length < 3) return 'Минимум 3 символа';
  if (value.length > 32) return 'Максимум 32 символа';
  if (!USERNAME_REGEX.test(value)) return 'Только латиница, цифры и _';
  return null;
}

function passwordScore(pwd: string): number {
  let s = 0;
  if (pwd.length >= 8) s += 25;
  if (pwd.length >= 12) s += 15;
  if (/[a-z]/.test(pwd) && /[A-Z]/.test(pwd)) s += 20;
  if (/[0-9]/.test(pwd)) s += 20;
  if (/[^a-zA-Z0-9]/.test(pwd)) s += 20;
  return Math.min(100, s);
}

const RegisterPage: React.FC = () => {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const navigate = useNavigate();
  const theme = useTheme();
  const inputSx = {
    ...buildAuthInputSx(theme),
    '& .MuiFormHelperText-root': { color: '#8a92b8' },
  };

  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [usernameBlurred, setUsernameBlurred] = useState(false);
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [submitAttempted, setSubmitAttempted] = useState(false);

  if (isAuthenticated) return <Navigate to="/tasks" replace />;

  const score = useMemo(() => passwordScore(password), [password]);
  const scoreLabel = score < 40 ? 'Слабый' : score < 70 ? 'Нормальный' : 'Сильный';
  const scoreColor = score < 40 ? '#f87171' : score < 70 ? '#fbbf24' : '#34d399';

  const mismatchVisible = confirm.length > 0 && confirm !== password;

  const usernameError = validateUsername(username);
  const showUsernameError = usernameError !== null && (usernameBlurred || submitAttempted);

  const submit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setSubmitAttempted(true);
    if (usernameError) {
      setErrorMsg(usernameError);
      return;
    }
    if (password.length < 8) {
      setErrorMsg('Пароль должен содержать минимум 8 символов');
      return;
    }
    if (password !== confirm) {
      setErrorMsg('Пароли не совпадают');
      return;
    }
    setErrorMsg(null);
    setLoading(true);
    try {
      const resp = await authService.register({ email, password, username });
      useAuthStore.getState().setSession(resp);
      navigate('/tasks');
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : 'Ошибка регистрации');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthLayout
      title="Регистрация"
      subtitle="Создайте аккаунт студента и начните решать"
      footer={
        <Typography sx={{ fontSize: 13, color: '#9ca3c4' }}>
          Уже зарегистрированы?{' '}
          <Link component={RouterLink} to="/login" sx={{ color: '#7dd3fc', textDecoration: 'none', fontWeight: 600, '&:hover': { color: '#bae6fd' } }}>
            Войти →
          </Link>
        </Typography>
      }
    >
      {errorMsg && (
        <Alert severity="error" sx={{ mb: 2, background: 'rgba(248,113,113,0.12)', color: '#fca5a5', border: '1px solid rgba(248,113,113,0.25)', '& .MuiAlert-icon': { color: '#fca5a5' } }}>
          {errorMsg}
        </Alert>
      )}

      <Box component="form" onSubmit={submit} noValidate sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <TextField label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required fullWidth autoComplete="email" autoFocus sx={inputSx} />

        <TextField
          label="Имя пользователя"
          type="text"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          onBlur={() => setUsernameBlurred(true)}
          required
          fullWidth
          autoComplete="username"
          inputProps={{ maxLength: 32 }}
          error={showUsernameError}
          helperText={showUsernameError ? usernameError : '3–32 символа: латиница, цифры, _'}
          sx={{
            ...inputSx,
            '& .MuiFormHelperText-root': showUsernameError
              ? { color: '#f87171' }
              : { color: '#8a92b8' },
          }}
        />

        <Box>
          <TextField
            label="Пароль" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
            required fullWidth autoComplete="new-password" inputProps={{ minLength: 8 }}
            helperText="Минимум 8 символов · буквы + цифры + спецсимвол"
            sx={inputSx}
          />
          {password.length > 0 && (
            <Box sx={{ mt: 1, display: 'flex', alignItems: 'center', gap: 1.5 }}>
              <LinearProgress
                variant="determinate"
                value={score}
                sx={{
                  flex: 1, height: 6, borderRadius: 3, background: 'rgba(255,255,255,0.06)',
                  '& .MuiLinearProgress-bar': { background: scoreColor, borderRadius: 3 },
                }}
              />
              <Typography sx={{ fontSize: 11, fontFamily: '"JetBrains Mono", monospace', color: scoreColor, minWidth: 70, textAlign: 'right' }}>
                {scoreLabel}
              </Typography>
            </Box>
          )}
        </Box>

        <TextField
          label="Подтвердите пароль" type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)}
          required fullWidth autoComplete="new-password"
          error={mismatchVisible}
          helperText={mismatchVisible ? 'Пароли не совпадают' : ' '}
          sx={inputSx}
        />

        <Button
          type="submit"
          variant="contained"
          fullWidth
          disabled={loading}
          sx={{
            mt: 1, py: 1.4, fontSize: 15, fontWeight: 700, textTransform: 'none', letterSpacing: '0.02em',
            background: 'linear-gradient(135deg, #6366f1 0%, #ec4899 100%)',
            boxShadow: '0 8px 24px rgba(99,102,241,0.35)',
            '&:hover': { background: 'linear-gradient(135deg, #4f46e5 0%, #db2777 100%)', boxShadow: '0 12px 32px rgba(99,102,241,0.5)' },
            '&.Mui-disabled': { background: 'rgba(99,102,241,0.3)', color: 'rgba(255,255,255,0.5)' },
          }}
        >
          {loading ? 'Создаём аккаунт…' : 'Зарегистрироваться →'}
        </Button>
      </Box>
    </AuthLayout>
  );
};

export default RegisterPage;
