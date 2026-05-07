import { useEffect, useRef, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Typography,
  Paper,
  Grid,
  Chip,
  LinearProgress,
  Alert,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  CircularProgress,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Card,
  CardContent,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Skeleton,
  alpha,
  useTheme,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import ErrorIcon from '@mui/icons-material/Error';
import TimerIcon from '@mui/icons-material/Timer';
import MemoryIcon from '@mui/icons-material/Memory';
import CodeIcon from '@mui/icons-material/Code';
import BugReportIcon from '@mui/icons-material/BugReport';
import ScheduleIcon from '@mui/icons-material/Schedule';
import FiberManualRecordIcon from '@mui/icons-material/FiberManualRecord';
import InfoIcon from '@mui/icons-material/Info';
import WarningIcon from '@mui/icons-material/Warning';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import ReplayIcon from '@mui/icons-material/Replay';
import type { SubmissionResult, AIAnalysisFull } from '@/types';
import { submissionService, aiService } from '@/services/api';
import { brand } from '@/theme/theme';

const POLL_INTERVAL_MS = 2000;
const MAX_POLL_DURATION_MS = 120_000;

type VerdictColor = 'success' | 'error' | 'warning' | 'default';

// Russian verdict labels
const VERDICT_RU: Record<string, string> = {
  OK: 'Верно',
  WRONG_ANSWER: 'Неверный ответ',
  RUNTIME_ERROR: 'Ошибка выполнения',
  COMPILATION_ERROR: 'Ошибка компиляции',
  TIME_LIMIT_EXCEEDED: 'Превышен лимит времени',
  MEMORY_LIMIT_EXCEEDED: 'Превышен лимит памяти',
  PRESENTATION_ERROR: 'Ошибка вывода',
};

// Russian status labels
const STATUS_RU: Record<string, string> = {
  SUCCESS: 'Принято',
  PARTIAL_SUCCESS: 'Частично принято',
  FAILED: 'Не принято',
  ERROR: 'Ошибка',
  PASSED: 'Пройден',
};

const COMPLEXITY_RU: Record<string, string> = {
  LOW: 'Низкая',
  MEDIUM: 'Средняя',
  HIGH: 'Высокая',
  VERY_HIGH: 'Очень высокая',
};

const getVerdictColor = (verdict: string): VerdictColor => {
  switch (verdict) {
    case 'OK': return 'success';
    case 'WRONG_ANSWER':
    case 'RUNTIME_ERROR':
    case 'COMPILATION_ERROR': return 'error';
    case 'TIME_LIMIT_EXCEEDED':
    case 'MEMORY_LIMIT_EXCEEDED':
    case 'PRESENTATION_ERROR': return 'warning';
    default: return 'default';
  }
};

const getVerdictIcon = (verdict: string) => {
  switch (verdict) {
    case 'OK': return <CheckCircleIcon color="success" />;
    case 'WRONG_ANSWER': return <CancelIcon color="error" />;
    case 'TIME_LIMIT_EXCEEDED': return <ScheduleIcon color="warning" />;
    case 'MEMORY_LIMIT_EXCEEDED': return <MemoryIcon color="warning" />;
    case 'RUNTIME_ERROR': return <BugReportIcon color="error" />;
    case 'COMPILATION_ERROR': return <CodeIcon color="error" />;
    case 'PRESENTATION_ERROR': return <WarningIcon color="warning" />;
    default: return <ErrorIcon color="warning" />;
  }
};

const getStatusColor = (status: string): VerdictColor => {
  switch (status) {
    case 'SUCCESS': return 'success';
    case 'PARTIAL_SUCCESS': return 'warning';
    case 'FAILED':
    case 'ERROR': return 'error';
    default: return 'default';
  }
};

const TERMINAL_STATUSES = new Set(['SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'ERROR']);

const Results = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

  const [result, setResult] = useState<SubmissionResult | null>(null);
  const [aiFullAnalysis, setAiFullAnalysis] = useState<AIAnalysisFull | null>(null);
  const [aiLoading, setAiLoading] = useState(false);
  const [loading, setLoading] = useState(true);
  const [timedOut, setTimedOut] = useState(false);
  const [elapsedSec, setElapsedSec] = useState(0);
  const [loadingPhase, setLoadingPhase] = useState<'running' | 'analyzing'>('running');

  const startedAtRef = useRef<number>(Date.now());
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const phaseTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const stopPolling = () => {
    if (intervalRef.current) clearInterval(intervalRef.current);
    if (timerRef.current) clearInterval(timerRef.current);
    if (phaseTimerRef.current) clearTimeout(phaseTimerRef.current);
  };

  useEffect(() => {
    if (!id) return;

    const poll = async () => {
      const elapsed = Date.now() - startedAtRef.current;
      if (elapsed >= MAX_POLL_DURATION_MS) {
        stopPolling();
        setTimedOut(true);
        setLoading(false);
        return;
      }

      try {
        const data = await submissionService.getResult(id);
        if (TERMINAL_STATUSES.has(data.status)) {
          stopPolling();
          setResult(data);
          setLoading(false);
          setAiLoading(true);
          try {
            const full = await aiService.getAnalysis(id);
            setAiFullAnalysis(full);
          } finally {
            setAiLoading(false);
          }
        }
      } catch (err: unknown) {
        const status = (err as any)?.response?.status;
        if (status !== 404) {
          stopPolling();
          setLoading(false);
        }
      }
    };

    timerRef.current = setInterval(() => {
      setElapsedSec(Math.floor((Date.now() - startedAtRef.current) / 1000));
    }, 1000);

    phaseTimerRef.current = setTimeout(() => setLoadingPhase('analyzing'), 7000);
    poll();
    intervalRef.current = setInterval(poll, POLL_INTERVAL_MS);

    return () => stopPolling();
  }, [id]);

  // ──── Loading state ────
  if (loading) {
    return (
      <Box sx={{ maxWidth: 640, mx: 'auto', mt: 8 }}>
        <Card role="status" aria-live="polite" aria-label="Ожидание результатов">
          <CardContent sx={{ textAlign: 'center', py: 6, px: 4 }}>
            <Box sx={{ mb: 3, position: 'relative', display: 'inline-block' }}>
              <CircularProgress size={72} thickness={3.5} />
              <Box
                sx={{
                  position: 'absolute',
                  top: '50%',
                  left: '50%',
                  transform: 'translate(-50%, -50%)',
                }}
              >
                <CodeIcon sx={{ fontSize: 28, color: 'primary.main' }} />
              </Box>
            </Box>
            <Typography variant="h5" sx={{ fontWeight: 700, mb: 1 }}>
              {loadingPhase === 'running' ? 'Запускаем тесты...' : 'Анализируем код с AI...'}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
              {loadingPhase === 'running'
                ? 'Выполнение тестов займёт несколько секунд'
                : 'AI изучает ваш код и готовит обратную связь'}
            </Typography>
            <LinearProgress
              sx={{ mb: 2, borderRadius: 2, height: 5 }}
            />
            <Typography variant="caption" color="text.secondary">
              Прошло: {elapsedSec} сек.
            </Typography>
            {elapsedSec >= 30 && (
              <Typography variant="caption" display="block" color="text.secondary" sx={{ mt: 1 }}>
                Если первый запуск — Docker-образ может загружаться дольше обычного
              </Typography>
            )}
          </CardContent>
        </Card>
      </Box>
    );
  }

  // ──── Timed out ────
  if (timedOut) {
    return (
      <Box sx={{ maxWidth: 640, mx: 'auto', mt: 6 }}>
        <Alert severity="warning" sx={{ mb: 2.5 }}>
          Результат не получен за 120 секунд. Возможно, sandbox перегружен. Попробуйте обновить страницу через минуту.
        </Alert>
        <Button variant="contained" onClick={() => window.location.reload()} startIcon={<ReplayIcon />}>
          Обновить страницу
        </Button>
      </Box>
    );
  }

  // ──── Not found ────
  if (!result) {
    return (
      <Box sx={{ maxWidth: 640, mx: 'auto', mt: 6 }}>
        <Alert severity="error" sx={{ mb: 2.5 }}>
          Результаты не найдены. Пожалуйста, сначала отправьте решение.
        </Alert>
        <Button variant="contained" onClick={() => navigate('/tasks')}>
          Все задачи
        </Button>
      </Box>
    );
  }

  const passedCount = result.testResults.filter((r) => r.status === 'PASSED').length;
  const totalCount = result.testResults.length;
  const successRate = totalCount > 0 ? Math.round((passedCount / totalCount) * 100) : 0;
  const statusColor = getStatusColor(result.status);
  const isSuccess = result.status === 'SUCCESS';

  const summaryAI = result.aiAnalysis;
  const displayedQuality = aiFullAnalysis?.codeQuality ?? summaryAI?.codeQuality;
  const displayedComplexity = aiFullAnalysis?.complexity ?? summaryAI?.complexity;
  const displayedExplanation = aiFullAnalysis?.explanation ?? summaryAI?.explanation;
  const hasAIBlock =
    displayedQuality != null || displayedComplexity != null || displayedExplanation != null || aiFullAnalysis != null;

  return (
    <Box sx={{ width: '100%', maxWidth: 1200 }}>
      <Typography variant="h4" component="h1" sx={{ fontWeight: 700, mb: 4 }}>
        Результаты решения
      </Typography>

      {/* ──── Overall status ──── */}
      <Paper
        sx={{
          p: 3,
          mb: 3,
          border: `1px solid ${isDark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.07)'}`,
          boxShadow: 'none',
          background: isDark
            ? alpha(isSuccess ? theme.palette.success.main : theme.palette.error.main, 0.06)
            : alpha(isSuccess ? theme.palette.success.main : theme.palette.error.main, 0.04),
          borderColor: alpha(isSuccess ? theme.palette.success.main : theme.palette.error.main, isDark ? 0.25 : 0.18),
        }}
      >
        <Grid container spacing={3} alignItems="center">
          <Grid item xs={12} sm={6}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              {isSuccess ? (
                <CheckCircleIcon sx={{ color: theme.palette.success.main, fontSize: 52 }} />
              ) : (
                <ErrorIcon sx={{ color: theme.palette.error.main, fontSize: 52 }} />
              )}
              <Box>
                <Chip
                  label={STATUS_RU[result.status] ?? result.status.replace(/_/g, ' ')}
                  color={statusColor}
                  sx={{ fontWeight: 700, mb: 0.75 }}
                />
                <Typography variant="body2" color="text.secondary">
                  Пройдено тестов: {passedCount} / {totalCount} ({successRate}%)
                </Typography>
              </Box>
            </Box>
          </Grid>
          <Grid item xs={12} sm={6}>
            <Box sx={{ display: 'flex', gap: 3, flexWrap: 'wrap' }}>
              {[
                { icon: <TimerIcon />, value: `${result.totalExecutionTimeMs} мс`, label: 'Время' },
                { icon: <MemoryIcon />, value: result.memoryUsedKb > 0 ? `${result.memoryUsedKb} КБ` : '—', label: 'Память' },
                { icon: <CodeIcon />, value: `${result.passedTests}/${result.totalTests}`, label: 'Тесты' },
              ].map((m, i) => (
                <Box key={i} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Box sx={{ color: 'text.secondary' }}>{m.icon}</Box>
                  <Box>
                    <Typography
                      variant="subtitle2"
                      sx={{ fontFamily: '"JetBrains Mono", monospace', fontWeight: 700 }}
                    >
                      {m.value}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {m.label}
                    </Typography>
                  </Box>
                </Box>
              ))}
            </Box>
          </Grid>
        </Grid>
      </Paper>

      {/* ──── Test results table ──── */}
      <Paper
        sx={{
          mb: 3,
          border: `1px solid ${isDark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.07)'}`,
          boxShadow: 'none',
          overflow: 'hidden',
        }}
      >
        <Typography variant="h6" sx={{ p: 2.5, pb: 2, fontWeight: 700, borderBottom: 1, borderColor: 'divider' }}>
          Сводка тест-кейсов
        </Typography>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>#</TableCell>
                <TableCell>Статус</TableCell>
                <TableCell>Вердикт</TableCell>
                <TableCell align="right">Время</TableCell>
                <TableCell align="right">Память</TableCell>
                <TableCell>Детали</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {result.testResults.map((test, index) => (
                <TableRow
                  key={test.testId}
                  sx={{
                    '&:hover': { background: isDark ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.02)' },
                    background: test.status === 'PASSED'
                      ? alpha('#22c55e', isDark ? 0.05 : 0.03)
                      : 'transparent',
                  }}
                >
                  <TableCell>
                    <Typography variant="body2" fontWeight={700}>{index + 1}</Typography>
                  </TableCell>
                  <TableCell>
                    <Chip
                      icon={test.status === 'PASSED' ? <CheckCircleIcon /> : <ErrorIcon />}
                      label={STATUS_RU[test.status] ?? test.status}
                      color={test.status === 'PASSED' ? 'success' : 'error'}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                      {getVerdictIcon(test.verdict)}
                      <Chip
                        label={VERDICT_RU[test.verdict] ?? test.verdict.replace(/_/g, ' ')}
                        color={getVerdictColor(test.verdict)}
                        size="small"
                        variant="outlined"
                      />
                    </Box>
                  </TableCell>
                  <TableCell align="right">
                    <Typography variant="caption" sx={{ fontFamily: '"JetBrains Mono", monospace' }}>
                      {test.executionTimeMs} мс
                    </Typography>
                  </TableCell>
                  <TableCell align="right">
                    <Typography variant="caption" sx={{ fontFamily: '"JetBrains Mono", monospace' }}>
                      {test.memoryUsedKb > 0 ? `${test.memoryUsedKb} КБ` : '—'}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    {test.error ? (
                      <Chip label="Ошибка" color="error" size="small" variant="outlined" />
                    ) : test.output ? (
                      <Chip label="Есть вывод" size="small" color="info" variant="outlined" />
                    ) : (
                      <Typography variant="caption" color="text.secondary">—</Typography>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      {/* ──── Detailed test accordions ──── */}
      <Typography variant="h6" sx={{ mb: 2, fontWeight: 700 }}>
        Подробные результаты
      </Typography>
      {result.testResults.map((test, index) => (
        <Accordion
          key={test.testId}
          defaultExpanded={test.status !== 'PASSED'}
          sx={{ mb: 1.5 }}
        >
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, width: '100%', flexWrap: 'wrap', rowGap: 0.5 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                {getVerdictIcon(test.verdict)}
                <Typography variant="body2" fontWeight={700}>
                  Тест #{index + 1}
                </Typography>
              </Box>
              <Chip
                label={VERDICT_RU[test.verdict] ?? test.verdict.replace(/_/g, ' ')}
                color={getVerdictColor(test.verdict)}
                size="small"
                variant="outlined"
              />
              <Box sx={{ display: 'flex', gap: 2, ml: 'auto' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  <TimerIcon fontSize="small" sx={{ color: 'text.disabled', fontSize: 14 }} />
                  <Typography variant="caption" sx={{ fontFamily: '"JetBrains Mono", monospace' }}>
                    {test.executionTimeMs} мс
                  </Typography>
                </Box>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  <MemoryIcon fontSize="small" sx={{ color: 'text.disabled', fontSize: 14 }} />
                  <Typography variant="caption" sx={{ fontFamily: '"JetBrains Mono", monospace' }}>
                    {test.memoryUsedKb > 0 ? `${test.memoryUsedKb} КБ` : '—'}
                  </Typography>
                </Box>
              </Box>
            </Box>
          </AccordionSummary>
          <AccordionDetails sx={{ pt: 0 }}>
            <Grid container spacing={2}>
              {test.output && (
                <Grid item xs={12}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
                    Вывод программы
                  </Typography>
                  <Paper
                    variant="outlined"
                    sx={(t) => ({
                      p: 2,
                      bgcolor: t.palette.mode === 'dark' ? '#0d1117' : '#f6f8fa',
                      color: 'text.primary',
                      fontFamily: '"JetBrains Mono", monospace',
                      fontSize: '0.82rem',
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-all',
                      borderRadius: 2,
                    })}
                  >
                    {test.output}
                  </Paper>
                </Grid>
              )}
              {test.error && (
                <Grid item xs={12}>
                  <Alert severity="error" sx={{ mt: 0.5 }}>
                    <Typography
                      variant="body2"
                      sx={{ fontFamily: '"JetBrains Mono", monospace', whiteSpace: 'pre-wrap', fontSize: '0.8rem' }}
                    >
                      {test.error}
                    </Typography>
                  </Alert>
                </Grid>
              )}
            </Grid>
          </AccordionDetails>
        </Accordion>
      ))}

      {/* ──── AI analysis block ──── */}
      <Box sx={{ mt: 3 }}>
        {aiLoading ? (
          /* result arrived but AI analysis still in flight */
          <Skeleton variant="rectangular" height={120} sx={{ borderRadius: 2 }} />
        ) : !hasAIBlock ? (
          <Paper
            sx={{
              p: 3,
              border: `1px solid ${isDark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.07)'}`,
              boxShadow: 'none',
            }}
          >
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1.5 }}>
              <AutoAwesomeIcon sx={{ color: brand.rose }} />
              <Typography variant="h6" sx={{ fontWeight: 700 }}>
                AI-анализ кода
              </Typography>
            </Box>
            <Alert severity="info">
              AI-анализ для этого решения недоступен. Возможные причины: не задан{' '}
              <code>GIGACHAT_AUTH_KEY</code> в окружении, истёк токен или временно недоступен внешний сервис.
              Базовая проверка тестов прошла нормально — смотри таблицу выше.
            </Alert>
          </Paper>
        ) : (
          <Paper
            sx={{
              p: 3,
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
                  background: alpha(brand.rose, isDark ? 0.2 : 0.12),
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: brand.rose,
                }}
              >
                <AutoAwesomeIcon fontSize="small" />
              </Box>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>
                AI-анализ кода
              </Typography>
            </Box>

            {/* Quality score */}
            {displayedQuality != null && (
              <Box sx={{ mb: 3 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.75 }}>
                  <Typography variant="body2" fontWeight={600}>
                    Качество кода
                  </Typography>
                  <Typography
                    variant="body2"
                    fontWeight={700}
                    sx={{ fontFamily: '"JetBrains Mono", monospace' }}
                  >
                    {displayedQuality} / 100
                  </Typography>
                </Box>
                <LinearProgress
                  variant="determinate"
                  value={displayedQuality}
                  color={displayedQuality >= 75 ? 'success' : displayedQuality >= 50 ? 'warning' : 'error'}
                />
              </Box>
            )}

            {/* Complexity chip */}
            {displayedComplexity != null && (
              <Box sx={{ mb: 2 }}>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                  Сложность алгоритма
                </Typography>
                <Chip
                  label={COMPLEXITY_RU[displayedComplexity] ?? displayedComplexity}
                  color={
                    displayedComplexity === 'LOW' ? 'success'
                    : displayedComplexity === 'MEDIUM' ? 'info'
                    : displayedComplexity === 'HIGH' ? 'warning'
                    : 'error'
                  }
                  size="small"
                  sx={{ fontWeight: 700 }}
                />
              </Box>
            )}

            {/* Explanation */}
            {displayedExplanation && (
              <Paper
                variant="outlined"
                sx={{
                  p: 2,
                  mb: 2,
                  borderRadius: 2,
                  background: isDark ? alpha('#ffffff', 0.03) : alpha('#000000', 0.02),
                }}
              >
                <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', lineHeight: 1.7 }}>
                  {displayedExplanation}
                </Typography>
              </Paper>
            )}

            {/* Issues accordion */}
            {aiFullAnalysis && aiFullAnalysis.issues.length > 0 && (
              <Accordion sx={{ mt: 2 }}>
                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                  <Typography variant="subtitle2" fontWeight={700}>
                    Найдено проблем: {aiFullAnalysis.issues.length}
                  </Typography>
                </AccordionSummary>
                <AccordionDetails>
                  <List dense>
                    {aiFullAnalysis.issues.map((issue, idx) => {
                      const sevColor =
                        issue.severity === 'BLOCKER' || issue.severity === 'CRITICAL'
                          ? 'error'
                          : issue.severity === 'MAJOR'
                          ? 'warning'
                          : issue.severity === 'MINOR'
                          ? 'info'
                          : 'action';
                      const SevIcon =
                        issue.severity === 'BLOCKER' || issue.severity === 'CRITICAL'
                          ? ErrorIcon
                          : issue.severity === 'MAJOR'
                          ? WarningIcon
                          : InfoIcon;
                      return (
                        <ListItem key={idx} alignItems="flex-start" disableGutters sx={{ mb: 0.5 }}>
                          <ListItemIcon sx={{ minWidth: 32, mt: 0.5 }}>
                            <SevIcon color={sevColor as any} fontSize="small" />
                          </ListItemIcon>
                          <ListItemText
                            primary={`[${issue.type}] ${issue.message}${issue.line != null ? ` — строка ${issue.line}` : ''}`}
                            secondary={issue.suggestion}
                            primaryTypographyProps={{ fontSize: '0.84rem', fontWeight: 600 }}
                            secondaryTypographyProps={{ fontSize: '0.8rem' }}
                          />
                        </ListItem>
                      );
                    })}
                  </List>
                </AccordionDetails>
              </Accordion>
            )}

            {/* Recommendations */}
            {aiFullAnalysis && aiFullAnalysis.recommendations.length > 0 && (
              <Box sx={{ mt: 2 }}>
                <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1 }}>
                  Рекомендации
                </Typography>
                <List dense>
                  {aiFullAnalysis.recommendations.map((rec, idx) => (
                    <ListItem key={idx} disableGutters sx={{ py: 0.25 }}>
                      <ListItemIcon sx={{ minWidth: 24 }}>
                        <FiberManualRecordIcon sx={{ fontSize: 8, color: 'text.secondary' }} />
                      </ListItemIcon>
                      <ListItemText
                        primary={rec}
                        primaryTypographyProps={{ fontSize: '0.85rem' }}
                      />
                    </ListItem>
                  ))}
                </List>
              </Box>
            )}
          </Paper>
        )}
      </Box>

      {/* ──── Navigation buttons ──── */}
      <Box sx={{ mt: 3, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <Button
          variant="outlined"
          startIcon={<ReplayIcon />}
          onClick={() =>
            navigate(`/submit/${result.taskId}`, {
              state: {
                prefilledCode: (result as any).code,
                language: (result as any).language,
              },
            })
          }
        >
          Решить снова
        </Button>
        <Button variant="contained" onClick={() => navigate(`/tasks/${result.taskId}`)}>
          К условию задачи
        </Button>
        <Button variant="outlined" onClick={() => navigate('/tasks')}>
          Все задачи
        </Button>
      </Box>
    </Box>
  );
};

export default Results;
