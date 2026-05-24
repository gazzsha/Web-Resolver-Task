import React, { useMemo } from 'react';
import { Box, Typography, useTheme } from '@mui/material';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import type { SubmissionSummary } from '@/types';
import { buildActivityData } from '@/utils/submissionStats';
import { brand } from '@/theme/theme';

interface ActivityLineChartProps {
  submissions: SubmissionSummary[];
}

const ActivityLineChart: React.FC<ActivityLineChartProps> = ({ submissions }) => {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

  const data = useMemo(() => buildActivityData(submissions, 30), [submissions]);

  // Show only every 5th label to avoid crowding
  const tickFormatter = (_: string, index: number) => {
    if (index % 5 !== 0) return '';
    const d = new Date(data[index]?.date ?? '');
    if (isNaN(d.getTime())) return '';
    return `${d.getDate()}.${String(d.getMonth() + 1).padStart(2, '0')}`;
  };

  return (
    <Box
      role="img"
      aria-label="График активности за последние 30 дней"
      sx={{ width: '100%' }}
    >
      <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1.5 }}>
        Активность (30 дней)
      </Typography>
      <ResponsiveContainer width="100%" height={200}>
        <LineChart data={data} margin={{ top: 4, right: 8, left: -24, bottom: 0 }}>
          <CartesianGrid
            strokeDasharray="4 4"
            stroke={isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)'}
            vertical={false}
          />
          <XAxis
            dataKey="date"
            tickFormatter={tickFormatter}
            tick={{ fill: isDark ? '#8b949e' : '#6b7280', fontSize: 11 }}
            axisLine={false}
            tickLine={false}
          />
          <YAxis
            allowDecimals={false}
            tick={{ fill: isDark ? '#8b949e' : '#6b7280', fontSize: 11 }}
            axisLine={false}
            tickLine={false}
          />
          <Tooltip
            formatter={(value: number) => [value, 'Попыток']}
            labelFormatter={(label: string) => {
              const d = new Date(label);
              return isNaN(d.getTime()) ? label : `${d.getDate()}.${String(d.getMonth() + 1).padStart(2, '0')}.${d.getFullYear()}`;
            }}
            contentStyle={{
              background: isDark ? '#1a1d2e' : '#fff',
              border: `1px solid ${isDark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.1)'}`,
              borderRadius: 8,
              fontSize: '0.78rem',
            }}
          />
          <Line
            type="monotone"
            dataKey="value"
            stroke={brand.indigo}
            strokeWidth={2}
            dot={false}
            activeDot={{ r: 4, fill: brand.indigo }}
          />
        </LineChart>
      </ResponsiveContainer>
    </Box>
  );
};

export default ActivityLineChart;
