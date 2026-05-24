import { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Typography,
  Paper,
  Button,
  Grid,
  Chip,
  CircularProgress,
  Card,
  CardContent,
  Divider,
  Link as MuiLink,
  Table,
  TableBody,
  TableCell,
  TableRow,
  alpha,
  useTheme,
  Alert,
} from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import TimerIcon from '@mui/icons-material/Timer';
import MemoryIcon from '@mui/icons-material/Memory';
import AssignmentIcon from '@mui/icons-material/Assignment';
import LightbulbOutlinedIcon from '@mui/icons-material/LightbulbOutlined';
import type { Task, SubmissionSummary } from '@/types';
import { taskService, meService } from '@/services/api';
import { brand } from '@/theme/theme';
import { StatusChip, LanguageChip } from '@/components/submissions/chips';
import { formatTimeAgo } from '@/utils/dateUtils';

const DIFFICULTY_RU: Record<string, string> = {
  Easy: 'Лёгкая',
  Medium: 'Средняя',
  Hard: 'Сложная',
};

const DIFFICULTY_STYLES: Record<string, { bg: string; color: string }> = {
  Easy: { bg: '#22c55e', color: '#15803d' },
  Medium: { bg: '#f59e0b', color: '#b45309' },
  Hard: { bg: '#ef4444', color: '#b91c1c' },
};

const TaskDetail = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';
  const [loading, setLoading] = useState(true);
  const [task, setTask] = useState<Task | null>(null);
  const [error, setError] = useState(false);
  const [taskSubmissions, setTaskSubmissions] = useState<SubmissionSummary[]>([]);

  useEffect(() => {
    const fetchTask = async () => {
      try {
        const data = await taskService.getById(id || '');
        setTask(data);
      } catch (err) {
        console.error('Failed to fetch task:', err);
        setError(true);
      } finally {
        setLoading(false);
      }
    };

    if (id) {
      fetchTask();
    }
  }, [id]);

  const loadSubmissions = useCallback(() => {
    if (!id) return;
    meService.getSubmissions().then((all) => {
      const filtered = all
        .filter((s) => s.taskId === id)
        .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
      setTaskSubmissions(filtered);
    }).catch(() => {
      // Non-critical
    });
  }, [id]);

  useEffect(() => {
    loadSubmissions();
  }, [loadSubmissions]);

  if (loading) {
    return (
      <Box display="flex" flexDirection="column" alignItems="center" gap={2} sx={{ minHeight: 400, justifyContent: 'center' }}>
        <CircularProgress aria-label="Загрузка задачи" />
        <Typography variant="body2" color="text.secondary">
          Загрузка задачи…
        </Typography>
      </Box>
    );
  }

  if (error) {
    return (
      <Box sx={{ maxWidth: 600, mx: 'auto', mt: 8, textAlign: 'center' }}>
        <Alert severity="error" sx={{ mb: 3 }}>
          Не удалось загрузить задачу
        </Alert>
        <Button variant="contained" onClick={() => navigate('/tasks')}>
          Вернуться к списку задач
        </Button>
      </Box>
    );
  }

  if (!task) {
    return (
      <Box sx={{ maxWidth: 600, mx: 'auto', mt: 8, textAlign: 'center' }}>
        <Alert severity="error" sx={{ mb: 3 }}>
          Задача не найдена или была удалена.
        </Alert>
        <Button variant="contained" onClick={() => navigate('/tasks')}>
          Вернуться к списку задач
        </Button>
      </Box>
    );
  }

  const diffStyle = DIFFICULTY_STYLES[task.difficulty] ?? { bg: '#6b7280', color: '#374151' };

  return (
    <Box sx={{ width: '100%' }}>
      {/* Back button */}
      <Button
        variant="text"
        startIcon={<ArrowBackIcon />}
        onClick={() => navigate('/tasks')}
        sx={{ mb: 2.5, color: 'text.secondary', fontWeight: 500 }}
      >
        Все задачи
      </Button>

      {/* Hero block */}
      <Box
        sx={{
          borderRadius: 3,
          mb: 4,
          p: { xs: 3, sm: 4 },
          background: isDark
            ? `linear-gradient(135deg, ${alpha(brand.indigo, 0.2)} 0%, ${alpha(brand.rose, 0.1)} 100%)`
            : `linear-gradient(135deg, ${alpha(brand.indigo, 0.07)} 0%, ${alpha(brand.rose, 0.04)} 100%)`,
          border: `1px solid ${isDark ? alpha(brand.indigo, 0.2) : alpha(brand.indigo, 0.12)}`,
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        {/* Decorative */}
        <Box
          sx={{
            position: 'absolute',
            right: -40,
            top: -40,
            width: 200,
            height: 200,
            borderRadius: '50%',
            background: `radial-gradient(circle, ${alpha(brand.indigo, 0.18)} 0%, transparent 70%)`,
            pointerEvents: 'none',
          }}
        />

        <Box sx={{ position: 'relative', zIndex: 1 }}>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1.5, mb: 2 }}>
            <Chip
              label={DIFFICULTY_RU[task.difficulty] ?? task.difficulty}
              size="medium"
              sx={{
                fontWeight: 700,
                background: alpha(diffStyle.bg, isDark ? 0.22 : 0.13),
                color: isDark ? diffStyle.bg : diffStyle.color,
                border: `1px solid ${alpha(diffStyle.bg, 0.3)}`,
              }}
            />
            {task.category && (
              <Chip
                label={task.category}
                size="small"
                variant="outlined"
                sx={{ fontWeight: 500 }}
              />
            )}
          </Box>

          <Typography
            variant="h3"
            component="h1"
            sx={{
              fontWeight: 700,
              mb: 2.5,
              fontSize: { xs: '1.5rem', sm: '2rem' },
            }}
          >
            {task.title}
          </Typography>

          <Button
            variant="contained"
            size="large"
            endIcon={<PlayArrowIcon />}
            onClick={() => navigate(`/submit/${id}`)}
          >
            Начать решение
          </Button>
        </Box>
      </Box>

      {/* Main content: description + sidebar */}
      <Grid container spacing={3}>
        {/* Description column */}
        <Grid item xs={12} md={8}>
          <Paper
            sx={{
              p: { xs: 2.5, sm: 3.5 },
              borderRadius: 3,
              border: `1px solid ${isDark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.07)'}`,
              boxShadow: 'none',
            }}
          >
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 2.5 }}>
              <Box
                sx={{
                  width: 36,
                  height: 36,
                  borderRadius: 1.5,
                  background: alpha(brand.indigo, isDark ? 0.2 : 0.1),
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: 'primary.main',
                }}
              >
                <AssignmentIcon fontSize="small" />
              </Box>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>
                Условие задачи
              </Typography>
            </Box>

            <Typography
              variant="body1"
              component="div"
              sx={{
                whiteSpace: 'pre-wrap',
                lineHeight: 1.75,
                color: 'text.primary',
                '& p': { mb: 1.5 },
              }}
            >
              {task.description}
            </Typography>
          </Paper>
        </Grid>

        {/* Sidebar */}
        <Grid item xs={12} md={4}>
          {/* Limits card */}
          <Card
            sx={{
              mb: 3,
              border: `1px solid ${isDark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.07)'}`,
              boxShadow: 'none',
            }}
          >
            <CardContent sx={{ p: 3 }}>
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
                Ограничения
              </Typography>

              {[
                {
                  icon: <TimerIcon fontSize="small" />,
                  label: 'Время выполнения',
                  value: '10 секунд',
                  color: brand.indigo,
                },
                {
                  icon: <MemoryIcon fontSize="small" />,
                  label: 'Память',
                  value: '256 МБ',
                  color: brand.rose,
                },
                {
                  icon: <AssignmentIcon fontSize="small" />,
                  label: 'Тест-кейсы',
                  value: 'Несколько тестов',
                  color: '#22c55e',
                },
              ].map((item, idx) => (
                <Box key={idx}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, py: 1.25 }}>
                    <Box
                      sx={{
                        width: 32,
                        height: 32,
                        borderRadius: 1.5,
                        background: alpha(item.color, isDark ? 0.18 : 0.1),
                        color: item.color,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexShrink: 0,
                      }}
                    >
                      {item.icon}
                    </Box>
                    <Box>
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.1 }}>
                        {item.label}
                      </Typography>
                      <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                        {item.value}
                      </Typography>
                    </Box>
                  </Box>
                  {idx < 2 && <Divider sx={{ opacity: 0.6 }} />}
                </Box>
              ))}
            </CardContent>
          </Card>

          {/* Tips card */}
          <Card
            sx={{
              border: `1px solid ${isDark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.07)'}`,
              boxShadow: 'none',
            }}
          >
            <CardContent sx={{ p: 3 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
                <LightbulbOutlinedIcon fontSize="small" sx={{ color: '#f59e0b' }} />
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  Советы
                </Typography>
              </Box>
              <Box component="ul" sx={{ pl: 2, m: 0, color: 'text.secondary' }}>
                {[
                  'Сначала разберите граничные случаи',
                  'Учитывайте временну́ю и пространственную сложность',
                  'Проверьте решение на примерах вручную',
                  'Программа читает данные из stdin, результат — в stdout',
                ].map((tip, i) => (
                  <Box
                    component="li"
                    key={i}
                    sx={{ mb: 0.75, fontSize: '0.85rem', lineHeight: 1.55 }}
                  >
                    {tip}
                  </Box>
                ))}
              </Box>
            </CardContent>
          </Card>

          {/* Mini-attempts table */}
          {taskSubmissions.length > 0 && (
            <Card
              sx={{
                mt: 3,
                border: `1px solid ${isDark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.07)'}`,
                boxShadow: 'none',
              }}
            >
              <CardContent sx={{ p: 2.5 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1.5 }}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                    Мои попытки ({taskSubmissions.length})
                  </Typography>
                  <MuiLink
                    component="button"
                    variant="body2"
                    sx={{ fontWeight: 600, cursor: 'pointer', border: 'none', background: 'none', color: 'primary.main' }}
                    onClick={() => navigate(`/submissions?task=${id}`)}
                  >
                    Все попытки
                  </MuiLink>
                </Box>
                <Table size="small" aria-label="Мои попытки по этой задаче">
                  <TableBody>
                    {taskSubmissions.slice(0, 5).map((item, idx) => (
                      <TableRow
                        key={item.id}
                        role="link"
                        tabIndex={0}
                        sx={{
                          cursor: 'pointer',
                          '&:last-child td': { border: 0 },
                          '&:hover': { bgcolor: isDark ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.02)' },
                        }}
                        onClick={() => navigate(`/results/${item.id}`)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault();
                            navigate(`/results/${item.id}`);
                          }
                        }}
                      >
                        <TableCell sx={{ pl: 0, width: 24 }}>
                          <Typography variant="caption" color="text.secondary">{idx + 1}</Typography>
                        </TableCell>
                        <TableCell sx={{ py: 0.75 }}>
                          <StatusChip status={item.status} />
                        </TableCell>
                        <TableCell sx={{ py: 0.75 }}>
                          <LanguageChip language={item.language} />
                        </TableCell>
                        <TableCell align="right" sx={{ py: 0.75 }}>
                          <Typography variant="caption" sx={{ fontFamily: '"JetBrains Mono", monospace' }}>
                            {item.passedTests != null && item.totalTests != null
                              ? `${item.passedTests}/${item.totalTests}`
                              : '—'}
                          </Typography>
                        </TableCell>
                        <TableCell align="right" sx={{ pr: 0, py: 0.75 }}>
                          <Typography variant="caption" color="text.secondary">
                            {formatTimeAgo(item.createdAt)}
                          </Typography>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          )}

          {/* CTA button */}
          <Button
            variant="contained"
            size="large"
            fullWidth
            endIcon={<PlayArrowIcon />}
            onClick={() => navigate(`/submit/${id}`)}
            sx={{ mt: 3 }}
          >
            Начать решение
          </Button>
        </Grid>
      </Grid>
    </Box>
  );
};

export default TaskDetail;
