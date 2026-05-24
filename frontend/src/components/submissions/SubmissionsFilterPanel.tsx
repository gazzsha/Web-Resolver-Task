import React from 'react';
import {
  Box,
  Typography,
  Button,
  ToggleButtonGroup,
  ToggleButton,
  RadioGroup,
  FormControlLabel,
  Radio,
  Divider,
  Autocomplete,
  TextField,
  alpha,
  useTheme,
} from '@mui/material';
import type { SubmissionSummary, Task } from '@/types';

export interface SubmissionsFilters {
  statuses: Array<SubmissionSummary['status']>;
  languages: Array<string>;
  period: '7' | '30' | 'all';
  taskId: string | null;
}

export const DEFAULT_FILTERS: SubmissionsFilters = {
  statuses: [],
  languages: [],
  period: 'all',
  taskId: null,
};

function isFiltersDefault(filters: SubmissionsFilters): boolean {
  return (
    filters.statuses.length === 0 &&
    filters.languages.length === 0 &&
    filters.period === 'all' &&
    filters.taskId === null
  );
}

interface SubmissionsFilterPanelProps {
  filters: SubmissionsFilters;
  onChange: (next: SubmissionsFilters) => void;
  tasks: Task[];
}

const STATUS_OPTIONS: Array<{ value: SubmissionSummary['status']; label: string }> = [
  { value: 'SUCCESS', label: 'Принято' },
  { value: 'PARTIAL_SUCCESS', label: 'Частично' },
  { value: 'FAILED', label: 'Не принято' },
  { value: 'ERROR', label: 'Ошибка' },
];

const LANG_OPTIONS = ['java', 'kotlin', 'python'];

const SubmissionsFilterPanel: React.FC<SubmissionsFilterPanelProps> = ({
  filters,
  onChange,
  tasks,
}) => {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';
  const dirty = !isFiltersDefault(filters);

  const handleStatusChange = (_: React.MouseEvent, newStatuses: Array<SubmissionSummary['status']>) => {
    onChange({ ...filters, statuses: newStatuses });
  };

  const handleLangChange = (_: React.MouseEvent, newLangs: string[]) => {
    onChange({ ...filters, languages: newLangs });
  };

  const handlePeriodChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange({ ...filters, period: e.target.value as SubmissionsFilters['period'] });
  };

  const selectedTask = tasks.find((t) => t.testId === filters.taskId) ?? null;

  return (
    <Box
      sx={{
        p: 2,
        borderRadius: 2,
        border: `1px solid ${theme.palette.divider}`,
        bgcolor: theme.palette.background.paper,
      }}
    >
      {/* Header */}
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
          Фильтры
        </Typography>
        {dirty && (
          <Button
            size="small"
            variant="text"
            onClick={() => onChange(DEFAULT_FILTERS)}
            sx={{ fontSize: '0.75rem' }}
          >
            Сбросить
          </Button>
        )}
      </Box>

      {/* Status */}
      <Typography variant="caption" sx={{ fontWeight: 700, color: 'text.secondary', display: 'block', mb: 0.75 }}>
        СТАТУС
      </Typography>
      <ToggleButtonGroup
        value={filters.statuses}
        onChange={handleStatusChange}
        size="small"
        sx={{ flexWrap: 'wrap', gap: 0.5, mb: 2 }}
      >
        {STATUS_OPTIONS.map(({ value, label }) => (
          <ToggleButton
            key={value}
            value={value}
            sx={{
              fontSize: '0.72rem',
              py: 0.4,
              px: 1,
              borderRadius: '6px !important',
              border: `1px solid ${isDark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.12)'} !important`,
              '&.Mui-selected': {
                bgcolor: alpha(theme.palette.primary.main, isDark ? 0.25 : 0.12),
                color: 'primary.main',
                borderColor: `${theme.palette.primary.main} !important`,
              },
            }}
          >
            {label}
          </ToggleButton>
        ))}
      </ToggleButtonGroup>

      <Divider sx={{ mb: 2 }} />

      {/* Language */}
      <Typography variant="caption" sx={{ fontWeight: 700, color: 'text.secondary', display: 'block', mb: 0.75 }}>
        ЯЗЫК
      </Typography>
      <ToggleButtonGroup
        value={filters.languages}
        onChange={handleLangChange}
        size="small"
        sx={{ gap: 0.5, mb: 2 }}
      >
        {LANG_OPTIONS.map((lang) => (
          <ToggleButton
            key={lang}
            value={lang}
            sx={{
              fontSize: '0.72rem',
              py: 0.4,
              px: 1,
              fontFamily: '"JetBrains Mono", monospace',
              borderRadius: '6px !important',
              border: `1px solid ${isDark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.12)'} !important`,
              '&.Mui-selected': {
                bgcolor: alpha(theme.palette.primary.main, isDark ? 0.25 : 0.12),
                color: 'primary.main',
                borderColor: `${theme.palette.primary.main} !important`,
              },
            }}
          >
            {lang}
          </ToggleButton>
        ))}
      </ToggleButtonGroup>

      <Divider sx={{ mb: 2 }} />

      {/* Period */}
      <Typography variant="caption" sx={{ fontWeight: 700, color: 'text.secondary', display: 'block', mb: 0.5 }}>
        ПЕРИОД
      </Typography>
      <RadioGroup value={filters.period} onChange={handlePeriodChange} sx={{ mb: 2 }}>
        {[
          { value: '7', label: 'За 7 дней' },
          { value: '30', label: 'За 30 дней' },
          { value: 'all', label: 'Всё время' },
        ].map(({ value, label }) => (
          <FormControlLabel
            key={value}
            value={value}
            control={<Radio size="small" />}
            label={<Typography variant="body2">{label}</Typography>}
            sx={{ m: 0, '& .MuiFormControlLabel-label': { ml: 0.5 } }}
          />
        ))}
      </RadioGroup>

      <Divider sx={{ mb: 2 }} />

      {/* Task */}
      <Typography variant="caption" sx={{ fontWeight: 700, color: 'text.secondary', display: 'block', mb: 0.75 }}>
        ЗАДАЧА
      </Typography>
      <Autocomplete
        options={tasks}
        getOptionLabel={(t) => t.title}
        value={selectedTask}
        onChange={(_e, newVal) => onChange({ ...filters, taskId: newVal?.testId ?? null })}
        size="small"
        freeSolo={false}
        renderInput={(params) => (
          <TextField
            {...params}
            placeholder="Все задачи"
            variant="outlined"
            size="small"
          />
        )}
        isOptionEqualToValue={(opt, val) => opt.testId === val.testId}
      />
    </Box>
  );
};

export default SubmissionsFilterPanel;
