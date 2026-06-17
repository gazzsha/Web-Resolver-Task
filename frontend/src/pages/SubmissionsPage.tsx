import { useEffect, useState, useMemo } from 'react';
import { useNavigate, useSearchParams, Link as RouterLink } from 'react-router-dom';
import {
  Box,
  Typography,
  Alert,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Skeleton,
  alpha,
  useTheme,
  useMediaQuery,
  Card,
  CardContent,
  Stack,
  IconButton,
  Tooltip,
} from '@mui/material';
import HistoryIcon from '@mui/icons-material/History';
import CodeIcon from '@mui/icons-material/Code';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import ReplayIcon from '@mui/icons-material/Replay';
import { meService, taskService } from '@/services/api';
import type { SubmissionSummary, Task } from '@/types';
import { brand } from '@/theme/theme';
import { StatusChip, LanguageChip } from '@/components/submissions/chips';
import SubmissionsFilterPanel, {
  type SubmissionsFilters,
  DEFAULT_FILTERS,
} from '@/components/submissions/SubmissionsFilterPanel';
import SubmissionsMiniStats from '@/components/submissions/SubmissionsMiniStats';

// ── helpers ──────────────────────────────────────────────────────────────────

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' });
}

// ── skeleton rows ─────────────────────────────────────────────────────────────

const SkeletonRow = () => (
  <TableRow>
    {[140, 200, 70, 90, 60, 80].map((w, i) => (
      <TableCell key={i}>
        <Skeleton variant="text" width={w} height={22} />
      </TableCell>
    ))}
  </TableRow>
);

// ── mobile card ───────────────────────────────────────────────────────────────

const SubmissionCard: React.FC<{ item: SubmissionSummary }> = ({ item }) => {
  const navigate = useNavigate();
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

  return (
    <Card
      sx={{
        cursor: 'pointer',
        mb: 1.5,
        transition: 'transform 0.15s, box-shadow 0.15s',
        '&:hover': {
          transform: 'translateY(-2px)',
          boxShadow: isDark
            ? `0 6px 24px ${alpha(brand.indigo, 0.25)}`
            : `0 6px 18px ${alpha(brand.indigo, 0.15)}`,
        },
      }}
      onClick={() => navigate(`/results/${item.id}`)}
    >
      <CardContent sx={{ p: 2 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 1 }}>
          <Typography
            variant="subtitle2"
            sx={{ fontWeight: 700, flex: 1, mr: 1 }}
            component={RouterLink}
            to={`/tasks/${item.taskId}`}
            onClick={(e) => e.stopPropagation()}
            style={{ textDecoration: 'none', color: 'inherit' }}
          >
            {item.taskTitle ?? 'Без названия'}
          </Typography>
          <StatusChip status={item.status} />
        </Box>
        <Stack direction="row" spacing={1} alignItems="center">
          <LanguageChip language={item.language} />
          {item.passedTests !== null && item.totalTests !== null && (
            <Typography
              variant="body2"
              sx={{
                fontFamily: '"JetBrains Mono", monospace',
                fontWeight: 600,
                color: item.passedTests === item.totalTests ? 'success.main' : 'warning.main',
              }}
            >
              {item.passedTests}/{item.totalTests}
            </Typography>
          )}
          <Typography variant="caption" color="text.secondary" sx={{ ml: 'auto !important' }}>
            {formatDate(item.createdAt)}
          </Typography>
        </Stack>
      </CardContent>
    </Card>
  );
};

// ── filter logic ──────────────────────────────────────────────────────────────

function applyFilters(
  submissions: SubmissionSummary[],
  filters: SubmissionsFilters
): SubmissionSummary[] {
  let result = submissions;

  if (filters.statuses.length > 0) {
    result = result.filter((s) => filters.statuses.includes(s.status));
  }

  if (filters.languages.length > 0) {
    result = result.filter((s) => filters.languages.includes(s.language));
  }

  if (filters.taskId) {
    result = result.filter((s) => s.taskId === filters.taskId);
  }

  if (filters.period !== 'all') {
    const days = filters.period === '7' ? 7 : 30;
    const cutoff = Date.now() - days * 86_400_000;
    result = result.filter((s) => new Date(s.createdAt).getTime() >= cutoff);
  }

  return result;
}

// ── main component ────────────────────────────────────────────────────────────

const SubmissionsPage = () => {
  const theme = useTheme();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const isDark = theme.palette.mode === 'dark';
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const isDesktop = useMediaQuery(theme.breakpoints.up('md'));

  const [submissions, setSubmissions] = useState<SubmissionSummary[]>([]);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Pre-fill taskId from ?task= query param
  const initialTaskId = searchParams.get('task');

  const [filters, setFilters] = useState<SubmissionsFilters>({
    ...DEFAULT_FILTERS,
    taskId: initialTaskId,
  });

  useEffect(() => {
    Promise.all([meService.getSubmissions(), taskService.getAll()])
      .then(([subs, allTasks]) => {
        setSubmissions(subs);
        setTasks(allTasks);
        setError(null);
      })
      .catch(() => {
        setError('Не удалось загрузить решения. Проверьте подключение к серверу.');
      })
      .finally(() => setLoading(false));
  }, []);

  const filteredSubmissions = useMemo(
    () => applyFilters(submissions, filters),
    [submissions, filters]
  );

  // ── Desktop table ──
  const desktopTable = (
    <TableContainer
      component={Paper}
      sx={{
        borderRadius: 2,
        border: `1px solid ${isDark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.07)'}`,
      }}
    >
      <Table size="small">
        <TableHead>
          <TableRow
            sx={{
              background: isDark
                ? alpha(brand.indigo, 0.08)
                : alpha(brand.indigo, 0.04),
            }}
          >
            <TableCell>Дата</TableCell>
            <TableCell>Задача</TableCell>
            <TableCell>Язык</TableCell>
            <TableCell>Статус</TableCell>
            <TableCell align="center">Тесты</TableCell>
            <TableCell align="right" sx={{ width: 120 }}>Действия</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {filteredSubmissions.map((item) => {
            return (
              <TableRow
                key={item.id}
                hover
                sx={{
                  cursor: 'pointer',
                  '&:last-child td': { border: 0 },
                  transition: 'background 0.15s',
                  '& .row-actions': { opacity: 0, transition: 'opacity 150ms' },
                  '&:hover .row-actions': { opacity: 1 },
                }}
                onClick={() => navigate(`/results/${item.id}`)}
              >
                <TableCell>
                  <Typography
                    variant="body2"
                    sx={{ fontFamily: '"JetBrains Mono", monospace', fontSize: '0.78rem' }}
                  >
                    {formatDate(item.createdAt)}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Typography
                    component={RouterLink}
                    to={`/tasks/${item.taskId}`}
                    onClick={(e) => e.stopPropagation()}
                    variant="body2"
                    sx={{
                      fontWeight: 600,
                      color: 'primary.main',
                      textDecoration: 'none',
                      '&:hover': { textDecoration: 'underline' },
                    }}
                  >
                    {item.taskTitle ?? 'Без названия'}
                  </Typography>
                </TableCell>
                <TableCell>
                  <LanguageChip language={item.language} />
                </TableCell>
                <TableCell>
                  <StatusChip status={item.status} />
                </TableCell>
                <TableCell align="center">
                  {item.passedTests !== null && item.totalTests !== null ? (
                    <Typography
                      variant="body2"
                      sx={{
                        fontFamily: '"JetBrains Mono", monospace',
                        fontWeight: 600,
                        color:
                          item.passedTests === item.totalTests
                            ? 'success.main'
                            : 'warning.main',
                      }}
                    >
                      {item.passedTests}/{item.totalTests}
                    </Typography>
                  ) : (
                    <Typography variant="body2" color="text.disabled">—</Typography>
                  )}
                </TableCell>
                <TableCell align="right">
                  <Box className="row-actions" sx={{ display: 'flex', gap: 0.5, justifyContent: 'flex-end' }}>
                    <Tooltip title="Открыть результаты">
                      <IconButton
                        size="small"
                        component="a"
                        href={`/results/${item.id}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        onClick={(e) => e.stopPropagation()}
                        aria-label="Открыть результаты в новой вкладке"
                      >
                        <OpenInNewIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Решить снова">
                      <IconButton
                        size="small"
                        onClick={(e) => {
                          e.stopPropagation();
                          navigate(`/submit/${item.taskId}`);
                        }}
                        aria-label="Решить задачу снова"
                      >
                        <ReplayIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </Box>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </TableContainer>
  );

  // ── Layout content ──
  const content = (
    <>
      {/* Page header */}
      <Box sx={{ mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 0.5 }}>
          <HistoryIcon
            sx={{
              fontSize: 28,
              color: brand.indigo,
              filter: isDark ? `drop-shadow(0 0 6px ${alpha(brand.indigo, 0.6)})` : 'none',
            }}
          />
          <Typography variant="h4" component="h1" sx={{ fontWeight: 700 }}>
            Мои решения
          </Typography>
        </Box>
        <Typography variant="body1" color="text.secondary">
          История всех отправленных решений
        </Typography>
        {!loading && !error && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Показано {filteredSubmissions.length} из {submissions.length}
          </Typography>
        )}
      </Box>

      {/* Error state */}
      {error && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {error}
        </Alert>
      )}

      {/* Loading */}
      {loading && (
        isMobile ? (
          <Box>
            {[0, 1, 2, 3, 4].map((i) => (
              <Card key={i} sx={{ mb: 1.5 }}>
                <CardContent sx={{ p: 2 }}>
                  <Skeleton variant="text" width="60%" height={22} sx={{ mb: 1 }} />
                  <Skeleton variant="text" width="40%" height={18} />
                </CardContent>
              </Card>
            ))}
          </Box>
        ) : (
          <TableContainer component={Paper} sx={{ borderRadius: 2 }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  {['Дата', 'Задача', 'Язык', 'Статус', 'Тесты', 'Действия'].map((h) => (
                    <TableCell key={h}>{h}</TableCell>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {[0, 1, 2, 3, 4].map((i) => <SkeletonRow key={i} />)}
              </TableBody>
            </Table>
          </TableContainer>
        )
      )}

      {/* Empty state */}
      {!loading && !error && filteredSubmissions.length === 0 && (
        <Box
          sx={{
            textAlign: 'center',
            py: 10,
            borderRadius: 3,
            background: isDark
              ? alpha(brand.indigo, 0.05)
              : alpha(brand.indigo, 0.03),
            border: `1px dashed ${alpha(brand.indigo, 0.2)}`,
          }}
        >
          <CodeIcon sx={{ fontSize: 56, color: alpha(brand.indigo, 0.4), mb: 2 }} />
          <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>
            {submissions.length === 0
              ? 'Вы ещё не отправляли решений'
              : 'Нет решений по заданным фильтрам'}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            {submissions.length === 0
              ? 'Попробуйте решить задачу и вернитесь сюда, чтобы увидеть историю'
              : 'Попробуйте изменить условия фильтрации'}
          </Typography>
          {submissions.length === 0 && (
            <Button
              variant="contained"
              endIcon={<ArrowForwardIcon />}
              onClick={() => navigate('/tasks')}
            >
              Перейти к задачам
            </Button>
          )}
        </Box>
      )}

      {/* Mobile cards */}
      {!loading && !error && filteredSubmissions.length > 0 && isMobile && (
        <Box>
          {filteredSubmissions.map((item) => (
            <SubmissionCard key={item.id} item={item} />
          ))}
        </Box>
      )}

      {/* Desktop table */}
      {!loading && !error && filteredSubmissions.length > 0 && !isMobile && desktopTable}
    </>
  );

  // ── 3-column desktop layout ──
  if (isDesktop) {
    return (
      <Box
        sx={{
          width: '100%',
          maxWidth: 1400,
          display: 'grid',
          gridTemplateColumns: '220px 1fr 240px',
          gap: '24px',
          alignItems: 'flex-start',
        }}
      >
        {/* Left sidebar — filters */}
        <Box sx={{ position: 'sticky', top: 80 }}>
          <SubmissionsFilterPanel
            filters={filters}
            onChange={setFilters}
            tasks={tasks}
          />
        </Box>

        {/* Center — main content */}
        <Box>{content}</Box>

        {/* Right sidebar — mini stats */}
        <Box sx={{ position: 'sticky', top: 80 }}>
          <SubmissionsMiniStats submissions={filteredSubmissions} />
        </Box>
      </Box>
    );
  }

  // ── Mobile / tablet single-column ──
  return (
    <Box sx={{ width: '100%', maxWidth: 1400 }}>
      {content}
    </Box>
  );
};

export default SubmissionsPage;
