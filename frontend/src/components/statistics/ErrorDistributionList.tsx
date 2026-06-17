import React, { useMemo } from 'react';
import { Box, Typography, LinearProgress, alpha, useTheme } from '@mui/material';
import type { SubmissionSummary } from '@/types';
import { StatusChip } from '@/components/submissions/chips';

interface ErrorDistributionListProps {
  submissions: SubmissionSummary[];
}

type NonSuccessStatus = 'FAILED' | 'ERROR' | 'PARTIAL_SUCCESS';

const NON_SUCCESS: NonSuccessStatus[] = ['FAILED', 'ERROR', 'PARTIAL_SUCCESS'];

const ErrorDistributionList: React.FC<ErrorDistributionListProps> = ({ submissions }) => {
  const theme = useTheme();

  const rows = useMemo(() => {
    const total = submissions.length;
    if (total === 0) return [];
    const counts: Record<NonSuccessStatus, number> = {
      FAILED: 0,
      ERROR: 0,
      PARTIAL_SUCCESS: 0,
    };
    for (const s of submissions) {
      if (NON_SUCCESS.includes(s.status as NonSuccessStatus)) {
        counts[s.status as NonSuccessStatus]++;
      }
    }
    return NON_SUCCESS.filter((s) => counts[s] > 0).map((s) => ({
      status: s as SubmissionSummary['status'],
      count: counts[s],
      percent: (counts[s] / total) * 100,
    }));
  }, [submissions]);

  return (
    <Box>
      <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1.5 }}>
        Распределение ошибок
      </Typography>
      {rows.length === 0 ? (
        <Box
          sx={{
            py: 2,
            px: 1.5,
            borderRadius: 2,
            background: alpha(theme.palette.success.main, 0.08),
            border: `1px solid ${alpha(theme.palette.success.main, 0.2)}`,
          }}
        >
          <Typography variant="body2" color="success.main" sx={{ fontWeight: 600 }}>
            Отлично! Нет ошибок.
          </Typography>
        </Box>
      ) : (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
          {rows.map(({ status, count, percent }) => (
            <Box key={status}>
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 0.5 }}>
                <StatusChip status={status} />
                <Typography variant="caption" color="text.secondary">
                  {count} попыток
                </Typography>
              </Box>
              <LinearProgress
                variant="determinate"
                value={percent}
                color={
                  status === 'PARTIAL_SUCCESS'
                    ? 'warning'
                    : 'error'
                }
                sx={{ borderRadius: 2, height: 6 }}
              />
            </Box>
          ))}
        </Box>
      )}
    </Box>
  );
};

export default ErrorDistributionList;
