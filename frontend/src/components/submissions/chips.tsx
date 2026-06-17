import React from 'react';
import { Chip, alpha, useTheme } from '@mui/material';
import type { SubmissionSummary } from '@/types';

export const LANG_COLORS: Record<string, string> = {
  java: '#f89820',
  kotlin: '#7f52ff',
  python: '#3572a5',
};

export const STATUS_MAP: Record<
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

interface StatusChipProps {
  status: SubmissionSummary['status'];
}

export const StatusChip: React.FC<StatusChipProps> = ({ status }) => {
  const { label, color } = STATUS_MAP[status] ?? { label: status, color: 'default' as const };
  return (
    <Chip
      label={label}
      color={color}
      size="small"
      sx={{ fontWeight: 700, minWidth: 90 }}
    />
  );
};

interface LanguageChipProps {
  language: string;
}

export const LanguageChip: React.FC<LanguageChipProps> = ({ language }) => {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';
  const color = LANG_COLORS[language] ?? '#6366f1';
  return (
    <Chip
      label={language}
      size="small"
      sx={{
        fontFamily: '"JetBrains Mono", monospace',
        fontWeight: 600,
        fontSize: '0.72rem',
        background: alpha(color, isDark ? 0.2 : 0.12),
        color,
        border: `1px solid ${alpha(color, 0.35)}`,
      }}
    />
  );
};
