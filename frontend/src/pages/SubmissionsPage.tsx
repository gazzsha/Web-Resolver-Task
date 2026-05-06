import { useEffect, useState } from 'react';
import { useNavigate, Link as RouterLink } from 'react-router-dom';
import {
  Box,
  Typography,
  Alert,
  Chip,
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
} from '@mui/material';
import HistoryIcon from '@mui/icons-material/History';
import CodeIcon from '@mui/icons-material/Code';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import { meService } from '@/services/api';
import type { SubmissionSummary } from '@/types';
import { brand } from '@/theme/theme';

// ── helpers ──────────────────────────────────────────────────────────────────

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' });
}

const STATUS_MAP: Record<
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

const LANG_COLORS: Record<string, string> = {
  java: '#f89820',
  kotlin: '#7f52ff',
  python: '#3572a5',
};

interface StatusChipProps {
  status: SubmissionSummary['status'];
}

const StatusChip: React.FC<StatusChipProps> = ({ status }) => {
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

interface TestsRatioProps {
  passed: number | null;
  total: number | null;
}

const TestsRatio: React.FC<TestsRatioProps> = ({ passed, total }) => {
  if (passed === null || total === null) {
    return <Typography variant="body2" color="text.disabled">—</Typography>;
  }
  const allPassed = passed === total;
  return (
    <Typography
      variant="body2"
      sx={{
        fontFamily: '"JetBrains Mono", monospace',
        fontWeight: 600,
        color: allPassed ? 'success.main' : 'warning.main',
      }}
    >
      {passed}/{total}
    </Typography>
  );
};

// ── skeleton rows ─────────────────────────────────────────────────────────────

const SkeletonRow = () => (
  <TableRow>
    {[140, 200, 70, 90, 60].map((w, i) => (
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
  const langColor = LANG_COLORS[item.language] ?? brand.indigo;

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
          <Chip
            label={item.language}
            size="small"
            sx={{
              fontFamily: '"JetBrains Mono", monospace',
              fontWeight: 600,
              fontSize: '0.72rem',
              background: alpha(langColor, isDark ? 0.2 : 0.12),
              color: langColor,
              border: `1px solid ${alpha(langColor, 0.35)}`,
            }}
          />
          <TestsRatio passed={item.passedTests} total={item.totalTests} />
          <Typography variant="caption" color="text.secondary" sx={{ ml: 'auto !important' }}>
            {formatDate(item.createdAt)}
          </Typography>
        </Stack>
      </CardContent>
    </Card>
  );
};

// ── main component ────────────────────────────────────────────────────────────

const SubmissionsPage = () => {
  const theme = useTheme();
  const navigate = useNavigate();
  const isDark = theme.palette.mode === 'dark';
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));

  const [submissions, setSubmissions] = useState<SubmissionSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    meService
      .getSubmissions()
      .then((data) => {
        setSubmissions(data);
        setError(null);
      })
      .catch(() => {
        setError('Не удалось загрузить решения. Проверьте подключение к серверу.');
      })
      .finally(() => setLoading(false));
  }, []);

  return (
    <Box sx={{ width: '100%', maxWidth: 1400 }}>
      {/* Page header */}
      <Box sx={{ mb: 4 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 0.5 }}>
          <HistoryIcon
            sx={{
              fontSize: 28,
              color: brand.indigo,
              filter: isDark ? `drop-shadow(0 0 6px ${alpha(brand.indigo, 0.6)})` : 'none',
            }}
          />
          <Typography variant="h4" sx={{ fontWeight: 700 }}>
            Мои решения
          </Typography>
        </Box>
        <Typography variant="body1" color="text.secondary">
          История всех отправленных решений
        </Typography>
      </Box>

      {/* Error state */}
      {error && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {error}
        </Alert>
      )}

      {/* Loading — mobile */}
      {loading && isMobile && (
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
      )}

      {/* Loading — desktop */}
      {loading && !isMobile && (
        <TableContainer component={Paper} sx={{ borderRadius: 2 }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                {['Дата', 'Задача', 'Язык', 'Статус', 'Тесты'].map((h) => (
                  <TableCell key={h}>{h}</TableCell>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {[0, 1, 2, 3, 4].map((i) => <SkeletonRow key={i} />)}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Empty state */}
      {!loading && !error && submissions.length === 0 && (
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
            Вы ещё не отправляли решений
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            Попробуйте решить задачу и вернитесь сюда, чтобы увидеть историю
          </Typography>
          <Button
            variant="contained"
            endIcon={<ArrowForwardIcon />}
            onClick={() => navigate('/tasks')}
          >
            Перейти к задачам
          </Button>
        </Box>
      )}

      {/* Mobile cards */}
      {!loading && !error && submissions.length > 0 && isMobile && (
        <Box>
          {submissions.map((item) => (
            <SubmissionCard key={item.id} item={item} />
          ))}
        </Box>
      )}

      {/* Desktop table */}
      {!loading && !error && submissions.length > 0 && !isMobile && (
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
              </TableRow>
            </TableHead>
            <TableBody>
              {submissions.map((item) => {
                const langColor = LANG_COLORS[item.language] ?? brand.indigo;
                return (
                  <TableRow
                    key={item.id}
                    hover
                    sx={{
                      cursor: 'pointer',
                      '&:last-child td': { border: 0 },
                      transition: 'background 0.15s',
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
                      <Chip
                        label={item.language}
                        size="small"
                        sx={{
                          fontFamily: '"JetBrains Mono", monospace',
                          fontWeight: 600,
                          fontSize: '0.72rem',
                          background: alpha(langColor, isDark ? 0.2 : 0.12),
                          color: langColor,
                          border: `1px solid ${alpha(langColor, 0.35)}`,
                        }}
                      />
                    </TableCell>
                    <TableCell>
                      <StatusChip status={item.status} />
                    </TableCell>
                    <TableCell align="center">
                      <TestsRatio passed={item.passedTests} total={item.totalTests} />
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Box>
  );
};

export default SubmissionsPage;
