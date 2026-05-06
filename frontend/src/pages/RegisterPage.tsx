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
} from '@mui/material';
import { useAuthStore } from '@/store/authStore';
import { authService } from '@/services/authService';
import AuthLayout from '@/components/auth/AuthLayout';

const inputSx = {
  '& .MuiOutlinedInput-root': {
    background: 'rgba(13, 17, 41, 0.5)',
    color: '#f5f7fb',
    fontFamily: '"JetBrains Mono", monospace',
    fontSize: 14,
    transition: 'all 0.2s ease',
    '& fieldset': { borderColor: 'rgba(255,255,255,0.12)' },
    '&:hover fieldset': { borderColor: 'rgba(125,211,252,0.4)' },
    '&.Mui-focused fieldset': {
      borderColor: '#7dd3fc',
      boxShadow: '0 0 0 4px rgba(125,211,252,0.12)',
    },
  },
  '& .MuiInputLabel-root': { color: '#8a92b8', fontSize: 13 },
  '& .MuiInputLabel-root.Mui-focused': { color: '#7dd3fc' },
  '& input:-webkit-autofill': {
    WebkitBoxShadow: '0 0 0 1000px #11163a inset',
    WebkitTextFillColor: '#f5f7fb',
    caretColor: '#f5f7fb',
  },
  '& .MuiFormHelperText-root': { color: '#8a92b8' },
} as const;

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

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  if (isAuthenticated) return <Navigate to="/tasks" replace />;

  const score = useMemo(() => passwordScore(password), [password]);
  const scoreLabel = score < 40 ? 'Слабый' : score < 70 ? 'Нормальный' : 'Сильный';
  const scoreColor = score < 40 ? '#f87171' : score < 70 ? '#fbbf24' : '#34d399';

  const mismatchVisible = confirm.length > 0 && confirm !== password;

  const submit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
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
      const resp = await authService.register({ email, password });
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
