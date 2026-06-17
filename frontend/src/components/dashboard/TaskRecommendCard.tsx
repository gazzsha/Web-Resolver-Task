import React from 'react';
import {
  Card,
  CardContent,
  Typography,
  Box,
  Chip,
  Button,
  alpha,
  useTheme,
} from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import type { Task } from '@/types';

const DIFFICULTY_RU: Record<string, string> = {
  Easy: 'Лёгкая',
  Medium: 'Средняя',
  Hard: 'Сложная',
};

const DIFFICULTY_COLORS: Record<string, { bg: string; text: string }> = {
  Easy: { bg: '#22c55e', text: '#16a34a' },
  Medium: { bg: '#f59e0b', text: '#b45309' },
  Hard: { bg: '#ef4444', text: '#b91c1c' },
};

interface TaskRecommendCardProps {
  task: Task;
  onNavigate: (id: string) => void;
}

const TaskRecommendCard: React.FC<TaskRecommendCardProps> = ({ task, onNavigate }) => {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';
  const diff = DIFFICULTY_COLORS[task.difficulty] ?? { bg: '#6b7280', text: '#374151' };

  return (
    <Card
      sx={{
        mb: 1.5,
        cursor: 'pointer',
        transition: 'transform 0.15s, box-shadow 0.15s',
        '&:hover': {
          transform: 'translateY(-2px)',
          boxShadow: isDark
            ? '0 6px 20px rgba(0,0,0,0.4)'
            : '0 6px 16px rgba(0,0,0,0.1)',
        },
      }}
      onClick={() => onNavigate(task.testId)}
    >
      <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 0.75, lineHeight: 1.3, fontSize: '0.92rem' }}>
          {task.title}
        </Typography>
        <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap', mb: 1.25, alignItems: 'center' }}>
          <Chip
            label={DIFFICULTY_RU[task.difficulty] ?? task.difficulty}
            size="small"
            sx={{
              fontWeight: 700,
              fontSize: '0.68rem',
              background: alpha(diff.bg, isDark ? 0.2 : 0.12),
              color: isDark ? diff.bg : diff.text,
              border: `1px solid ${alpha(diff.bg, 0.3)}`,
            }}
          />
          {task.category && (
            <Chip
              label={task.category}
              size="small"
              variant="outlined"
              sx={{ fontSize: '0.68rem', fontWeight: 500 }}
            />
          )}
        </Box>
        <Button
          variant="outlined"
          size="small"
          endIcon={<PlayArrowIcon fontSize="small" />}
          aria-label={`Решить задачу: ${task.title}`}
          onClick={(e) => {
            e.stopPropagation();
            onNavigate(task.testId);
          }}
          sx={{ fontSize: '0.75rem' }}
        >
          Решить
        </Button>
      </CardContent>
    </Card>
  );
};

export default TaskRecommendCard;
