import React, { useState } from 'react';
import { Navigate, Link as RouterLink, useNavigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Chip,
  Collapse,
  Link,
  TextField,
  Typography,
  useTheme,
} from '@mui/material';
import { useAuthStore } from '@/store/authStore';
import { authService } from '@/services/authService';
import AuthLayout from '@/components/auth/AuthLayout';
import { authInputSx } from '@/styles/authInputSx';

const DEMO_ACCOUNTS: ReadonlyArray<{ email: string; password: string; role: string }> = [
  { email: 'student1@diplom.local', password: 'Student123!', role: 'STUDENT' },
  { email: 'student2@diplom.local', password: 'Student123!', role: 'STUDENT' },
  { email: 'teacher@diplom.local', password: 'Teacher123!', role: 'TEACHER' },
];

const LoginPage: React.FC = () => {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const navigate = useNavigate();
  const theme = useTheme();
  const inputSx = authInputSx(theme);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [demoOpen, setDemoOpen] = useState(true);

  if (isAuthenticated) return <Navigate to="/tasks" replace />;

  const fillDemo = (acc: { email: string; password: string }) => {
    setEmail(acc.email);
    setPassword(acc.password);
    setErrorMsg(null);
  };

  const submit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setErrorMsg(null);
    setLoading(true);
    try {
      const resp = await authService.login({ email, password });
      useAuthStore.getState().setSession(resp);
      navigate('/tasks');
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : 'Ошибка входа');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthLayout
      title="Вход"
      subtitle="Войдите, чтобы решать задачи и получать AI-фидбэк"
      footer={
        <Typography sx={{ fontSize: 13, color: '#9ca3c4' }}>
          Нет аккаунта?{' '}
          <Link component={RouterLink} to="/register" sx={{ color: '#7dd3fc', textDecoration: 'none', fontWeight: 600, '&:hover': { color: '#bae6fd' } }}>
            Создайте за минуту →
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
        <TextField label="Пароль" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required fullWidth autoComplete="current-password" inputProps={{ minLength: 8 }} sx={inputSx} />
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
          {loading ? 'Входим…' : 'Войти →'}
        </Button>
      </Box>

      <Box sx={{ mt: 3, pt: 2.5, borderTop: '1px solid rgba(255,255,255,0.08)' }}>
        <Box
          onClick={() => setDemoOpen(!demoOpen)}
          role="button"
          tabIndex={0}
          aria-expanded={demoOpen}
          onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setDemoOpen(!demoOpen); } }}
          sx={{ cursor: 'pointer' }}
        >
          <Typography sx={{ fontSize: 11, fontFamily: '"JetBrains Mono", monospace', color: '#7dd3fc', letterSpacing: '0.08em', textTransform: 'uppercase' }}>
            // demo accounts {demoOpen ? '▾' : '▸'}
          </Typography>
        </Box>
        <Collapse in={demoOpen}>
          <Box sx={{ mt: 1.5, display: 'flex', flexDirection: 'column', gap: 0.75 }}>
            {DEMO_ACCOUNTS.map((acc) => (
              <Box
                key={acc.email}
                onClick={() => fillDemo(acc)}
                role="button"
                tabIndex={0}
                aria-label={'Заполнить аккаунтом ' + acc.email}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); fillDemo(acc); } }}
                sx={{
                  display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                  background: 'rgba(13,17,41,0.4)', border: '1px solid rgba(255,255,255,0.06)',
                  borderRadius: '8px', px: 1.5, py: 0.9, cursor: 'pointer',
                  transition: 'all 0.15s ease',
                  '&:hover': { background: 'rgba(99,102,241,0.12)', borderColor: 'rgba(125,211,252,0.3)' },
                }}
              >
                <Box>
                  <Typography sx={{ fontSize: 12, fontFamily: '"JetBrains Mono", monospace', color: '#e6edf3' }}>{acc.email}</Typography>
                  <Typography sx={{ fontSize: 10, fontFamily: '"JetBrains Mono", monospace', color: '#7dd3fc' }}>{acc.password}</Typography>
                </Box>
                <Chip
                  label={acc.role}
                  size="small"
                  sx={{
                    height: 20, fontSize: 9, fontWeight: 700, fontFamily: '"JetBrains Mono", monospace',
                    background: acc.role === 'TEACHER' ? 'rgba(236,72,153,0.2)' : 'rgba(99,102,241,0.2)',
                    color: acc.role === 'TEACHER' ? '#f9a8d4' : '#a5b4fc',
                    border: `1px solid ${acc.role === 'TEACHER' ? 'rgba(236,72,153,0.4)' : 'rgba(99,102,241,0.4)'}`,
                  }}
                />
              </Box>
            ))}
          </Box>
        </Collapse>
      </Box>
    </AuthLayout>
  );
};

export default LoginPage;
