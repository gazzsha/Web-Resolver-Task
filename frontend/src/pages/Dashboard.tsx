import { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Grid,
  Card,
  CardContent,
  Typography,
  Box,
  Button,
  Alert,
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
import LocalFireDepartmentIcon from '@mui/icons-material/LocalFireDepartment';
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty';
import { useAuthStore } from '@/store/authStore';
import { meService, taskService } from '@/services/api';
import type { UserStats, SubmissionSummary, Task } from '@/types';
import { brand } from '@/theme/theme';
import KpiCard from '@/components/statistics/KpiCard';
import ActivityLineChart from '@/components/statistics/ActivityLineChart';
import SubmissionsMiniStats from '@/components/submissions/SubmissionsMiniStats';
import StreakCard from '@/components/statistics/StreakCard';
import TaskRecommendCard from '@/components/dashboard/TaskRecommendCard';
import { buildStreak } from '@/utils/submissionStats';

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

// Shuffle an array with a fixed seed (based on array length + first id so it's stable per session)
function stableShuffleOnce<T>(arr: T[]): T[] {
  const copy = [...arr];
  // Simple Fisher-Yates with deterministic seed derived from array content
  let seed = copy.length * 31;
  const rng = () => {
    seed = (seed * 1664525 + 1013904223) & 0xffffffff;
    return ((seed >>> 0) / 0xffffffff);
  };
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}

const Dashboard = () => {
  const theme = useTheme();
  const navigate = useNavigate();
  const userEmail = useAuthStore((s) => s.user?.email);
  const isDark = theme.palette.mode === 'dark';

  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState<UserStats | null>(null);
  const [statsError, setStatsError] = useState(false);
  const [submissions, setSubmissions] = useState<SubmissionSummary[]>([]);
  const [tasks, setTasks] = useState<Task[]>([]);
  // Shuffled once on tasks load
  const [shuffledTasks, setShuffledTasks] = useState<Task[]>([]);

  useEffect(() => {
    meService
      .getStats()
      .then((data) => setStats(data))
      .catch(() => { setStats(null); setStatsError(true); })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    meService.getSubmissions().then(setSubmissions).catch(() => {
      // Non-critical
    });
  }, []);

  useEffect(() => {
    taskService.getAll().then((data) => {
      setTasks(data);
      setShuffledTasks(stableShuffleOnce(data));
    }).catch(() => {
      // Non-critical
    });
  }, []);

  // Derived KPI values
  const streak = useMemo(() => buildStreak(submissions), [submissions]);
  const inProgressCount = useMemo(
    () => submissions.filter((s) => ['PENDING', 'PROCESSING'].includes(s.status)).length,
    [submissions]
  );
  const qualityValue = stats?.averageQuality != null ? `${stats.averageQuality.toFixed(0)}%` : '—';

  // Recommended tasks: Easy/Medium, not solved yet
  const recommendedTasks = useMemo(() => {
    const solvedTaskIds = new Set(
      submissions.filter((s) => s.status === 'SUCCESS').map((s) => s.taskId)
    );
    return shuffledTasks
      .filter((t) => ['Easy', 'Medium'].includes(t.difficulty) && !solvedTaskIds.has(t.testId))
      .slice(0, 4);
  }, [shuffledTasks, submissions]);

  const kpiCards = [
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
      label: 'Среднее качество',
      value: qualityValue,
      icon: <AutoAwesomeIcon sx={{ fontSize: 26 }} />,
      accentColor: brand.rose,
      description: 'По оценке AI-анализатора',
    },
    {
      label: 'Серия',
      value: streak.currentStreak,
      icon: <LocalFireDepartmentIcon sx={{ fontSize: 26 }} />,
      accentColor: '#f97316',
      description: 'Дней активности подряд',
    },
    {
      label: 'В процессе',
      value: inProgressCount,
      icon: <HourglassEmptyIcon sx={{ fontSize: 26 }} />,
      accentColor: '#6366f1',
      description: 'Отправок в обработке',
    },
  ];

  return (
    <Box sx={{ width: '100%' }}>
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
            sx={{ color: 'primary.main', fontWeight: 700, letterSpacing: '0.12em', mb: 1, display: 'block' }}
          >
            Добро пожаловать
          </Typography>
          <Typography
            variant="h3"
            component="h1"
            sx={{ fontWeight: 700, mb: 1, fontSize: { xs: '1.5rem', sm: '2rem', md: '2.25rem' } }}
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
          <Typography variant="body1" color="text.secondary" sx={{ mb: 3, maxWidth: 480 }}>
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

      {/* ──────────── Stats error ──────────── */}
      {!loading && statsError && (
        <Alert severity="error" sx={{ mb: 3 }}>
          Не удалось загрузить статистику. Попробуйте обновить страницу.
        </Alert>
      )}

      {/* ──────────── Row 1: 5 KPI cards ──────────── */}
      <Grid
        container
        spacing={2}
        sx={{ mb: 4 }}
        aria-busy={loading}
      >
        {kpiCards.map((kpi) => (
          <Grid item xs={12} sm={6} md={4} lg={2.4} key={kpi.label}>
            <KpiCard
              label={kpi.label}
              value={kpi.value}
              icon={kpi.icon}
              accentColor={kpi.accentColor}
              description={kpi.description}
              loading={loading}
            />
          </Grid>
        ))}
      </Grid>

      {/* ──────────── Row 2: ActivityLineChart 8 / SubmissionsMiniStats 4 ──────────── */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        <Grid item xs={12} md={8}>
          <Card sx={{ p: 3 }}>
            <ActivityLineChart submissions={submissions} />
          </Card>
        </Grid>
        <Grid item xs={12} md={4}>
          <SubmissionsMiniStats submissions={submissions} />
        </Grid>
      </Grid>

      {/* ──────────── Row 3: Recent submissions 8 / Recommendations 4 ──────────── */}
      <Grid container spacing={3}>
        {/* Recent submissions table */}
        <Grid item xs={12} md={8}>
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
                          sx={{ cursor: 'pointer', '&:last-child td': { border: 0 } }}
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
                            <Chip label={label} color={color} size="small" sx={{ fontWeight: 700 }} />
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
        </Grid>

        {/* Recommendations or StreakCard */}
        <Grid item xs={12} md={4}>
          {tasks.length > 0 && recommendedTasks.length > 0 ? (
            <Card>
              <CardContent sx={{ p: 3 }}>
                <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
                  Рекомендуемые задачи
                </Typography>
                {recommendedTasks.map((task) => (
                  <TaskRecommendCard
                    key={task.testId}
                    task={task}
                    onNavigate={(id) => navigate(`/tasks/${id}`)}
                  />
                ))}
              </CardContent>
            </Card>
          ) : (
            <Card>
              <CardContent sx={{ p: 3 }}>
                <StreakCard submissions={submissions} />
              </CardContent>
            </Card>
          )}
        </Grid>
      </Grid>
    </Box>
  );
};

// Inline SVG placeholder icon for empty state
const HistoryPlaceholder: React.FC<{ isDark: boolean }> = ({ isDark }) => (
  <Box component="svg" viewBox="0 0 80 80" sx={{ width: 72, height: 72, opacity: 0.4 }}>
    <circle cx="40" cy="40" r="38" fill={isDark ? '#334155' : '#e2e8f0'} />
    <rect x="24" y="34" width="32" height="3" rx="1.5" fill={isDark ? '#64748b' : '#94a3b8'} />
    <rect x="24" y="41" width="22" height="3" rx="1.5" fill={isDark ? '#64748b' : '#94a3b8'} />
    <rect x="24" y="48" width="28" height="3" rx="1.5" fill={isDark ? '#64748b' : '#94a3b8'} />
    <circle cx="40" cy="27" r="6" stroke={isDark ? '#818cf8' : '#6366f1'} strokeWidth="2.5" fill="none" />
    <path d="M40 23v4l3 2" stroke={isDark ? '#818cf8' : '#6366f1'} strokeWidth="2" strokeLinecap="round" fill="none" />
  </Box>
);

export default Dashboard;
