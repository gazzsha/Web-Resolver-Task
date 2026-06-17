import { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Grid,
  Card,
  CardContent,
  Typography,
  Box,
  Chip,
  Button,
  Alert,
  alpha,
  useTheme,
  Skeleton,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Autocomplete,
  Drawer,
  IconButton,
  InputAdornment,
} from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import SearchIcon from '@mui/icons-material/Search';
import BarChartIcon from '@mui/icons-material/BarChart';
import CloseIcon from '@mui/icons-material/Close';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import { taskService, meService } from '@/services/api';
import type { Task, SubmissionSummary } from '@/types';
import { brand } from '@/theme/theme';
import TaskProgressPanel from '@/components/tasks/TaskProgressPanel';

type Difficulty = 'all' | 'Easy' | 'Medium' | 'Hard';
type SolvedStatus = 'all' | 'solved' | 'unsolved';

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

const DEMO_TASKS: Task[] = [
  { testId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', title: 'Сумма двух чисел', description: 'Дан массив целых чисел. Найдите два числа, сумма которых равна заданному target, и верните их индексы.', difficulty: 'Easy', category: 'Массивы' },
  { testId: 'b2c3d4e5-f6a7-8901-bcde-f12345678901', title: 'Правильные скобки', description: 'Определите, является ли строка, содержащая только скобки, корректной (каждая открывающая скобка закрыта в правильном порядке).', difficulty: 'Easy', category: 'Строки' },
  { testId: 'c3d4e5f6-a7b8-9012-cdef-123456789012', title: 'Слияние двух отсортированных списков', description: 'Слейте два отсортированных связных списка в один отсортированный список.', difficulty: 'Easy', category: 'Связные списки' },
  { testId: 'e5f6a7b8-c9d0-1234-ef01-345678901234', title: 'Палиндром', description: 'Проверьте, является ли строка палиндромом после удаления всех символов кроме букв и цифр.', difficulty: 'Easy', category: 'Строки' },
  { testId: 'c5d6e7f8-a9b0-1234-8901-345678901234', title: 'Медиана двух отсортированных массивов', description: 'Найдите медиану двух отсортированных массивов за O(log(m+n)). Это сложная задача на двоичный поиск.', difficulty: 'Hard', category: 'Массивы' },
];

const TaskCardSkeleton = () => (
  <Card sx={{ height: 260 }}>
    <CardContent sx={{ p: 3 }}>
      <Skeleton variant="text" width="70%" height={32} sx={{ mb: 1.5 }} />
      <Skeleton variant="text" width="100%" />
      <Skeleton variant="text" width="100%" />
      <Skeleton variant="text" width="80%" sx={{ mb: 2.5 }} />
      <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
        <Skeleton variant="rectangular" width={70} height={22} sx={{ borderRadius: 1 }} />
        <Skeleton variant="rectangular" width={80} height={22} sx={{ borderRadius: 1 }} />
      </Box>
      <Skeleton variant="rectangular" height={36} sx={{ borderRadius: 1.5 }} />
    </CardContent>
  </Card>
);

const DEFAULT_FILTERS = {
  search: '',
  difficulty: 'all' as Difficulty,
  solvedStatus: 'all' as SolvedStatus,
  category: null as string | null,
};

const TaskList = () => {
  const navigate = useNavigate();
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [submissions, setSubmissions] = useState<SubmissionSummary[]>([]);
  const [progressOpen, setProgressOpen] = useState(false);

  // Filter state
  const [search, setSearch] = useState(DEFAULT_FILTERS.search);
  const [difficulty, setDifficulty] = useState<Difficulty>(DEFAULT_FILTERS.difficulty);
  const [solvedStatus, setSolvedStatus] = useState<SolvedStatus>(DEFAULT_FILTERS.solvedStatus);
  const [category, setCategory] = useState<string | null>(DEFAULT_FILTERS.category);

  useEffect(() => {
    const fetchTasks = async () => {
      try {
        setLoading(true);
        const data = await taskService.getAll();
        setTasks(data);
        setError(null);
      } catch (_err: unknown) {
        setError('Не удалось загрузить задачи с сервера. Показаны демо-данные.');
        setTasks(DEMO_TASKS);
      } finally {
        setLoading(false);
      }
    };
    fetchTasks();
  }, []);

  useEffect(() => {
    meService.getSubmissions().then(setSubmissions).catch(() => {
      // Non-critical
    });
  }, []);

  const solvedTaskIds = useMemo(
    () => new Set(submissions.filter((s) => s.status === 'SUCCESS').map((s) => s.taskId)),
    [submissions],
  );

  const categories = useMemo(() => {
    const cats = new Set<string>();
    for (const t of tasks) {
      if (t.category) cats.add(t.category);
    }
    return Array.from(cats).sort();
  }, [tasks]);

  const dirty =
    search !== DEFAULT_FILTERS.search ||
    difficulty !== DEFAULT_FILTERS.difficulty ||
    solvedStatus !== DEFAULT_FILTERS.solvedStatus ||
    category !== DEFAULT_FILTERS.category;

  const handleReset = () => {
    setSearch(DEFAULT_FILTERS.search);
    setDifficulty(DEFAULT_FILTERS.difficulty);
    setSolvedStatus(DEFAULT_FILTERS.solvedStatus);
    setCategory(DEFAULT_FILTERS.category);
  };

  const filteredTasks = useMemo(() => {
    return tasks.filter((task) => {
      const matchSearch =
        task.title.toLowerCase().includes(search.toLowerCase()) ||
        task.description.toLowerCase().includes(search.toLowerCase());
      const matchDiff = difficulty === 'all' || task.difficulty === difficulty;
      const matchSolved =
        solvedStatus === 'all'
          ? true
          : solvedStatus === 'solved'
          ? solvedTaskIds.has(task.testId)
          : !solvedTaskIds.has(task.testId);
      const matchCategory = category === null || task.category === category;
      return matchSearch && matchDiff && matchSolved && matchCategory;
    });
  }, [tasks, search, difficulty, solvedStatus, category, solvedTaskIds]);

  const solvedCount = solvedTaskIds.size;
  const totalTasks = tasks.length;
  const progressPct = totalTasks > 0 ? Math.round((solvedCount / totalTasks) * 100) : 0;

  return (
    <Box sx={{ width: '100%' }}>
      {/* ── Page header: title + progress toggle ── */}
      <Box
        sx={{
          display: 'flex',
          alignItems: { xs: 'flex-start', sm: 'center' },
          justifyContent: 'space-between',
          gap: 2,
          mb: 3,
          flexWrap: 'wrap',
        }}
      >
        <Box>
          <Typography variant="h4" component="h1" sx={{ fontWeight: 700, mb: 0.5 }}>
            Задачи
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Выберите задачу и напишите решение — AI проверит и даст обратную связь
          </Typography>
        </Box>
        <Button
          variant="outlined"
          startIcon={<BarChartIcon />}
          onClick={() => setProgressOpen(true)}
          aria-label="Открыть панель прогресса"
          sx={{ flexShrink: 0, fontWeight: 600 }}
        >
          Прогресс {totalTasks > 0 && <Box component="span" sx={{ ml: 1, color: 'text.secondary', fontWeight: 500 }}>{solvedCount}/{totalTasks} · {progressPct}%</Box>}
        </Button>
      </Box>

      {error && (
        <Alert severity="warning" sx={{ mb: 3, borderRadius: 2 }}>
          {error}
        </Alert>
      )}

      {/* ── Filter bar (horizontal, above grid) ── */}
      <Box
        sx={{
          mb: 3,
          p: 2,
          borderRadius: 2,
          border: 1,
          borderColor: 'divider',
          bgcolor: isDark ? alpha(brand.indigo, 0.04) : alpha(brand.indigo, 0.02),
          display: 'flex',
          flexWrap: 'wrap',
          gap: 2,
          alignItems: 'center',
        }}
      >
        <TextField
          size="small"
          placeholder="Поиск по названию или описанию…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          aria-label="Поиск задач"
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" sx={{ color: 'text.disabled' }} />
              </InputAdornment>
            ),
          }}
          sx={{ flexGrow: 1, minWidth: 240, maxWidth: 480 }}
        />

        <ToggleButtonGroup
          size="small"
          value={difficulty}
          exclusive
          onChange={(_e, v) => v && setDifficulty(v)}
          aria-label="Фильтр по сложности"
        >
          <ToggleButton value="all">Все</ToggleButton>
          <ToggleButton value="Easy">Лёгкая</ToggleButton>
          <ToggleButton value="Medium">Средняя</ToggleButton>
          <ToggleButton value="Hard">Сложная</ToggleButton>
        </ToggleButtonGroup>

        <ToggleButtonGroup
          size="small"
          value={solvedStatus}
          exclusive
          onChange={(_e, v) => v && setSolvedStatus(v)}
          aria-label="Фильтр по статусу решения"
        >
          <ToggleButton value="all">Все</ToggleButton>
          <ToggleButton value="solved">Решено</ToggleButton>
          <ToggleButton value="unsolved">Не решено</ToggleButton>
        </ToggleButtonGroup>

        <Autocomplete
          size="small"
          options={categories}
          value={category}
          onChange={(_e, v) => setCategory(v)}
          renderInput={(params) => <TextField {...params} placeholder="Все категории" aria-label="Фильтр по категории" />}
          sx={{ minWidth: 200 }}
          isOptionEqualToValue={(o, v) => o === v}
        />

        {dirty && (
          <Button
            size="small"
            variant="text"
            startIcon={<RestartAltIcon />}
            onClick={handleReset}
            aria-label="Сбросить все фильтры"
            sx={{ ml: 'auto' }}
          >
            Сбросить
          </Button>
        )}
      </Box>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Показано {filteredTasks.length} из {tasks.length}
      </Typography>

      {/* ── Task grid (full width) ── */}
      {loading ? (
        <Grid container spacing={3} aria-busy="true">
          {[0, 1, 2, 3, 4, 5, 6, 7].map((i) => (
            <Grid item xs={12} sm={6} md={4} lg={3} xl={2.4} key={i}>
              <TaskCardSkeleton />
            </Grid>
          ))}
        </Grid>
      ) : filteredTasks.length === 0 && tasks.length === 0 ? (
        <Box sx={{ textAlign: 'center', py: 10, color: 'text.secondary' }}>
          <Typography variant="h6" sx={{ mb: 1 }}>Задачи не найдены</Typography>
          <Typography variant="body2">Список задач пуст. Обратитесь к преподавателю.</Typography>
        </Box>
      ) : filteredTasks.length === 0 ? (
        <Box sx={{ textAlign: 'center', py: 10, color: 'text.secondary' }}>
          <Typography variant="h6" sx={{ mb: 1 }}>По вашему запросу ничего не найдено</Typography>
          <Typography variant="body2" sx={{ mb: 3 }}>Попробуйте изменить поисковый запрос или фильтры</Typography>
          <Button variant="outlined" startIcon={<RestartAltIcon />} onClick={handleReset}>
            Сбросить фильтры
          </Button>
        </Box>
      ) : (
        <Grid container spacing={3}>
          {filteredTasks.map((task) => {
            const diff = DIFFICULTY_COLORS[task.difficulty] ?? { bg: '#6b7280', text: '#374151' };
            const isSolved = solvedTaskIds.has(task.testId);
            return (
              <Grid item xs={12} sm={6} md={4} lg={3} xl={2.4} key={task.testId}>
                <Card
                  role="button"
                  tabIndex={0}
                  onClick={() => navigate(`/tasks/${task.testId}`)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      navigate(`/tasks/${task.testId}`);
                    }
                  }}
                  aria-label={`Открыть задачу: ${task.title}`}
                  sx={{
                    height: '100%',
                    display: 'flex',
                    flexDirection: 'column',
                    cursor: 'pointer',
                    position: 'relative',
                    transition: 'transform 0.18s, box-shadow 0.18s, border-color 0.18s',
                    border: 1,
                    borderColor: isSolved ? alpha('#22c55e', 0.4) : 'divider',
                    '&:hover': {
                      transform: 'translateY(-4px)',
                      borderColor: alpha(brand.indigo, isDark ? 0.5 : 0.4),
                      boxShadow: isDark
                        ? `0 12px 36px rgba(0,0,0,0.5)`
                        : `0 12px 28px rgba(0,0,0,0.12)`,
                    },
                    '&:focus-visible': {
                      outline: '2px solid',
                      outlineColor: 'primary.main',
                      outlineOffset: 2,
                    },
                  }}
                >
                  {isSolved && (
                    <Box
                      sx={{
                        position: 'absolute',
                        top: 12,
                        right: 12,
                        display: 'flex',
                        alignItems: 'center',
                        gap: 0.5,
                        px: 1,
                        py: 0.25,
                        borderRadius: 1,
                        bgcolor: alpha('#22c55e', 0.15),
                        color: '#16a34a',
                        fontSize: '0.7rem',
                        fontWeight: 700,
                        textTransform: 'uppercase',
                        letterSpacing: 0.5,
                      }}
                      aria-label="Решено"
                    >
                      <CheckCircleOutlineIcon sx={{ fontSize: 14 }} />
                      Решено
                    </Box>
                  )}
                  <CardContent sx={{ p: 3, flexGrow: 1, display: 'flex', flexDirection: 'column' }}>
                    {/* Title — primary focus */}
                    <Typography
                      variant="h6"
                      sx={{
                        fontWeight: 700,
                        fontSize: '1.15rem',
                        lineHeight: 1.3,
                        mb: 1.5,
                        pr: isSolved ? 10 : 0,
                      }}
                    >
                      {task.title}
                    </Typography>

                    {/* Description — 4 lines clamp, secondary focus */}
                    <Typography
                      variant="body2"
                      color="text.secondary"
                      sx={{
                        flexGrow: 1,
                        mb: 2.5,
                        display: '-webkit-box',
                        WebkitLineClamp: 4,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                        lineHeight: 1.6,
                        fontSize: '0.9rem',
                      }}
                    >
                      {task.description}
                    </Typography>

                    {/* Metadata row — difficulty + category */}
                    <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mb: 2 }}>
                      <Chip
                        label={DIFFICULTY_RU[task.difficulty] ?? task.difficulty}
                        size="small"
                        sx={{
                          fontWeight: 700,
                          background: alpha(diff.bg, isDark ? 0.2 : 0.12),
                          color: isDark ? diff.bg : diff.text,
                          borderColor: alpha(diff.bg, 0.3),
                          border: '1px solid',
                        }}
                      />
                      {task.category && (
                        <Chip
                          label={task.category}
                          size="small"
                          variant="outlined"
                          sx={{ fontWeight: 500, fontSize: '0.72rem' }}
                        />
                      )}
                    </Box>

                    {/* CTA */}
                    <Button
                      variant={isSolved ? 'outlined' : 'contained'}
                      fullWidth
                      endIcon={<PlayArrowIcon />}
                      onClick={(e) => {
                        e.stopPropagation();
                        navigate(`/tasks/${task.testId}`);
                      }}
                      sx={{ mt: 'auto' }}
                    >
                      {isSolved ? 'Решить снова' : 'Решить задачу'}
                    </Button>
                  </CardContent>
                </Card>
              </Grid>
            );
          })}
        </Grid>
      )}

      {/* ── Progress Drawer ── */}
      <Drawer
        anchor="right"
        open={progressOpen}
        onClose={() => setProgressOpen(false)}
        ModalProps={{ keepMounted: false }}
        PaperProps={{
          sx: {
            width: 340,
            maxWidth: '90vw',
            display: 'flex',
            flexDirection: 'column',
          },
        }}
      >
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            p: 2,
            borderBottom: 1,
            borderColor: 'divider',
          }}
        >
          <Typography variant="h6" sx={{ fontWeight: 700 }}>
            Прогресс
          </Typography>
          <IconButton
            onClick={() => setProgressOpen(false)}
            aria-label="Закрыть панель прогресса"
            autoFocus
          >
            <CloseIcon />
          </IconButton>
        </Box>
        <Box sx={{ flex: 1, overflowY: 'auto', p: 0 }}>
          <TaskProgressPanel
            tasks={tasks}
            solvedTaskIds={solvedTaskIds}
            submissions={submissions}
          />
        </Box>
      </Drawer>
    </Box>
  );
};

export default TaskList;
