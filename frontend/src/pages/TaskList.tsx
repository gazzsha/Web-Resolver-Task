import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Grid,
  Card,
  CardContent,
  Typography,
  Box,
  Chip,
  Button,
  TextField,
  InputAdornment,
  Alert,
  ToggleButton,
  ToggleButtonGroup,
  alpha,
  useTheme,
  Skeleton,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import FilterListIcon from '@mui/icons-material/FilterList';
import { taskService } from '@/services/api';
import type { Task } from '@/types';
import { brand } from '@/theme/theme';

type Difficulty = 'all' | 'Easy' | 'Medium' | 'Hard';

const DIFFICULTY_RU: Record<string, string> = {
  Easy: 'Лёгкая',
  Medium: 'Средняя',
  Hard: 'Сложная',
};

const DIFFICULTY_COLORS: Record<string, { bg: string; text: string; chip: 'success' | 'warning' | 'error' }> = {
  Easy: { bg: '#22c55e', text: '#16a34a', chip: 'success' },
  Medium: { bg: '#f59e0b', text: '#b45309', chip: 'warning' },
  Hard: { bg: '#ef4444', text: '#b91c1c', chip: 'error' },
};

const DEMO_TASKS: Task[] = [
  {
    testId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    title: 'Сумма двух чисел',
    description: 'Дан массив целых чисел. Найдите два числа, сумма которых равна заданному target, и верните их индексы.',
    difficulty: 'Easy',
    category: 'Массивы',
  },
  {
    testId: 'b2c3d4e5-f6a7-8901-bcde-f12345678901',
    title: 'Правильные скобки',
    description: 'Определите, является ли строка, содержащая только скобки, корректной (каждая открывающая скобка закрыта в правильном порядке).',
    difficulty: 'Easy',
    category: 'Строки',
  },
  {
    testId: 'c3d4e5f6-a7b8-9012-cdef-123456789012',
    title: 'Слияние двух отсортированных списков',
    description: 'Слейте два отсортированных связных списка в один отсортированный список.',
    difficulty: 'Easy',
    category: 'Связные списки',
  },
  {
    testId: 'e5f6a7b8-c9d0-1234-ef01-345678901234',
    title: 'Палиндром',
    description: 'Проверьте, является ли строка палиндромом после удаления всех символов кроме букв и цифр.',
    difficulty: 'Easy',
    category: 'Строки',
  },
  {
    testId: 'c5d6e7f8-a9b0-1234-8901-345678901234',
    title: 'Медиана двух отсортированных массивов',
    description: 'Найдите медиану двух отсортированных массивов за O(log(m+n)). Это сложная задача на двоичный поиск.',
    difficulty: 'Hard',
    category: 'Массивы',
  },
];

const TaskCardSkeleton = () => (
  <Card sx={{ height: '100%' }}>
    <CardContent sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
        <Skeleton variant="text" width="60%" height={28} />
        <Skeleton variant="rectangular" width={64} height={22} sx={{ borderRadius: 1 }} />
      </Box>
      <Skeleton variant="text" width="100%" />
      <Skeleton variant="text" width="80%" sx={{ mb: 2 }} />
      <Skeleton variant="rectangular" width={80} height={22} sx={{ borderRadius: 1, mb: 2 }} />
      <Skeleton variant="rectangular" height={38} sx={{ borderRadius: 1.5 }} />
    </CardContent>
  </Card>
);

const TaskList = () => {
  const navigate = useNavigate();
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [difficultyFilter, setDifficultyFilter] = useState<Difficulty>('all');
  const [tasks, setTasks] = useState<Task[]>([]);

  useEffect(() => {
    const fetchTasks = async () => {
      try {
        setLoading(true);
        const data = await taskService.getAll();
        setTasks(data);
        setError(null);
      } catch (err: any) {
        setError('Не удалось загрузить задачи с сервера. Показаны демо-данные.');
        setTasks(DEMO_TASKS);
      } finally {
        setLoading(false);
      }
    };
    fetchTasks();
  }, []);

  const filteredTasks = tasks.filter((task) => {
    const matchSearch =
      task.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
      task.description.toLowerCase().includes(searchTerm.toLowerCase());
    const matchDiff = difficultyFilter === 'all' || task.difficulty === difficultyFilter;
    return matchSearch && matchDiff;
  });

  const difficultyCount = (d: Difficulty) =>
    d === 'all' ? tasks.length : tasks.filter((t) => t.difficulty === d).length;

  return (
    <Box sx={{ width: '100%', maxWidth: 1400 }}>
      {/* Page header */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h4" component="h1" sx={{ fontWeight: 700, mb: 0.5 }}>
          Задачи
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Выберите задачу и напишите решение — AI проверит и даст обратную связь
        </Typography>
      </Box>

      {error && (
        <Alert severity="warning" sx={{ mb: 3, borderRadius: 2 }}>
          {error}
        </Alert>
      )}

      {/* Toolbar: search + filter */}
      <Box
        sx={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 2,
          alignItems: 'center',
          mb: 4,
        }}
      >
        <TextField
          placeholder="Поиск по названию или описанию..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          size="small"
          sx={{
            minWidth: 260,
            flexGrow: 1,
            maxWidth: 480,
            '& .MuiOutlinedInput-root': { borderRadius: 2.5 },
          }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" sx={{ color: 'text.disabled' }} />
              </InputAdornment>
            ),
          }}
        />

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <FilterListIcon fontSize="small" sx={{ color: 'text.secondary' }} />
          <ToggleButtonGroup
            value={difficultyFilter}
            exclusive
            onChange={(_, v) => v !== null && setDifficultyFilter(v)}
            size="small"
            sx={{
              '& .MuiToggleButton-root': {
                borderRadius: '8px !important',
                px: 1.5,
                py: 0.5,
                fontSize: '0.78rem',
                fontWeight: 600,
                border: `1px solid ${isDark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.12)'} !important`,
                mx: '2px',
                '&.Mui-selected': {
                  background: alpha(brand.indigo, isDark ? 0.2 : 0.1),
                  color: 'primary.main',
                  borderColor: `${alpha(brand.indigo, 0.4)} !important`,
                },
              },
            }}
          >
            <ToggleButton value="all">Все ({difficultyCount('all')})</ToggleButton>
            <ToggleButton value="Easy">Лёгкие ({difficultyCount('Easy')})</ToggleButton>
            <ToggleButton value="Medium">Средние ({difficultyCount('Medium')})</ToggleButton>
            <ToggleButton value="Hard">Сложные ({difficultyCount('Hard')})</ToggleButton>
          </ToggleButtonGroup>
        </Box>
      </Box>

      {/* Task grid */}
      {loading ? (
        <Grid container spacing={3}>
          {[0, 1, 2, 3, 4, 5].map((i) => (
            <Grid item xs={12} sm={6} lg={4} key={i}>
              <TaskCardSkeleton />
            </Grid>
          ))}
        </Grid>
      ) : filteredTasks.length === 0 && tasks.length === 0 ? (
        <Box sx={{ textAlign: 'center', py: 10, color: 'text.secondary' }}>
          <Typography variant="h6" sx={{ mb: 1 }}>
            Задачи не найдены
          </Typography>
          <Typography variant="body2">
            Список задач пуст. Обратитесь к преподавателю.
          </Typography>
        </Box>
      ) : filteredTasks.length === 0 ? (
        <Box sx={{ textAlign: 'center', py: 10, color: 'text.secondary' }}>
          <Typography variant="h6" sx={{ mb: 1 }}>
            По вашему запросу ничего не найдено
          </Typography>
          <Typography variant="body2" sx={{ mb: 3 }}>
            Попробуйте изменить поисковый запрос или фильтр по сложности
          </Typography>
          <Button
            variant="outlined"
            onClick={() => { setSearchTerm(''); setDifficultyFilter('all'); }}
          >
            Сбросить фильтры
          </Button>
        </Box>
      ) : (
        <Grid container spacing={3}>
          {filteredTasks.map((task) => {
            const diff = DIFFICULTY_COLORS[task.difficulty] ?? { bg: '#6b7280', text: '#374151', chip: 'default' as const };
            return (
              <Grid item xs={12} sm={6} lg={4} key={task.testId}>
                <Card
                  sx={{
                    height: '100%',
                    display: 'flex',
                    flexDirection: 'column',
                    cursor: 'pointer',
                    transition: 'transform 0.18s, box-shadow 0.18s',
                    '&:hover': {
                      transform: 'translateY(-4px)',
                      boxShadow: isDark
                        ? `0 12px 36px rgba(0,0,0,0.5), 0 0 0 1px ${alpha(brand.indigo, 0.2)}`
                        : `0 12px 28px rgba(0,0,0,0.12), 0 0 0 1px ${alpha(brand.indigo, 0.1)}`,
                    },
                  }}
                  onClick={() => navigate(`/tasks/${task.testId}`)}
                >
                  <CardContent sx={{ p: 3, flexGrow: 1, display: 'flex', flexDirection: 'column' }}>
                    {/* Header row */}
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 1, mb: 1.5 }}>
                      <Typography
                        variant="h6"
                        sx={{
                          fontWeight: 700,
                          fontSize: '1rem',
                          lineHeight: 1.35,
                          flex: 1,
                        }}
                      >
                        {task.title}
                      </Typography>
                      <Chip
                        label={DIFFICULTY_RU[task.difficulty] ?? task.difficulty}
                        size="small"
                        sx={{
                          fontWeight: 700,
                          flexShrink: 0,
                          background: alpha(diff.bg, isDark ? 0.2 : 0.12),
                          color: isDark ? diff.bg : diff.text,
                          borderColor: alpha(diff.bg, 0.3),
                          border: '1px solid',
                        }}
                      />
                    </Box>

                    {/* Description truncated to 2 lines */}
                    <Typography
                      variant="body2"
                      color="text.secondary"
                      sx={{
                        flexGrow: 1,
                        mb: 2,
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                        lineHeight: 1.55,
                      }}
                    >
                      {task.description}
                    </Typography>

                    {/* Category chip */}
                    {task.category && (
                      <Chip
                        label={task.category}
                        size="small"
                        variant="outlined"
                        sx={{ alignSelf: 'flex-start', mb: 2.5, fontWeight: 500, fontSize: '0.72rem' }}
                      />
                    )}

                    {/* CTA */}
                    <Button
                      variant="contained"
                      fullWidth
                      endIcon={<PlayArrowIcon />}
                      onClick={(e) => {
                        e.stopPropagation();
                        navigate(`/tasks/${task.testId}`);
                      }}
                      sx={{ mt: 'auto' }}
                    >
                      Решить задачу
                    </Button>
                  </CardContent>
                </Card>
              </Grid>
            );
          })}
        </Grid>
      )}
    </Box>
  );
};

export default TaskList;
