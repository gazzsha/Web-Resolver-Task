import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Grid,
  Card,
  CardContent,
  Typography,
  Box,
  Button,
  Chip,
  alpha,
  useTheme,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableRow,
} from '@mui/material';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import SendIcon from '@mui/icons-material/Send';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import { useAuthStore } from '@/store/authStore';
import { meService } from '@/services/api';
import type { UserStats, SubmissionSummary } from '@/types';
import { brand } from '@/theme/theme';

interface StatCard {
  label: string;
  value: string | number;
  icon: React.ReactNode;
  accentColor: string;
  description: string;
}

const STATUS_MAP: Record<
  SubmissionSummary['status'],
  { label: string; color: 'success' | 'warning' | 'error' | 'info' | 'default' }
> = {
  SUCCESS: { label: 'Принято', color: 'success' },
  PARTIAL_SUCCESS: { label: 'Частично', color: 'warning' },
  FAILED: { label: 'Не принято', color: 'error' },
  ERROR: { label: 'Ошибка', color: 'error' },
  PENDING: { label: 'В ожидании', color: 'info' },
  PROCESSING: { label: 'Выполняется', color: 'info' },
};

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' });
}

const Dashboard = () => {
  const theme = useTheme();
  const navigate = useNavigate();
  const userEmail = useAuthStore((s) => s.user?.email);
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState<UserStats | null>(null);

  useEffect(() => {
    meService
      .getStats()
      .then((data) => setStats(data))
      .catch(() => setStats(null))
      .finally(() => setLoading(false));
  }, []);

  const isDark = theme.palette.mode === 'dark';

  const qualityValue =
    stats?.averageQuality != null ? `${stats.averageQuality.toFixed(0)}%` : '—';

  const statCards: StatCard[] = [
    {
      label: 'Решено задач',
      value: stats?.tasksSolved ?? '—',
      icon: <CheckCircleOutlineIcon sx={{ fontSize: 26 }} />,
      accentColor: '#22c55e',
      description: 'Успешно сданных задач',
    },
    {
      label: 'Всего попыток',
      value: stats?.totalSubmissions ?? '—',
      icon: <SendIcon sx={{ fontSize: 26 }} />,
      accentColor: brand.indigo,
      description: 'Отправленных решений',
    },
    {
      label: 'Среднее качество кода',
      value: qualityValue,
      icon: <AutoAwesomeIcon sx={{ fontSize: 26 }} />,
      accentColor: brand.rose,
      description: 'По оценке AI-анализатора',
    },
  ];

  return (
    <Box sx={{ width: '100%', maxWidth: 1400 }}>

      {/* ──────────── Hero block ──────────── */}
      <Box
        sx={{
          borderRadius: 3,
          mb: 4,
          p: { xs: 3, sm: 4, md: 5 },
          position: 'relative',
          overflow: 'hidden',
          background: isDark
            ? `linear-gradient(135deg, ${alpha(brand.indigo, 0.25)} 0%, ${alpha(brand.rose, 0.12)} 100%)`
            : `linear-gradient(135deg, ${alpha(brand.indigo, 0.08)} 0%, ${alpha(brand.rose, 0.06)} 100%)`,
          border: `1px solid ${isDark ? alpha(brand.indigo, 0.25) : alpha(brand.indigo, 0.15)}`,
        }}
      >
        {/* Decorative blob */}
        <Box
          sx={{
            position: 'absolute',
            right: -60,
            top: -60,
            width: 240,
            height: 240,
            borderRadius: '50%',
            background: `radial-gradient(circle, ${alpha(brand.indigo, 0.25)} 0%, transparent 70%)`,
            pointerEvents: 'none',
          }}
        />
        <Box
          sx={{
            position: 'absolute',
            right: 80,
            bottom: -40,
            width: 160,
            height: 160,
            borderRadius: '50%',
            background: `radial-gradient(circle, ${alpha(brand.rose, 0.2)} 0%, transparent 70%)`,
            pointerEvents: 'none',
          }}
        />

        <Box sx={{ position: 'relative', zIndex: 1 }}>
          <Typography
            variant="overline"
            sx={{
              color: 'primary.main',
              fontWeight: 700,
              letterSpacing: '0.12em',
              mb: 1,
              display: 'block',
            }}
          >
            Добро пожаловать
          </Typography>
          <Typography
            variant="h3"
            sx={{
              fontWeight: 700,
              mb: 1,
              fontSize: { xs: '1.5rem', sm: '2rem', md: '2.25rem' },
            }}
          >
            {userEmail ? (
              <>
                Привет,{' '}
                <Box
                  component="span"
                  sx={{
                    background: `linear-gradient(135deg, ${brand.indigo} 0%, ${brand.rose} 100%)`,
                    WebkitBackgroundClip: 'text',
                    WebkitTextFillColor: 'transparent',
                    backgroundClip: 'text',
                  }}
                >
                  {userEmail.split('@')[0]}
                </Box>
              </>
            ) : (
              'Добро пожаловать!'
            )}
          </Typography>
          <Typography
            variant="body1"
            color="text.secondary"
            sx={{ mb: 3, maxWidth: 480 }}
          >
            Решайте алгоритмические задачи, получайте AI-обратную связь и отслеживайте свой прогресс.
          </Typography>
          <Button
            variant="contained"
            size="large"
            endIcon={<ArrowForwardIcon />}
            onClick={() => navigate('/tasks')}
          >
            Все задачи
          </Button>
        </Box>
      </Box>

      {/* ──────────── Stat cards ──────────── */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        {statCards.map((stat) => (
          <Grid item xs={12} sm={4} key={stat.label}>
            <Card
              sx={{
                height: '100%',
                transition: 'transform 0.2s, box-shadow 0.2s',
                '&:hover': {
                  transform: 'translateY(-3px)',
                  boxShadow: isDark
                    ? `0 8px 30px ${alpha(stat.accentColor, 0.25)}`
                    : `0 8px 24px ${alpha(stat.accentColor, 0.18)}`,
                },
              }}
            >
              <CardContent sx={{ p: 3 }}>
                <Box
                  sx={{
                    width: 48,
                    height: 48,
                    borderRadius: 2,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    mb: 2,
                    color: stat.accentColor,
                    background: alpha(stat.accentColor, isDark ? 0.18 : 0.1),
                  }}
                >
                  {stat.icon}
                </Box>
                {loading ? (
                  <>
                    <Skeleton variant="text" width="50%" height={48} />
                    <Skeleton variant="text" width="70%" />
                  </>
                ) : (
                  <>
                    <Typography
                      variant="h3"
                      sx={{
                        fontWeight: 700,
                        fontSize: '2rem',
                        mb: 0.5,
                        color: 'text.primary',
                        fontFamily: '"JetBrains Mono", monospace',
                      }}
                    >
                      {stat.value}
                    </Typography>
                    <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.primary', mb: 0.25 }}>
                      {stat.label}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {stat.description}
                    </Typography>
                  </>
                )}
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {/* ──────────── Recent submissions ──────────── */}
      <Card>
        <CardContent sx={{ p: 3 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
            <Typography variant="h6" sx={{ fontWeight: 700 }}>
              Последние решения
            </Typography>
            {!loading && stats && stats.recentSubmissions.length > 0 && (
              <Button
                size="small"
                variant="text"
                endIcon={<ArrowForwardIcon fontSize="small" />}
                onClick={() => navigate('/submissions')}
              >
                Все решения
              </Button>
            )}
          </Box>

          {loading ? (
            <Box>
              {[0, 1, 2].map((i) => (
                <Box key={i} sx={{ display: 'flex', gap: 2, mb: 2, alignItems: 'center' }}>
                  <Skeleton variant="circular" width={36} height={36} />
                  <Box sx={{ flex: 1 }}>
                    <Skeleton variant="text" width="40%" />
                    <Skeleton variant="text" width="25%" />
                  </Box>
                  <Skeleton variant="rectangular" width={80} height={24} sx={{ borderRadius: 1 }} />
                </Box>
              ))}
            </Box>
          ) : stats && stats.recentSubmissions.length > 0 ? (
            <Table size="small">
              <TableBody>
                {stats.recentSubmissions.map((item) => {
                  const { label, color } = STATUS_MAP[item.status] ?? { label: item.status, color: 'default' as const };
                  return (
                    <TableRow
                      key={item.id}
                      hover
                      sx={{
                        cursor: 'pointer',
                        '&:last-child td': { border: 0 },
                      }}
                      onClick={() => navigate(`/results/${item.id}`)}
                    >
                      <TableCell sx={{ pl: 0 }}>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                          {item.taskTitle ?? 'Без названия'}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {formatDate(item.createdAt)}
                        </Typography>
                      </TableCell>
                      <TableCell align="right">
                        <Chip
                          label={label}
                          color={color}
                          size="small"
                          sx={{ fontWeight: 700 }}
                        />
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          ) : (
            <Box sx={{ textAlign: 'center', py: 5, color: 'text.secondary' }}>
              <HistoryPlaceholder isDark={isDark} />
              <Typography variant="body2" sx={{ mt: 2 }}>
                Здесь появятся ваши последние решения после первой отправки
              </Typography>
              <Button
                variant="outlined"
                size="small"
                sx={{ mt: 2 }}
                onClick={() => navigate('/tasks')}
              >
                Начать решать задачи
              </Button>
            </Box>
          )}
        </CardContent>
      </Card>
    </Box>
  );
};

// Inline SVG placeholder icon for empty state
const HistoryPlaceholder: React.FC<{ isDark: boolean }> = ({ isDark }) => (
  <Box
    component="svg"
    viewBox="0 0 80 80"
    sx={{ width: 72, height: 72, opacity: 0.4 }}
  >
    <circle cx="40" cy="40" r="38" fill={isDark ? '#334155' : '#e2e8f0'} />
    <rect x="24" y="34" width="32" height="3" rx="1.5" fill={isDark ? '#64748b' : '#94a3b8'} />
    <rect x="24" y="41" width="22" height="3" rx="1.5" fill={isDark ? '#64748b' : '#94a3b8'} />
    <rect x="24" y="48" width="28" height="3" rx="1.5" fill={isDark ? '#64748b' : '#94a3b8'} />
    <circle cx="40" cy="27" r="6" stroke={isDark ? '#818cf8' : '#6366f1'} strokeWidth="2.5" fill="none" />
    <path d="M40 23v4l3 2" stroke={isDark ? '#818cf8' : '#6366f1'} strokeWidth="2" strokeLinecap="round" fill="none" />
  </Box>
);

export default Dashboard;
