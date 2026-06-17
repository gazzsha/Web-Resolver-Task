import React, { useMemo } from 'react';
import {
  Box,
  Typography,
  Card,
  CardContent,
  Divider,
  Link as MuiLink,
  alpha,
  useTheme,
} from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import {
  PieChart,
  Pie,
  Cell,
  BarChart,
  Bar,
  XAxis,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import type { SubmissionSummary } from '@/types';
import { buildLangData, buildWeeklyData } from '@/utils/submissionStats';

const LANG_COLORS: Record<string, string> = {
  java: '#f89820',
  kotlin: '#7f52ff',
  python: '#3572a5',
};

interface MiniCardProps {
  label: string;
  value: string | number;
  color?: string;
}

const MiniCard: React.FC<MiniCardProps> = ({ label, value, color }) => {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';
  const accent = color ?? theme.palette.text.primary;

  return (
    <Box
      sx={{
        px: 2,
        py: 1.25,
        borderRadius: 1.5,
        bgcolor: isDark ? alpha('#ffffff', 0.03) : alpha('#000000', 0.02),
        border: `1px solid ${theme.palette.divider}`,
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
      }}
    >
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography
        variant="subtitle2"
        sx={{ fontWeight: 700, fontFamily: '"JetBrains Mono", monospace', color: accent }}
      >
        {value}
      </Typography>
    </Box>
  );
};

interface SubmissionsMiniStatsProps {
  submissions: SubmissionSummary[];
}

const SubmissionsMiniStats: React.FC<SubmissionsMiniStatsProps> = ({ submissions }) => {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

  const langData = useMemo(() => buildLangData(submissions), [submissions]);
  const weeklyData = useMemo(() => buildWeeklyData(submissions), [submissions]);

  const total = submissions.length;
  const successCount = submissions.filter((s) => s.status === 'SUCCESS').length;
  const successRate = total > 0 ? `${((successCount / total) * 100).toFixed(0)}%` : '—';

  const langColors = langData.map((d) => LANG_COLORS[d.name] ?? '#6366f1');

  if (total === 0) {
    return (
      <Card sx={{ p: 2 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1.5 }}>
          Статистика
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Нет данных
        </Typography>
      </Card>
    );
  }

  return (
    <Card>
      <CardContent sx={{ p: 2 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1.5 }}>
          Статистика
        </Typography>

        {/* Mini cards */}
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.75, mb: 1.5 }}>
          <MiniCard label="Всего попыток" value={total} />
          <MiniCard label="Успешных" value={successCount} color={theme.palette.success.main} />
          <MiniCard label="Успешность" value={successRate} color={theme.palette.success.main} />
        </Box>

        {/* Pie chart by language */}
        {langData.length > 0 && (
          <Box
            role="img"
            aria-label="Круговая диаграмма по языкам программирования"
            sx={{ mb: 1 }}
          >
            <ResponsiveContainer width="100%" height={120}>
              <PieChart>
                <Pie
                  data={langData}
                  cx="50%"
                  cy="50%"
                  innerRadius={28}
                  outerRadius={48}
                  paddingAngle={3}
                  dataKey="value"
                  nameKey="name"
                >
                  {langData.map((entry, index) => (
                    <Cell key={entry.name} fill={langColors[index]} stroke="transparent" />
                  ))}
                </Pie>
                <Tooltip
                  formatter={(value: number, name: string) => [value, name]}
                  contentStyle={{
                    background: isDark ? '#1a1d2e' : '#fff',
                    border: `1px solid ${isDark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.1)'}`,
                    borderRadius: 8,
                    fontSize: '0.75rem',
                  }}
                />
              </PieChart>
            </ResponsiveContainer>
          </Box>
        )}

        {/* Mini bar chart — last 7 days */}
        <Box
          role="img"
          aria-label="Столбчатая диаграмма активности за последние 7 дней"
          sx={{ mb: 1.5 }}
        >
          <ResponsiveContainer width="100%" height={80}>
            <BarChart data={weeklyData} margin={{ top: 0, right: 0, left: -32, bottom: 0 }}>
              <XAxis
                dataKey="day"
                tick={{ fill: isDark ? '#8b949e' : '#6b7280', fontSize: 10 }}
                axisLine={false}
                tickLine={false}
              />
              <Tooltip
                formatter={(value: number) => [value, 'Попыток']}
                contentStyle={{
                  background: isDark ? '#1a1d2e' : '#fff',
                  border: `1px solid ${isDark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.1)'}`,
                  borderRadius: 8,
                  fontSize: '0.75rem',
                }}
              />
              <Bar dataKey="value" fill="#6366f1" radius={[3, 3, 0, 0]} maxBarSize={20} />
            </BarChart>
          </ResponsiveContainer>
        </Box>

        <Divider sx={{ mb: 1.5 }} />

        <MuiLink
          component={RouterLink}
          to="/statistics"
          variant="body2"
          sx={{ fontWeight: 600 }}
        >
          Полная статистика →
        </MuiLink>
      </CardContent>
    </Card>
  );
};

export default SubmissionsMiniStats;
