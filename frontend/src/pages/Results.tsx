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
import type { SubmissionResult, AIAnalysisFull } from '@/types';
import { submissionService, aiService } from '@/services/api';

const POLL_INTERVAL_MS = 2000;
const MAX_POLL_DURATION_MS = 120_000;

type VerdictColor = 'success' | 'error' | 'warning' | 'default';

const getVerdictColor = (verdict: string): VerdictColor => {
  switch (verdict) {
    case 'OK':
      return 'success';
    case 'WRONG_ANSWER':
    case 'RUNTIME_ERROR':
    case 'COMPILATION_ERROR':
      return 'error';
    case 'TIME_LIMIT_EXCEEDED':
    case 'MEMORY_LIMIT_EXCEEDED':
    case 'PRESENTATION_ERROR':
      return 'warning';
    default:
      return 'default';
  }
};

const getVerdictIcon = (verdict: string) => {
  switch (verdict) {
    case 'OK':
      return <CheckCircleIcon color="success" />;
    case 'WRONG_ANSWER':
      return <CancelIcon color="error" />;
    case 'TIME_LIMIT_EXCEEDED':
      return <ScheduleIcon color="warning" />;
    case 'MEMORY_LIMIT_EXCEEDED':
      return <MemoryIcon color="warning" />;
    case 'RUNTIME_ERROR':
      return <BugReportIcon color="error" />;
    case 'COMPILATION_ERROR':
      return <CodeIcon color="error" />;
    case 'PRESENTATION_ERROR':
      return <WarningIcon color="warning" />;
    default:
      return <ErrorIcon color="warning" />;
  }
};

const getStatusColor = (status: string): VerdictColor => {
  switch (status) {
    case 'SUCCESS':
      return 'success';
    case 'PARTIAL_SUCCESS':
      return 'warning';
    case 'FAILED':
    case 'ERROR':
      return 'error';
    default:
      return 'default';
  }
};

const TERMINAL_STATUSES = new Set(['SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'ERROR']);

const Results = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [result, setResult] = useState<SubmissionResult | null>(null);
  const [aiFullAnalysis, setAiFullAnalysis] = useState<AIAnalysisFull | null>(null);
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

          const full = await aiService.getAnalysis(id);
          setAiFullAnalysis(full);
        }
      } catch (err: unknown) {
        const status = (err as any)?.response?.status;
        if (status !== 404) {
          stopPolling();
          setLoading(false);
        }
        // 404 → keep polling
      }
    };

    // Elapsed-seconds ticker
    timerRef.current = setInterval(() => {
      setElapsedSec(Math.floor((Date.now() - startedAtRef.current) / 1000));
    }, 1000);

    // Switch loading phase text after 7s
    phaseTimerRef.current = setTimeout(() => setLoadingPhase('analyzing'), 7000);

    // First call immediately, then every POLL_INTERVAL_MS
    poll();
    intervalRef.current = setInterval(poll, POLL_INTERVAL_MS);

    return () => stopPolling();
  }, [id]);

  if (loading) {
    return (
      <Box sx={{ maxWidth: 800, mx: 'auto', mt: 8 }}>
        <Card sx={{ textAlign: 'center', p: 4 }}>
          <CardContent>
            <Box sx={{ mb: 3, position: 'relative', display: 'inline-block' }}>
              <CircularProgress size={80} thickness={4} />
              <Box
                sx={{
                  position: 'absolute',
                  top: '50%',
                  left: '50%',
                  transform: 'translate(-50%, -50%)',
                }}
              >
                <CodeIcon sx={{ fontSize: 32, color: 'primary.main' }} />
              </Box>
            </Box>
            <Typography variant="h5" gutterBottom sx={{ fontWeight: 'bold' }}>
              {loadingPhase === 'running' ? 'Запускаем тесты...' : 'Анализируем код...'}
            </Typography>
            <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
              Выполнение тестов займёт несколько секунд.
            </Typography>
            <LinearProgress sx={{ mb: 2 }} />
            <Box sx={{ display: 'flex', justifyContent: 'center', gap: 1, mb: 1 }}>
              <CircularProgress size={16} />
              <Typography variant="body2" color="text.secondary">
                Прошло: {elapsedSec}s
              </Typography>
            </Box>
            {elapsedSec >= 30 && (
              <Typography variant="caption" color="text.secondary">
                Если задача занимает более 30 секунд, идёт первая загрузка docker-образа
              </Typography>
            )}
          </CardContent>
        </Card>
      </Box>
    );
  }

  if (timedOut) {
    return (
      <Box sx={{ maxWidth: 800, mx: 'auto', mt: 4 }}>
        <Alert severity="warning" sx={{ mb: 2 }}>
          Результат не получен за 120 секунд. Возможно, sandbox перегружен. Попробуйте обновить страницу через минуту.
        </Alert>
        <Button variant="contained" onClick={() => window.location.reload()}>
          Refresh
        </Button>
      </Box>
    );
  }

  if (!result) {
    return (
      <Box sx={{ maxWidth: 800, mx: 'auto', mt: 4 }}>
        <Alert severity="error" sx={{ mb: 2 }}>
          Результаты не найдены. Пожалуйста, сначала отправьте решение.
        </Alert>
        <Button variant="contained" onClick={() => navigate('/tasks')}>
          Все задачи
        </Button>
      </Box>
    );
  }

  const passedCount = result.testResults.filter(r => r.status === 'PASSED').length;
  const totalCount = result.testResults.length;
  const successRate = totalCount > 0 ? Math.round((passedCount / totalCount) * 100) : 0;
  const statusColor = getStatusColor(result.status);

  // Determine displayed AI analysis source: prefer full, fallback to summary
  const summaryAI = result.aiAnalysis;
  const displayedQuality = aiFullAnalysis?.codeQuality ?? summaryAI?.codeQuality;
  const displayedComplexity = aiFullAnalysis?.complexity ?? summaryAI?.complexity;
  const displayedExplanation = aiFullAnalysis?.explanation ?? summaryAI?.explanation;
  const hasAIBlock = displayedQuality != null || displayedComplexity != null || displayedExplanation != null || aiFullAnalysis != null;

  return (
    <Box sx={{ maxWidth: 1200, mx: 'auto', mt: 4 }}>
      <Typography variant="h4" gutterBottom sx={{ mb: 4, fontWeight: 'bold' }}>
        Результаты отправки
      </Typography>

      {/* Overall Status */}
      <Paper sx={{ p: 3, mb: 3 }}>
        <Grid container spacing={3} alignItems="center">
          <Grid item xs={12} md={6}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              {statusColor === 'success' ? (
                <CheckCircleIcon color="success" sx={{ fontSize: 48 }} />
              ) : (
                <ErrorIcon color="error" sx={{ fontSize: 48 }} />
              )}
              <div>
                <Typography variant="h5" color={statusColor + '.main'} sx={{ fontWeight: 'bold' }}>
                  {result.status.replace(/_/g, ' ')}
                </Typography>
                <Typography variant="body1" color="text.secondary">
                  {passedCount} / {totalCount} тестов пройдено ({successRate}%)
                </Typography>
              </div>
            </Box>
          </Grid>
          <Grid item xs={12} md={6}>
            <Grid container spacing={2}>
              <Grid item xs={4}>
                <Box sx={{ textAlign: 'center' }}>
                  <TimerIcon sx={{ fontSize: 24, mb: 1, color: 'text.secondary' }} />
                  <Typography variant="h6" fontFamily="monospace">{result.totalExecutionTimeMs}ms</Typography>
                  <Typography variant="caption" color="text.secondary">Время</Typography>
                </Box>
              </Grid>
              <Grid item xs={4}>
                <Box sx={{ textAlign: 'center' }}>
                  <MemoryIcon sx={{ fontSize: 24, mb: 1, color: 'text.secondary' }} />
                  <Typography variant="h6" fontFamily="monospace">{result.memoryUsedKb}KB</Typography>
                  <Typography variant="caption" color="text.secondary">Память</Typography>
                </Box>
              </Grid>
              <Grid item xs={4}>
                <Box sx={{ textAlign: 'center' }}>
                  <Typography variant="h6" fontFamily="monospace">{result.passedTests}/{result.totalTests}</Typography>
                  <Typography variant="caption" color="text.secondary">Тесты</Typography>
                </Box>
              </Grid>
            </Grid>
          </Grid>
        </Grid>
      </Paper>

      {/* Test Results Table */}
      <Paper sx={{ mb: 3 }}>
        <Typography variant="h6" sx={{ p: 2, borderBottom: 1, borderColor: 'divider', fontWeight: 'bold' }}>
          Сводка тест-кейсов
        </Typography>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 'bold' }}>#</TableCell>
                <TableCell sx={{ fontWeight: 'bold' }}>Статус</TableCell>
                <TableCell sx={{ fontWeight: 'bold' }}>Вердикт</TableCell>
                <TableCell align="right" sx={{ fontWeight: 'bold' }}>Время</TableCell>
                <TableCell align="right" sx={{ fontWeight: 'bold' }}>Память</TableCell>
                <TableCell sx={{ fontWeight: 'bold' }}>Детали</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {result.testResults.map((test, index) => (
                <TableRow key={test.testId}>
                  <TableCell>
                    <Typography variant="body2" fontWeight="bold">{index + 1}</Typography>
                  </TableCell>
                  <TableCell>
                    <Chip
                      icon={test.status === 'PASSED' ? <CheckCircleIcon /> : <ErrorIcon />}
                      label={test.status}
                      color={test.status === 'PASSED' ? 'success' : 'error'}
                      size="small"
                      variant="filled"
                    />
                  </TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      {getVerdictIcon(test.verdict)}
                      <Chip
                        label={test.verdict.replace(/_/g, ' ')}
                        color={getVerdictColor(test.verdict)}
                        size="small"
                        variant="outlined"
                      />
                    </Box>
                  </TableCell>
                  <TableCell align="right">
                    <Typography variant="body2" fontFamily="monospace">
                      {test.executionTimeMs}ms
                    </Typography>
                  </TableCell>
                  <TableCell align="right">
                    <Typography variant="body2" fontFamily="monospace">
                      {test.memoryUsedKb}KB
                    </Typography>
                  </TableCell>
                  <TableCell>
                    {test.error ? (
                      <Chip label="Ошибка" color="error" size="small" />
                    ) : test.output ? (
                      <Chip label="Есть вывод" size="small" color="info" />
                    ) : (
                      <Typography variant="body2" color="text.secondary">-</Typography>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      {/* Detailed Test Results */}
      <Typography variant="h6" sx={{ mb: 2, fontWeight: 'bold' }}>
        Подробные результаты
      </Typography>
      {result.testResults.map((test, index) => (
        <Accordion key={test.testId} sx={{ mb: 2 }} defaultExpanded={test.status !== 'PASSED'}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, width: '100%' }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 100 }}>
                {getVerdictIcon(test.verdict)}
                <Typography variant="body1" fontWeight="medium">
                  Тест #{index + 1}
                </Typography>
              </Box>
              <Chip
                label={test.verdict.replace(/_/g, ' ')}
                color={getVerdictColor(test.verdict)}
                size="small"
                variant="outlined"
              />
              <Box sx={{ display: 'flex', gap: 2, ml: 'auto' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  <TimerIcon fontSize="small" color="action" />
                  <Typography variant="body2" fontFamily="monospace">{test.executionTimeMs}ms</Typography>
                </Box>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  <MemoryIcon fontSize="small" color="action" />
                  <Typography variant="body2" fontFamily="monospace">{test.memoryUsedKb}KB</Typography>
                </Box>
              </Box>
            </Box>
          </AccordionSummary>
          <AccordionDetails>
            <Grid container spacing={2}>
              {test.output && (
                <Grid item xs={12}>
                  <Typography variant="subtitle2" gutterBottom sx={{ fontWeight: 'bold', mb: 1 }}>
                    Вывод
                  </Typography>
                  <Paper
                    variant="outlined"
                    sx={{
                      p: 2,
                      bgcolor: 'grey.50',
                      fontFamily: 'monospace',
                      fontSize: '0.875rem',
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-all',
                    }}
                  >
                    {test.output}
                  </Paper>
                </Grid>
              )}
              {test.error && (
                <Grid item xs={12}>
                  <Typography variant="subtitle2" gutterBottom sx={{ fontWeight: 'bold', mb: 1 }}>
                    Детали ошибки
                  </Typography>
                  <Alert severity="error" sx={{ mt: 1 }}>
                    <Typography variant="body2" fontFamily="monospace" sx={{ whiteSpace: 'pre-wrap' }}>
                      {test.error}
                    </Typography>
                  </Alert>
                </Grid>
              )}
            </Grid>
          </AccordionDetails>
        </Accordion>
      ))}

      {/* AI Analysis Block */}
      {!hasAIBlock && (
        <Paper sx={{ p: 3, mb: 3 }} variant="outlined">
          <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 1 }}>
            AI-анализ кода
          </Typography>
          <Alert severity="info" icon={<InfoIcon />}>
            AI-анализ для этого решения недоступен. Возможные причины: не задан
            <code style={{ margin: '0 4px' }}>GIGACHAT_AUTH_KEY</code>в окружении,
            истёк токен или временно недоступен внешний сервис. Базовая проверка
            тестов прошла нормально — посмотри таблицу выше.
          </Alert>
        </Paper>
      )}
      {hasAIBlock && (
        <Paper sx={{ p: 3, mb: 3 }}>
          <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 2 }}>
            AI-анализ кода
          </Typography>

          {displayedQuality != null && (
            <Box sx={{ mb: 3 }}>
              <Typography variant="body2" sx={{ mb: 0.5 }}>
                Качество кода: {displayedQuality}/100
              </Typography>
              <LinearProgress
                variant="determinate"
                value={displayedQuality}
                color={displayedQuality >= 75 ? 'success' : displayedQuality >= 50 ? 'warning' : 'error'}
              />
            </Box>
          )}

          {displayedComplexity != null && (
            <Chip
              label={`Сложность: ${displayedComplexity}`}
              color={
                displayedComplexity === 'LOW'
                  ? 'success'
                  : displayedComplexity === 'MEDIUM'
                  ? 'info'
                  : displayedComplexity === 'HIGH'
                  ? 'warning'
                  : 'error'
              }
              sx={{ mb: 2 }}
            />
          )}

          {displayedExplanation && (
            <Typography variant="body2" sx={{ mt: 2, whiteSpace: 'pre-wrap' }}>
              {displayedExplanation}
            </Typography>
          )}

          {/* Issues from full analysis */}
          {aiFullAnalysis && aiFullAnalysis.issues.length > 0 && (
            <Accordion sx={{ mt: 2 }}>
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Typography variant="subtitle1" fontWeight="medium">
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
                      <ListItem key={idx} alignItems="flex-start" disableGutters>
                        <ListItemIcon sx={{ minWidth: 36, mt: 0.5 }}>
                          <SevIcon color={sevColor as any} fontSize="small" />
                        </ListItemIcon>
                        <ListItemText
                          primary={`[${issue.type}] ${issue.message}${issue.line != null ? ` — Строка ${issue.line}` : ''}`}
                          secondary={issue.suggestion}
                        />
                      </ListItem>
                    );
                  })}
                </List>
              </AccordionDetails>
            </Accordion>
          )}

          {/* Recommendations from full analysis */}
          {aiFullAnalysis && aiFullAnalysis.recommendations.length > 0 && (
            <Box sx={{ mt: 2 }}>
              <Typography variant="subtitle1" fontWeight="medium" sx={{ mb: 1 }}>
                Рекомендации
              </Typography>
              <List dense>
                {aiFullAnalysis.recommendations.map((rec, idx) => (
                  <ListItem key={idx} disableGutters>
                    <ListItemIcon sx={{ minWidth: 28 }}>
                      <FiberManualRecordIcon sx={{ fontSize: 10, color: 'text.secondary' }} />
                    </ListItemIcon>
                    <ListItemText primary={rec} />
                  </ListItem>
                ))}
              </List>
            </Box>
          )}
        </Paper>
      )}

      {/* Navigation Buttons */}
      <Box sx={{ mt: 3, display: 'flex', gap: 2 }}>
        <Button
          variant="outlined"
          color="warning"
          onClick={() => navigate(`/submit/${result.taskId}`, {
            state: {
              prefilledCode: (result as { code?: string }).code,
              language: (result as { language?: 'java' | 'kotlin' | 'python' }).language,
            },
          })}
        >
          Resubmit
        </Button>
        <Button variant="contained" onClick={() => navigate(`/tasks/${result.taskId}`)}>
          К заданию
        </Button>
        <Button variant="outlined" onClick={() => navigate('/tasks')}>
          Все задачи
        </Button>
      </Box>
    </Box>
  );
};

export default Results;
