import { lazy, Suspense, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';

// Monaco is heavy — load only when the code panel renders.
const MonacoEditor = lazy(() => import('@monaco-editor/react'));
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
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Skeleton,
  Divider,
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
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import type { SubmissionResult, AIAnalysisFull } from '@/types';
import { submissionService, aiService } from '@/services/api';
import { brand } from '@/theme/theme';
import PollingView from './PollingView';
import CopyButton from '@/components/common/CopyButton';

const POLL_INTERVAL_MS = 2000;
const MAX_POLL_DURATION_MS = 120_000;

type VerdictColor = 'success' | 'error' | 'warning' | 'default';

const VERDICT_RU: Record<string, string> = {
  OK: 'Верно',
  WRONG_ANSWER: 'Неверный ответ',
  RUNTIME_ERROR: 'Ошибка выполнения',
  COMPILATION_ERROR: 'Ошибка компиляции',
  TIME_LIMIT_EXCEEDED: 'Превышен лимит времени',
  MEMORY_LIMIT_EXCEEDED: 'Превышен лимит памяти',
  PRESENTATION_ERROR: 'Ошибка вывода',
};

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

type SubmittedCode = { code: string; language: string };

const readSubmittedCode = (id: string | undefined, locState: unknown): SubmittedCode | null => {
  if (locState && typeof locState === 'object') {
    const s = locState as Partial<SubmittedCode>;
    if (typeof s.code === 'string' && typeof s.language === 'string') {
      return { code: s.code, language: s.language };
    }
  }
  if (id) {
    try {
      const raw = sessionStorage.getItem(`submission:${id}`);
      if (raw) {
        const parsed = JSON.parse(raw) as Partial<SubmittedCode>;
        if (typeof parsed.code === 'string' && typeof parsed.language === 'string') {
          return { code: parsed.code, language: parsed.language };
        }
      }
    } catch (_e) {
      // storage unavailable
    }
  }
  return null;
};

export interface ResultsViewProps {
  submissionId: string;
  embedded: boolean;
  /** Optional pre-fetched result to skip initial polling round. Not used in current flows. */
  initialResult?: SubmissionResult;
  onResultReceived?: (result: SubmissionResult) => void;
}

const ResultsView: React.FC<ResultsViewProps> = ({
  submissionId,
  embedded,
  initialResult,
  onResultReceived,
}) => {
  const navigate = useNavigate();
  const location = useLocation();
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

  // Code panel is only relevant in standalone mode
  const submitted = useMemo(
    () => (embedded ? null : readSubmittedCode(submissionId, location.state)),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [submissionId, embedded]
  );
  const [codeOpen, setCodeOpen] = useState(true);

  // ── Internal state — fully reset when submissionId changes (resubmit case) ──
  const [result, setResult] = useState<SubmissionResult | null>(initialResult ?? null);
  const [aiFullAnalysis, setAiFullAnalysis] = useState<AIAnalysisFull | null>(null);
  const [aiLoading, setAiLoading] = useState(false);
  const [loading, setLoading] = useState(!initialResult);
  const [timedOut, setTimedOut] = useState(false);
  const [elapsedSec, setElapsedSec] = useState(0);
  const [loadingPhase, setLoadingPhase] = useState<'running' | 'analyzing'>('running');

  // Ref for results heading — focus moves here on result arrival (WCAG)
  const resultHeadingRef = useRef<HTMLHeadingElement>(null);
  // Fired once per submissionId when result arrives
  const resultReceivedFiredRef = useRef(false);

  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const phaseTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const startedAtRef = useRef<number>(Date.now());

  const stopPolling = () => {
    if (intervalRef.current) { clearInterval(intervalRef.current); intervalRef.current = null; }
    if (timerRef.current) { clearInterval(timerRef.current); timerRef.current = null; }
    if (phaseTimerRef.current) { clearTimeout(phaseTimerRef.current); phaseTimerRef.current = null; }
  };

  // Reset all state whenever submissionId changes
  useEffect(() => {
    stopPolling();
    setResult(initialResult ?? null);
    setAiFullAnalysis(null);
    setAiLoading(false);
    setLoading(!initialResult);
    setTimedOut(false);
    setElapsedSec(0);
    setLoadingPhase('running');
    resultReceivedFiredRef.current = false;
    startedAtRef.current = Date.now();

    if (initialResult) return; // Already have result — no need to poll

    // Cancellation guard: protects state writes from leaking into the next submission
    // when user resubmits while a slow getResult/getAnalysis call is still in flight.
    let cancelled = false;
    // Skip a tick if previous poll is still in flight on a slow backend.
    let inFlight = false;

    const poll = async () => {
      if (cancelled || inFlight) return;
      const elapsed = Date.now() - startedAtRef.current;
      if (elapsed >= MAX_POLL_DURATION_MS) {
        stopPolling();
        if (cancelled) return;
        setTimedOut(true);
        setLoading(false);
        return;
      }

      inFlight = true;
      try {
        const data = await submissionService.getResult(submissionId);
        if (cancelled) return;
        if (TERMINAL_STATUSES.has(data.status)) {
          stopPolling();
          setResult(data);
          setLoading(false);

          // Fire callback once
          if (!resultReceivedFiredRef.current) {
            resultReceivedFiredRef.current = true;
            onResultReceived?.(data);
          }

          setAiLoading(true);
          try {
            const full = await aiService.getAnalysis(submissionId);
            if (cancelled) return;
            setAiFullAnalysis(full);
          } finally {
            if (!cancelled) setAiLoading(false);
          }
        }
      } catch (err: unknown) {
        if (cancelled) return;
        const status = (err as { response?: { status?: number } })?.response?.status;
        if (status !== 404) {
          stopPolling();
          setLoading(false);
        }
      } finally {
        inFlight = false;
      }
    };

    timerRef.current = setInterval(() => {
      setElapsedSec(Math.floor((Date.now() - startedAtRef.current) / 1000));
    }, 1000);

    phaseTimerRef.current = setTimeout(() => setLoadingPhase('analyzing'), 7000);
    void poll();
    intervalRef.current = setInterval(() => { void poll(); }, POLL_INTERVAL_MS);

    return () => {
      cancelled = true;
      stopPolling();
    };
    // onResultReceived is intentionally excluded — callers must memoize it or use useCallback
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [submissionId]);

  // WCAG: move focus to results heading when result arrives
  useEffect(() => {
    if (result && resultHeadingRef.current) {
      resultHeadingRef.current.focus();
    }
  }, [result]);

  // ──── Code preview panel (standalone mode only) ────
  const CodePanel = () => {
    if (!submitted || embedded) return null;
    return (
      <Accordion
        expanded={codeOpen}
        onChange={(_e, v) => setCodeOpen(v)}
        sx={{
          mb: 3,
          borderRadius: 2,
          '&:before': { display: 'none' },
          border: 1,
          borderColor: 'divider',
          boxShadow: 'none',
        }}
      >
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}>
            <CodeIcon fontSize="small" color="primary" />
            <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
              Отправленное решение
            </Typography>
            <Chip
              label={submitted.language}
              size="small"
              sx={{ ml: 1, textTransform: 'uppercase', fontWeight: 600, fontSize: '0.7rem' }}
            />
          </Box>
        </AccordionSummary>
        <AccordionDetails sx={{ p: 0 }}>
          <Box sx={{ height: 320, borderTop: 1, borderColor: 'divider' }}>
            <Suspense
              fallback={
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
                  <CircularProgress size={24} aria-label="Загрузка редактора" />
                </Box>
              }
            >
              <MonacoEditor
                height="100%"
                language={submitted.language}
                value={submitted.code}
                theme={isDark ? 'vs-dark' : 'light'}
                options={{
                  readOnly: true,
                  domReadOnly: true,
                  minimap: { enabled: false },
                  fontSize: 13,
                  scrollBeyondLastLine: false,
                  renderLineHighlight: 'none',
                  lineNumbers: 'on',
                  wordWrap: 'on',
                }}
              />
            </Suspense>
          </Box>
        </AccordionDetails>
      </Accordion>
    );
  };

  // ──── Loading (polling) state ────
  if (loading) {
    if (embedded) {
      // Compact skeleton + PollingView inside the panel card
      return (
        <Box sx={{ px: 1.5, py: 1.5 }}>
          <Skeleton variant="rectangular" height={120} sx={{ borderRadius: 1.5, mb: 1.5 }} />
          <PollingView
            elapsedSec={elapsedSec}
            loadingPhase={loadingPhase}
            submissionId={submissionId}
            compact
          />
        </Box>
      );
    }

    return (
      <PollingView
        elapsedSec={elapsedSec}
        loadingPhase={loadingPhase}
        submissionId={submissionId}
      />
    );
  }

  // ──── Timed out ────
  if (timedOut) {
    return (
      <Box sx={embedded ? { p: 1.5 } : { maxWidth: 640, mx: 'auto', mt: 6 }}>
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
      <Box sx={embedded ? { p: 1.5 } : { maxWidth: 640, mx: 'auto', mt: 6 }}>
        <Alert severity="error" sx={{ mb: 2.5 }}>
          Результаты не найдены. Пожалуйста, сначала отправьте решение.
        </Alert>
        {!embedded && (
          <Button variant="contained" onClick={() => navigate('/tasks')}>
            Все задачи
          </Button>
        )}
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
    displayedQuality != null ||
    displayedComplexity != null ||
    displayedExplanation != null ||
    aiFullAnalysis != null;

  // ──── Embedded result content ────
  const resultContent = (
    <>
      <Typography
        ref={resultHeadingRef}
        variant="h6"
        component="h2"
        tabIndex={-1}
        sx={{ fontWeight: 700, mb: 1, outline: 'none' }}
      >
        Результаты
      </Typography>
      <Divider sx={{ mb: 2 }} />

      {/* ──── Overall status (embedded compact) ──── */}
      <Paper
        sx={{
          p: 2,
          mb: 3,
          border: `1px solid ${isDark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.07)'}`,
          boxShadow: 'none',
          background: isDark
            ? alpha(isSuccess ? theme.palette.success.main : theme.palette.error.main, 0.06)
            : alpha(isSuccess ? theme.palette.success.main : theme.palette.error.main, 0.04),
          borderColor: alpha(
            isSuccess ? theme.palette.success.main : theme.palette.error.main,
            isDark ? 0.25 : 0.18
          ),
        }}
      >
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              {isSuccess ? (
                <CheckCircleIcon
                  sx={{ color: theme.palette.success.main, fontSize: 38 }}
                  titleAccess="Тест пройден"
                />
              ) : (
                <ErrorIcon
                  sx={{ color: theme.palette.error.main, fontSize: 38 }}
                  titleAccess="Тест не пройден"
                />
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
          <Grid item xs={12}>
            <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
              {[
                { icon: <TimerIcon fontSize="small" />, value: `${result.totalExecutionTimeMs} мс`, label: 'Время' },
                {
                  icon: <MemoryIcon fontSize="small" />,
                  value: result.memoryUsedKb > 0 ? `${result.memoryUsedKb} КБ` : '—',
                  label: 'Память',
                },
              ].map((m, i) => (
                <Box key={i} sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                  <Box sx={{ color: 'text.secondary' }}>{m.icon}</Box>
                  <Box>
                    <Typography
                      variant="caption"
                      sx={{ fontFamily: '"JetBrains Mono", monospace', fontWeight: 700, display: 'block' }}
                    >
                      {m.value}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.68rem' }}>
                      {m.label}
                    </Typography>
                  </Box>
                </Box>
              ))}
            </Box>
          </Grid>
        </Grid>
      </Paper>

      {/* ──── Test results table (embedded) ──── */}
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
                <TableCell>Детали</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {result.testResults.map((test, index) => {
                const isSkipped = test.status === 'SKIPPED';
                return (
                  <TableRow
                    key={test.testId}
                    sx={{
                      '&:hover': { background: isDark ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.02)' },
                      background: test.status === 'PASSED'
                        ? alpha('#22c55e', isDark ? 0.05 : 0.03)
                        : 'transparent',
                      opacity: isSkipped ? 0.55 : 1,
                    }}
                  >
                    <TableCell>
                      <Typography variant="body2" fontWeight={700}>{index + 1}</Typography>
                    </TableCell>
                    <TableCell>
                      {isSkipped ? (
                        <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic' }}>
                          Не выполнялся
                        </Typography>
                      ) : (
                        <Chip
                          icon={
                            test.status === 'PASSED' ? (
                              <CheckCircleIcon titleAccess="Тест пройден" />
                            ) : (
                              <ErrorIcon titleAccess="Тест не пройден" />
                            )
                          }
                          label={STATUS_RU[test.status] ?? test.status}
                          color={test.status === 'PASSED' ? 'success' : 'error'}
                          size="small"
                        />
                      )}
                    </TableCell>
                    <TableCell>
                      {isSkipped ? (
                        <Typography variant="caption" color="text.secondary">—</Typography>
                      ) : (
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                          {getVerdictIcon(test.verdict)}
                          <Chip
                            label={VERDICT_RU[test.verdict] ?? test.verdict.replace(/_/g, ' ')}
                            color={getVerdictColor(test.verdict)}
                            size="small"
                            variant="outlined"
                          />
                        </Box>
                      )}
                    </TableCell>
                    <TableCell align="right">
                      <Typography variant="caption" sx={{ fontFamily: '"JetBrains Mono", monospace' }}>
                        {isSkipped ? '—' : `${test.executionTimeMs} мс`}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      {isSkipped ? (
                        <Typography variant="caption" color="text.secondary">—</Typography>
                      ) : test.error ? (
                        <Chip label="Ошибка" color="error" size="small" variant="outlined" />
                      ) : test.output ? (
                        <Chip label="Есть вывод" size="small" color="info" variant="outlined" />
                      ) : (
                        <Typography variant="caption" color="text.secondary">—</Typography>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      {/* ──── AI analysis block (embedded) ──── */}
      <Box sx={{ mt: 3 }}>
        {aiLoading ? (
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

            {displayedQuality != null && (
              <Box sx={{ mb: 3 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.75 }}>
                  <Typography variant="body2" fontWeight={600}>Качество кода</Typography>
                  <Typography variant="body2" fontWeight={700} sx={{ fontFamily: '"JetBrains Mono", monospace' }}>
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

            {displayedExplanation && (
              <Paper variant="outlined" sx={{ p: 2, mb: 2, borderRadius: 2, background: isDark ? alpha('#ffffff', 0.03) : alpha('#000000', 0.02) }}>
                <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', lineHeight: 1.7 }}>
                  {displayedExplanation}
                </Typography>
              </Paper>
            )}
          </Paper>
        )}
      </Box>

      {/* Deep-link button */}
      <Box sx={{ mt: 2.5, display: 'flex', justifyContent: 'center' }}>
        <Button
          variant="text"
          size="small"
          endIcon={<OpenInNewIcon fontSize="small" />}
          href={`/results/${submissionId}`}
          target="_blank"
          rel="noopener noreferrer"
        >
          Открыть полные результаты
        </Button>
      </Box>
    </>
  );

  // ──── Embedded wrapper ────
  if (embedded) {
    return (
      <Box
        aria-live="polite"
        aria-label="Панель результатов"
        sx={{
          height: '100%',
          overflowY: 'auto',
          px: 2,
          py: 2,
          '&::-webkit-scrollbar': { width: '5px' },
          '&::-webkit-scrollbar-track': { background: 'transparent' },
          '&::-webkit-scrollbar-thumb': {
            background: isDark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.14)',
            borderRadius: '3px',
          },
        }}
      >
        {resultContent}
      </Box>
    );
  }

  // ──── Standalone 2-column layout ────
  // Split resultContent into left (code + tests) and right (status + AI + nav)
  const leftContent = (
    <>
      <CodePanel />

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
              {result.testResults.map((test, index) => {
                const isSkipped = test.status === 'SKIPPED';
                return (
                  <TableRow
                    key={test.testId}
                    sx={{
                      '&:hover': { background: isDark ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.02)' },
                      background: test.status === 'PASSED'
                        ? alpha('#22c55e', isDark ? 0.05 : 0.03)
                        : 'transparent',
                      opacity: isSkipped ? 0.55 : 1,
                    }}
                  >
                    <TableCell>
                      <Typography variant="body2" fontWeight={700}>{index + 1}</Typography>
                    </TableCell>
                    <TableCell>
                      {isSkipped ? (
                        <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic' }}>
                          Не выполнялся
                        </Typography>
                      ) : (
                        <Chip
                          icon={
                            test.status === 'PASSED' ? (
                              <CheckCircleIcon titleAccess="Тест пройден" />
                            ) : (
                              <ErrorIcon titleAccess="Тест не пройден" />
                            )
                          }
                          label={STATUS_RU[test.status] ?? test.status}
                          color={test.status === 'PASSED' ? 'success' : 'error'}
                          size="small"
                        />
                      )}
                    </TableCell>
                    <TableCell>
                      {isSkipped ? (
                        <Typography variant="caption" color="text.secondary">—</Typography>
                      ) : (
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                          {getVerdictIcon(test.verdict)}
                          <Chip
                            label={VERDICT_RU[test.verdict] ?? test.verdict.replace(/_/g, ' ')}
                            color={getVerdictColor(test.verdict)}
                            size="small"
                            variant="outlined"
                          />
                        </Box>
                      )}
                    </TableCell>
                    <TableCell align="right">
                      <Typography variant="caption" sx={{ fontFamily: '"JetBrains Mono", monospace' }}>
                        {isSkipped ? '—' : `${test.executionTimeMs} мс`}
                      </Typography>
                    </TableCell>
                    <TableCell align="right">
                      <Typography variant="caption" sx={{ fontFamily: '"JetBrains Mono", monospace' }}>
                        {isSkipped || test.memoryUsedKb <= 0 ? '—' : `${test.memoryUsedKb} КБ`}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      {isSkipped ? (
                        <Typography variant="caption" color="text.secondary">—</Typography>
                      ) : test.error ? (
                        <Chip label="Ошибка" color="error" size="small" variant="outlined" />
                      ) : test.output ? (
                        <Chip label="Есть вывод" size="small" color="info" variant="outlined" />
                      ) : (
                        <Typography variant="caption" color="text.secondary">—</Typography>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      {/* ──── Detailed test accordions ──── */}
      <Typography variant="h6" sx={{ mb: 2, fontWeight: 700 }}>
        Подробные результаты
      </Typography>
      {result.testResults.map((test, index) => {
        const isSkipped = test.status === 'SKIPPED';
        const verdictLabel = isSkipped
          ? 'Не выполнялся'
          : (VERDICT_RU[test.verdict] ?? test.verdict.replace(/_/g, ' '));
        return (
          <Accordion
            key={test.testId}
            defaultExpanded={false}
            disabled={isSkipped}
            sx={{ mb: 1.5, opacity: isSkipped ? 0.6 : 1 }}
          >
            <AccordionSummary expandIcon={isSkipped ? null : <ExpandMoreIcon />}>
              <Box
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 2,
                  width: '100%',
                  flexWrap: 'wrap',
                  rowGap: 0.5,
                }}
              >
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  {!isSkipped && getVerdictIcon(test.verdict)}
                  <Typography variant="body2" fontWeight={700}>
                    Тест #{index + 1}
                  </Typography>
                </Box>
                <Chip
                  label={verdictLabel}
                  color={isSkipped ? 'default' : getVerdictColor(test.verdict)}
                  size="small"
                  variant="outlined"
                  sx={isSkipped ? { fontStyle: 'italic' } : undefined}
                />
                {!isSkipped && (
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
                )}
              </Box>
            </AccordionSummary>
            {!isSkipped && (
              <AccordionDetails sx={{ pt: 0 }}>
                <Grid container spacing={2}>
                  {test.output && (
                    <Grid item xs={12}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 1 }}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                          Вывод программы
                        </Typography>
                        <CopyButton
                          text={test.output}
                          label="вывод программы"
                          size="small"
                        />
                      </Box>
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
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.5 }}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                          Ошибка выполнения
                        </Typography>
                        <CopyButton
                          text={test.error}
                          label="ошибку выполнения"
                          size="small"
                        />
                      </Box>
                      <Alert severity="error" sx={{ mt: 0 }}>
                        <Typography
                          variant="body2"
                          sx={{
                            fontFamily: '"JetBrains Mono", monospace',
                            whiteSpace: 'pre-wrap',
                            fontSize: '0.8rem',
                          }}
                        >
                          {test.error}
                        </Typography>
                      </Alert>
                    </Grid>
                  )}
                  {(test.output || test.error) && (
                    <Grid item xs={12}>
                      <CopyButton
                        text={[
                          test.output ? `Вывод программы:\n${test.output}` : '',
                          test.error ? `Ошибка:\n${test.error}` : '',
                        ]
                          .filter(Boolean)
                          .join('\n\n')}
                        label="все данные теста"
                        size="small"
                      />
                      <Typography
                        component="span"
                        variant="caption"
                        color="text.disabled"
                        sx={{ ml: 0.5 }}
                      >
                        Скопировать всё
                      </Typography>
                    </Grid>
                  )}
                </Grid>
              </AccordionDetails>
            )}
          </Accordion>
        );
      })}
    </>
  );

  const rightContent = (
    <>
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
          borderColor: alpha(
            isSuccess ? theme.palette.success.main : theme.palette.error.main,
            isDark ? 0.25 : 0.18
          ),
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
          {isSuccess ? (
            <CheckCircleIcon
              sx={{ color: theme.palette.success.main, fontSize: 52 }}
              titleAccess="Тест пройден"
            />
          ) : (
            <ErrorIcon
              sx={{ color: theme.palette.error.main, fontSize: 52 }}
              titleAccess="Тест не пройден"
            />
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
        <Box sx={{ display: 'flex', gap: 3, flexWrap: 'wrap' }}>
          {[
            { icon: <TimerIcon />, value: `${result.totalExecutionTimeMs} мс`, label: 'Время' },
            {
              icon: <MemoryIcon />,
              value: result.memoryUsedKb > 0 ? `${result.memoryUsedKb} КБ` : '—',
              label: 'Память',
            },
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
      </Paper>

      {/* ──── AI analysis block ──── */}
      <Box sx={{ mb: 3 }}>
        {aiLoading ? (
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
                  color={
                    displayedQuality >= 75 ? 'success' : displayedQuality >= 50 ? 'warning' : 'error'
                  }
                />
              </Box>
            )}

            {displayedComplexity != null && (
              <Box sx={{ mb: 2 }}>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                  Сложность алгоритма
                </Typography>
                <Chip
                  label={COMPLEXITY_RU[displayedComplexity] ?? displayedComplexity}
                  color={
                    displayedComplexity === 'LOW'
                      ? 'success'
                      : displayedComplexity === 'MEDIUM'
                      ? 'info'
                      : displayedComplexity === 'HIGH'
                      ? 'warning'
                      : 'error'
                  }
                  size="small"
                  sx={{ fontWeight: 700 }}
                />
              </Box>
            )}

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
                            <SevIcon
                              color={sevColor as 'error' | 'warning' | 'info' | 'action'}
                              fontSize="small"
                            />
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

      {/* Navigation buttons */}
      <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <Button
          variant="outlined"
          startIcon={<ReplayIcon />}
          onClick={() =>
            navigate(`/submit/${result.taskId}`, {
              state: {
                prefilledCode: (result as unknown as Record<string, unknown>).code,
                language: (result as unknown as Record<string, unknown>).language,
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
    </>
  );

  return (
    <Box sx={{ width: '100%' }}>
      {/* Page heading — full width, focus target */}
      <Typography
        ref={resultHeadingRef}
        variant="h4"
        component="h1"
        tabIndex={-1}
        sx={{ fontWeight: 700, mb: 4, outline: 'none' }}
      >
        Результаты решения
      </Typography>

      {/* Two-column layout: 60% left / 40% right */}
      <Box sx={{ display: 'flex', gap: 3, alignItems: 'flex-start' }}>
        {/* LEFT — code + tests table + detail accordions */}
        <Box sx={{ flex: 3, minWidth: 0 }}>
          {leftContent}
        </Box>

        {/* RIGHT — status + AI + navigation, sticky */}
        <Box
          sx={{
            flex: 2,
            position: 'sticky',
            top: 80,
            maxHeight: 'calc(100vh - 96px)',
            overflowY: 'auto',
            '&::-webkit-scrollbar': { width: '4px' },
            '&::-webkit-scrollbar-track': { background: 'transparent' },
            '&::-webkit-scrollbar-thumb': {
              background: isDark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.14)',
              borderRadius: '2px',
            },
          }}
        >
          {rightContent}
        </Box>
      </Box>
    </Box>
  );
};

export default ResultsView;
