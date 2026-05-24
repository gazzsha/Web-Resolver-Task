import React, { useMemo } from 'react';
import {
  Box,
  Typography,
  LinearProgress,
  Chip,
  Divider,
  alpha,
  useTheme,
} from '@mui/material';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import type { Task, SubmissionSummary } from '@/types';

interface TaskProgressPanelProps {
  tasks: Task[];
  solvedTaskIds: Set<string>;
  submissions: SubmissionSummary[];
}

const TaskProgressPanel: React.FC<TaskProgressPanelProps> = ({ tasks, solvedTaskIds }) => {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

  const solved = solvedTaskIds.size;
  const total = tasks.length;
  const progressPct = total > 0 ? Math.round((solved / total) * 100) : 0;

  const categoryData = useMemo(() => {
    const counts: Record<string, number> = {};
    for (const t of tasks) {
      const cat = t.category ?? 'Прочее';
      counts[cat] = (counts[cat] ?? 0) + 1;
    }
    return Object.entries(counts)
      .map(([name, value]) => ({ name, value }))
      .sort((a, b) => b.value - a.value)
      .slice(0, 6);
  }, [tasks]);

  const easyCount = tasks.filter((t) => t.difficulty === 'Easy').length;
  const mediumCount = tasks.filter((t) => t.difficulty === 'Medium').length;
  const hardCount = tasks.filter((t) => t.difficulty === 'Hard').length;

  return (
    <Box
      sx={{
        width: '100%',
        flexShrink: 0,
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
        p: 2,
        '&::-webkit-scrollbar': { width: '3px' },
        '&::-webkit-scrollbar-track': { background: 'transparent' },
        '&::-webkit-scrollbar-thumb': {
          background: isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.1)',
          borderRadius: '2px',
        },
      }}
    >
      {/* Header */}
      <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
        Прогресс
      </Typography>

      {/* Progress bar */}
      <Box>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.75 }}>
          <Typography variant="body2" color="text.secondary" sx={{ fontSize: '0.8rem' }}>
            {solved} / {total} задач решено
          </Typography>
          <Typography variant="body2" sx={{ fontWeight: 700, fontSize: '0.8rem', fontFamily: '"JetBrains Mono", monospace' }}>
            {progressPct}%
          </Typography>
        </Box>
        <LinearProgress
          variant="determinate"
          value={progressPct}
          color={progressPct >= 75 ? 'success' : progressPct >= 40 ? 'warning' : 'primary'}
          sx={{ borderRadius: 2, height: 8 }}
        />
      </Box>

      <Divider />

      {/* Category distribution */}
      {categoryData.length > 0 && (
        <Box>
          <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
            Категории
          </Typography>
          <Box
            role="img"
            aria-label="Распределение задач по категориям"
          >
            <ResponsiveContainer width="100%" height={160}>
              <BarChart
                data={categoryData}
                layout="vertical"
                margin={{ top: 0, right: 8, left: 0, bottom: 0 }}
              >
                <XAxis
                  type="number"
                  allowDecimals={false}
                  tick={{ fill: isDark ? '#8b949e' : '#6b7280', fontSize: 10 }}
                  axisLine={false}
                  tickLine={false}
                />
                <YAxis
                  type="category"
                  dataKey="name"
                  width={72}
                  tick={{ fill: isDark ? '#8b949e' : '#6b7280', fontSize: 10 }}
                  axisLine={false}
                  tickLine={false}
                />
                <Tooltip
                  formatter={(value: number) => [value, 'Задач']}
                  contentStyle={{
                    background: isDark ? '#1a1d2e' : '#fff',
                    border: `1px solid ${isDark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.1)'}`,
                    borderRadius: 8,
                    fontSize: '0.75rem',
                  }}
                />
                <Bar dataKey="value" fill="#6366f1" radius={[0, 3, 3, 0]} maxBarSize={14} />
              </BarChart>
            </ResponsiveContainer>
          </Box>
        </Box>
      )}

      <Divider />

      {/* Difficulty chips */}
      <Box>
        <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
          Сложность
        </Typography>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.75 }}>
          <Chip
            label={`Лёгких: ${easyCount}`}
            size="small"
            sx={{
              fontWeight: 600,
              background: alpha('#22c55e', isDark ? 0.2 : 0.12),
              color: isDark ? '#22c55e' : '#16a34a',
              border: `1px solid ${alpha('#22c55e', 0.3)}`,
              alignSelf: 'flex-start',
            }}
          />
          <Chip
            label={`Средних: ${mediumCount}`}
            size="small"
            sx={{
              fontWeight: 600,
              background: alpha('#f59e0b', isDark ? 0.2 : 0.12),
              color: isDark ? '#f59e0b' : '#b45309',
              border: `1px solid ${alpha('#f59e0b', 0.3)}`,
              alignSelf: 'flex-start',
            }}
          />
          <Chip
            label={`Сложных: ${hardCount}`}
            size="small"
            sx={{
              fontWeight: 600,
              background: alpha('#ef4444', isDark ? 0.2 : 0.12),
              color: isDark ? '#ef4444' : '#b91c1c',
              border: `1px solid ${alpha('#ef4444', 0.3)}`,
              alignSelf: 'flex-start',
            }}
          />
        </Box>
      </Box>
    </Box>
  );
};

export default TaskProgressPanel;
