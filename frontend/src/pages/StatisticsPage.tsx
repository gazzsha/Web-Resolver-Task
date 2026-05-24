import { useEffect, useState, useMemo } from 'react';
import {
  Box,
  Typography,
  Card,
  Grid,
  Alert,
  Skeleton,
  Paper,
  alpha,
  useTheme,
} from '@mui/material';
import BarChartIcon from '@mui/icons-material/BarChart';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import SendIcon from '@mui/icons-material/Send';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import PendingOutlinedIcon from '@mui/icons-material/PendingOutlined';
import WhatshotOutlinedIcon from '@mui/icons-material/WhatshotOutlined';
import {
  PieChart,
  Pie,
  Cell,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from 'recharts';
import { meService } from '@/services/api';
import type { UserStats, SubmissionSummary } from '@/types';
import { brand } from '@/theme/theme';
import { buildLangData, buildStatusData, buildStreak } from '@/utils/submissionStats';
import KpiCard from '@/components/statistics/KpiCard';
import ActivityLineChart from '@/components/statistics/ActivityLineChart';
import ErrorDistributionList from '@/components/statistics/ErrorDistributionList';
import StreakCard from '@/components/statistics/StreakCard';

// ── constants ─────────────────────────────────────────────────────────────────

const LANG_COLORS: Record<string, string> = {
  java: '#f89820',
  kotlin: '#7f52ff',
  python: '#3572a5',
};

const MIN_SUBMISSIONS_FOR_CHARTS = 5;

// ── custom tooltip ────────────────────────────────────────────────────────────

interface TooltipEntry {
  dataKey?: string;
  name: string;
  value: number;
  color: string;
}

interface CustomTooltipProps {
  active?: boolean;
  payload?: TooltipEntry[];
  label?: string;
}

const CustomTooltip = ({ active, payload, label }: CustomTooltipProps) => {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';
  if (!active || !payload?.length) return null;
  return (
    <Box
      sx={{
        background: isDark ? '#1a1d2e' : '#fff',
        border: `1px solid ${isDark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.1)'}`,
        borderRadius: 2,
        p: '8px 12px',
        boxShadow: '0 4px 16px rgba(0,0,0,0.15)',
      }}
    >
      {label && (
        <Typography variant="caption" sx={{ fontWeight: 700, display: 'block', mb: 0.25 }}>
          {label}
        </Typography>
      )}
      {payload.map((entry) => (
        <Typography key={entry.dataKey ?? entry.name} variant="caption" sx={{ display: 'block', color: entry.color }}>
          {entry.name}: <strong>{entry.value}</strong>
        </Typography>
      ))}
    </Box>
  );
};

// ── empty state ───────────────────────────────────────────────────────────────

const ChartEmptyState: React.FC<{ isDark: boolean }> = ({ isDark }) => (
  <Box
    sx={{
      textAlign: 'center',
      py: 6,
      borderRadius: 2,
      background: isDark ? alpha(brand.indigo, 0.05) : alpha(brand.indigo, 0.03),
      border: `1px dashed ${alpha(brand.indigo, 0.2)}`,
    }}
  >
    <BarChartIcon sx={{ fontSize: 48, color: alpha(brand.indigo, 0.35), mb: 1.5 }} />
    <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 500 }}>
      Решите хотя бы {MIN_SUBMISSIONS_FOR_CHARTS} задач, чтобы увидеть статистику
    </Typography>
  </Box>
);

// ── main component ────────────────────────────────────────────────────────────

const StatisticsPage = () => {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

  const [stats, setStats] = useState<UserStats | null>(null);
  const [submissions, setSubmissions] = useState<SubmissionSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([meService.getStats(), meService.getSubmissions()])
      .then(([s, subs]) => {
        setStats(s);
        setSubmissions(subs);
        setError(null);
      })
      .catch(() => {
        setError('Не удалось загрузить статистику. Проверьте подключение к серверу.');
      })
      .finally(() => setLoading(false));
  }, []);

  const langData = useMemo(() => buildLangData(submissions), [submissions]);
  const statusData = useMemo(() => buildStatusData(submissions), [submissions]);
  const hasEnoughData = submissions.length >= MIN_SUBMISSIONS_FOR_CHARTS;

  const { currentStreak, maxStreak } = useMemo(() => buildStreak(submissions), [submissions]);

  // KPI derived values
  const qualityValue =
    stats?.averageQuality != null ? `${stats.averageQuality.toFixed(0)}%` : '—';
  const qualityDescription =
    stats?.averageQuality != null ? 'Средний балл AI-анализатора' : 'AI-анализ не выполнялся';

  const tasksInProgress = useMemo(() => {
    const nonSuccess = submissions.filter((s) => s.status !== 'SUCCESS');
    return new Set(nonSuccess.map((s) => s.taskId)).size;
  }, [submissions]);

  const bestStreak = maxStreak;

  // recharts colors
  const langColors = langData.map((d) => LANG_COLORS[d.name] ?? brand.indigo);
  const statusColors = [
    theme.palette.success.main,
    theme.palette.warning.main,
    theme.palette.error.main,
    theme.palette.error.dark,
    theme.palette.info.main,
    theme.palette.info.dark,
  ];

  return (
    <Box sx={{ width: '100%', maxWidth: 1400 }}>
      {/* Page header */}
      <Box sx={{ mb: 4 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 0.5 }}>
          <BarChartIcon
            sx={{
              fontSize: 28,
              color: brand.rose,
              filter: isDark ? `drop-shadow(0 0 6px ${alpha(brand.rose, 0.6)})` : 'none',
            }}
          />
          <Typography variant="h4" component="h1" sx={{ fontWeight: 700 }}>
            Статистика
          </Typography>
        </Box>
        <Typography variant="body1" color="text.secondary">
          Ваш прогресс и аналитика решений
        </Typography>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {error}
        </Alert>
      )}

      {/* ── 5 KPI cards ─────────────────────────────────────────────── */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        <Grid item xs={12} sm={6} md={2.4}>
          <KpiCard
            label="Решено задач"
            value={stats?.tasksSolved ?? '—'}
            icon={<CheckCircleOutlineIcon sx={{ fontSize: 26 }} />}
            accentColor="#22c55e"
            description="Уникальных задач с успешным решением"
            loading={loading}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={2.4}>
          <KpiCard
            label="Всего попыток"
            value={stats?.totalSubmissions ?? '—'}
            icon={<SendIcon sx={{ fontSize: 26 }} />}
            accentColor={brand.indigo}
            description="Отправленных решений"
            loading={loading}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={2.4}>
          <KpiCard
            label="Среднее качество кода"
            value={qualityValue}
            icon={<AutoAwesomeIcon sx={{ fontSize: 26 }} />}
            accentColor={brand.rose}
            description={qualityDescription}
            loading={loading}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={2.4}>
          <KpiCard
            label="Задач в работе"
            value={loading ? '—' : tasksInProgress}
            icon={<PendingOutlinedIcon sx={{ fontSize: 26 }} />}
            accentColor={brand.indigo}
            description="Задач без успешного решения"
            loading={loading}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={2.4}>
          <KpiCard
            label="Лучшая серия"
            value={loading ? '—' : `${bestStreak} дн.`}
            icon={<WhatshotOutlinedIcon sx={{ fontSize: 26 }} />}
            accentColor="#f97316"
            description="Максимум дней подряд с активностью"
            loading={loading}
          />
        </Grid>
      </Grid>

      {/* ── Charts row ──────────────────────────────────────────────── */}
      {loading ? (
        <Grid container spacing={3} sx={{ mb: 4 }}>
          <Grid item xs={12} md={4}>
            <Card sx={{ p: 3 }}>
              <Skeleton variant="text" width="50%" height={28} sx={{ mb: 2 }} />
              <Skeleton variant="circular" width={200} height={200} sx={{ mx: 'auto' }} />
            </Card>
          </Grid>
          <Grid item xs={12} md={8}>
            <Card sx={{ p: 3 }}>
              <Skeleton variant="text" width="50%" height={28} sx={{ mb: 2 }} />
              <Skeleton variant="rectangular" height={300} sx={{ borderRadius: 1 }} />
            </Card>
          </Grid>
        </Grid>
      ) : !hasEnoughData ? (
        <Grid container spacing={3} sx={{ mb: 4 }}>
          <Grid item xs={12} md={4}>
            <Card sx={{ p: 3 }}>
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
                По языкам
              </Typography>
              <ChartEmptyState isDark={isDark} />
            </Card>
          </Grid>
          <Grid item xs={12} md={8}>
            <Card sx={{ p: 3 }}>
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
                По статусам
              </Typography>
              <ChartEmptyState isDark={isDark} />
            </Card>
          </Grid>
        </Grid>
      ) : (
        <Grid container spacing={3} sx={{ mb: 4 }}>
          {/* Pie chart — languages */}
          <Grid item xs={12} md={4}>
            <Card sx={{ p: 3, height: '100%' }}>
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
                Распределение по языкам
              </Typography>
              <ResponsiveContainer width="100%" height={300}>
                <PieChart>
                  <Pie
                    data={langData}
                    cx="50%"
                    cy="50%"
                    innerRadius={60}
                    outerRadius={100}
                    paddingAngle={3}
                    dataKey="value"
                    nameKey="name"
                    label={({ name, percent }) =>
                      `${name} ${(percent * 100).toFixed(0)}%`
                    }
                    labelLine={false}
                  >
                    {langData.map((entry, index) => (
                      <Cell key={entry.name} fill={langColors[index]} stroke="transparent" />
                    ))}
                  </Pie>
                  <Tooltip content={<CustomTooltip />} />
                  <Legend
                    formatter={(value) => (
                      <span style={{ color: isDark ? '#e6edf3' : '#111827', fontSize: '0.8rem' }}>
                        {value}
                      </span>
                    )}
                  />
                </PieChart>
              </ResponsiveContainer>
            </Card>
          </Grid>

          {/* Bar chart — statuses */}
          <Grid item xs={12} md={8}>
            <Card sx={{ p: 3, height: '100%' }}>
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
                Распределение по статусам
              </Typography>
              <ResponsiveContainer width="100%" height={300}>
                <BarChart
                  data={statusData}
                  margin={{ top: 8, right: 16, left: -8, bottom: 0 }}
                >
                  <CartesianGrid
                    strokeDasharray="3 3"
                    stroke={isDark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.07)'}
                    vertical={false}
                  />
                  <XAxis
                    dataKey="name"
                    tick={{ fill: isDark ? '#8b949e' : '#6b7280', fontSize: 12 }}
                    axisLine={false}
                    tickLine={false}
                  />
                  <YAxis
                    allowDecimals={false}
                    tick={{ fill: isDark ? '#8b949e' : '#6b7280', fontSize: 12 }}
                    axisLine={false}
                    tickLine={false}
                  />
                  <Tooltip content={<CustomTooltip />} cursor={{ fill: alpha(brand.indigo, 0.08) }} />
                  <Bar dataKey="value" name="Решений" radius={[6, 6, 0, 0]} maxBarSize={64}>
                    {statusData.map((entry, index) => (
                      <Cell key={entry.name} fill={statusColors[index] ?? brand.indigo} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </Card>
          </Grid>
        </Grid>
      )}

      {/* ── Bottom analytic block ────────────────────────────────── */}
      <Grid container spacing={3}>
        {/* Activity line chart (md=5) */}
        <Grid item xs={12} md={5}>
          <Paper sx={{ p: 3, height: '100%' }}>
            <ActivityLineChart submissions={submissions} />
          </Paper>
        </Grid>

        {/* Error distribution (md=4) */}
        <Grid item xs={12} md={4}>
          <Paper sx={{ p: 3, height: '100%' }}>
            <ErrorDistributionList submissions={submissions} />
          </Paper>
        </Grid>

        {/* Streak card (md=3) */}
        <Grid item xs={12} md={3}>
          <Paper sx={{ p: 3, height: '100%' }}>
            <StreakCard submissions={submissions} />
          </Paper>
        </Grid>
      </Grid>

      {/* Hint about current streak under KPI if active */}
      {!loading && currentStreak > 0 && (
        <Box sx={{ mt: 2 }}>
          <Typography variant="caption" color="text.secondary">
            Текущая серия: {currentStreak} дн. подряд
          </Typography>
        </Box>
      )}
    </Box>
  );
};

export default StatisticsPage;
