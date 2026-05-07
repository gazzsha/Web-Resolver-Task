import React from 'react';
import {
  Box,
  Typography,
  Chip,
  IconButton,
  Tooltip,
  Divider,
  Button,
  Alert,
  alpha,
  useTheme,
  Skeleton,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import TimerOutlinedIcon from '@mui/icons-material/TimerOutlined';
import MemoryOutlinedIcon from '@mui/icons-material/MemoryOutlined';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import type { Task } from '@/types';

const DIFFICULTY_RU: Record<string, string> = {
  Easy: 'Лёгкая',
  Medium: 'Средняя',
  Hard: 'Сложная',
};

const DIFFICULTY_COLORS: Record<string, string> = {
  Easy: '#22c55e',
  Medium: '#f59e0b',
  Hard: '#ef4444',
};

interface TaskPanelProps {
  task: Task | null;
  loading?: boolean;
  onBack: () => void;
  onResetCode: () => void;
}

const TaskPanel: React.FC<TaskPanelProps> = ({ task, loading, onBack, onResetCode }) => {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

  const borderColor = theme.palette.divider;
  const panelBg = isDark ? theme.palette.background.paper : '#f8f9fc';

  const diffColor = task?.difficulty ? DIFFICULTY_COLORS[task.difficulty] : undefined;

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        bgcolor: panelBg,
        borderRight: { md: `1px solid ${borderColor}` },
        borderBottom: { xs: `1px solid ${borderColor}`, md: 'none' },
      }}
    >
      {/* Panel header — back + title */}
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          px: 2,
          py: 1.5,
          borderBottom: `1px solid ${borderColor}`,
          flexShrink: 0,
        }}
      >
        <Tooltip title="Назад к списку задач">
          <IconButton
            size="small"
            onClick={onBack}
            sx={{
              color: 'text.secondary',
              flexShrink: 0,
              '&:hover': { color: 'primary.main' },
            }}
          >
            <ArrowBackIcon fontSize="small" />
          </IconButton>
        </Tooltip>

        {loading ? (
          <Skeleton width={180} height={22} />
        ) : (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 0, flex: 1 }}>
            <Typography
              variant="subtitle1"
              sx={{
                fontWeight: 700,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                flex: 1,
                minWidth: 0,
              }}
            >
              {task?.title ?? '—'}
            </Typography>
            {task?.difficulty && diffColor && (
              <Chip
                label={DIFFICULTY_RU[task.difficulty] ?? task.difficulty}
                size="small"
                sx={{
                  flexShrink: 0,
                  fontWeight: 700,
                  background: alpha(diffColor, isDark ? 0.22 : 0.12),
                  color: diffColor,
                  border: `1px solid ${alpha(diffColor, 0.35)}`,
                  fontSize: '0.72rem',
                }}
              />
            )}
          </Box>
        )}
      </Box>

      {/* Scrollable body */}
      <Box
        sx={{
          flex: 1,
          overflowY: 'auto',
          px: 2.5,
          py: 2,
          // Custom thin scrollbar
          '&::-webkit-scrollbar': { width: '5px' },
          '&::-webkit-scrollbar-track': { background: 'transparent' },
          '&::-webkit-scrollbar-thumb': {
            background: isDark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.14)',
            borderRadius: '3px',
          },
          '&::-webkit-scrollbar-thumb:hover': {
            background: isDark ? 'rgba(255,255,255,0.22)' : 'rgba(0,0,0,0.24)',
          },
        }}
      >
        {loading ? (
          <>
            <Skeleton width="90%" height={18} sx={{ mb: 1 }} />
            <Skeleton width="80%" height={18} sx={{ mb: 1 }} />
            <Skeleton width="70%" height={18} sx={{ mb: 1 }} />
            <Skeleton width="85%" height={18} sx={{ mb: 1 }} />
            <Skeleton width="60%" height={18} />
          </>
        ) : !task ? (
          <Alert severity="warning" sx={{ m: 1 }}>
            Задача не найдена
          </Alert>
        ) : (
          <>
            {/* Description section */}
            <Typography
              variant="overline"
              sx={{ color: 'text.disabled', letterSpacing: '0.08em', display: 'block', mb: 1 }}
            >
              Описание
            </Typography>
            <Typography
              variant="body2"
              sx={{
                whiteSpace: 'pre-wrap',
                lineHeight: 1.75,
                color: 'text.primary',
                fontFamily: 'inherit',
              }}
            >
              {task.description}
            </Typography>

            <Divider sx={{ my: 2.5 }} />

            {/* Constraints section */}
            <Typography
              variant="overline"
              sx={{ color: 'text.disabled', letterSpacing: '0.08em', display: 'block', mb: 1.5 }}
            >
              Ограничения
            </Typography>

            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.25 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                <Box
                  sx={{
                    width: 32,
                    height: 32,
                    borderRadius: '8px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: alpha('#f59e0b', isDark ? 0.2 : 0.12),
                    flexShrink: 0,
                  }}
                >
                  <TimerOutlinedIcon sx={{ fontSize: 16, color: '#f59e0b' }} />
                </Box>
                <Box>
                  <Typography variant="caption" sx={{ color: 'text.disabled', display: 'block', lineHeight: 1.2 }}>
                    Время выполнения
                  </Typography>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    10 секунд
                  </Typography>
                </Box>
              </Box>

              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                <Box
                  sx={{
                    width: 32,
                    height: 32,
                    borderRadius: '8px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: alpha('#0ea5e9', isDark ? 0.2 : 0.12),
                    flexShrink: 0,
                  }}
                >
                  <MemoryOutlinedIcon sx={{ fontSize: 16, color: '#0ea5e9' }} />
                </Box>
                <Box>
                  <Typography variant="caption" sx={{ color: 'text.disabled', display: 'block', lineHeight: 1.2 }}>
                    Память
                  </Typography>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    256 МБ
                  </Typography>
                </Box>
              </Box>

              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                <Box
                  sx={{
                    width: 32,
                    height: 32,
                    borderRadius: '8px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: alpha('#22c55e', isDark ? 0.2 : 0.12),
                    flexShrink: 0,
                  }}
                >
                  <CheckCircleOutlineIcon sx={{ fontSize: 16, color: '#22c55e' }} />
                </Box>
                <Box>
                  <Typography variant="caption" sx={{ color: 'text.disabled', display: 'block', lineHeight: 1.2 }}>
                    Требование
                  </Typography>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    Все тесты должны пройти
                  </Typography>
                </Box>
              </Box>
            </Box>

            <Divider sx={{ my: 2.5 }} />

            {/* Requirements list */}
            <Typography
              variant="overline"
              sx={{ color: 'text.disabled', letterSpacing: '0.08em', display: 'block', mb: 1.5 }}
            >
              Требования к коду
            </Typography>
            <Box component="ul" sx={{ m: 0, pl: 2.5, color: 'text.secondary' }}>
              {[
                'Все необходимые импорты',
                'Точка входа main',
                'Чтение данных из stdin',
                'Вывод результата в stdout',
                'Класс Solution (Java / Kotlin)',
              ].map((req) => (
                <Box
                  component="li"
                  key={req}
                  sx={{
                    fontSize: '0.8125rem',
                    lineHeight: 1.7,
                    '&::marker': { color: 'primary.main' },
                  }}
                >
                  {req}
                </Box>
              ))}
            </Box>
          </>
        )}
      </Box>

      {/* Sticky footer — reset button */}
      <Box
        sx={{
          flexShrink: 0,
          px: 2,
          py: 1.5,
          borderTop: `1px solid ${borderColor}`,
          bgcolor: panelBg,
        }}
      >
        <Button
          fullWidth
          variant="outlined"
          size="small"
          startIcon={<RestartAltIcon />}
          onClick={onResetCode}
          sx={{
            color: 'text.secondary',
            borderColor: borderColor,
            fontSize: '0.8125rem',
            '&:hover': {
              borderColor: 'primary.main',
              color: 'primary.main',
              background: alpha('#6366f1', isDark ? 0.1 : 0.05),
            },
          }}
        >
          Сбросить код до шаблона
        </Button>
      </Box>
    </Box>
  );
};

export default TaskPanel;
