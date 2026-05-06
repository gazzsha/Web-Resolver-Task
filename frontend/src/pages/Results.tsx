import { useEffect, useState } from 'react';
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
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import TimerIcon from '@mui/icons-material/Timer';
import MemoryIcon from '@mui/icons-material/Memory';
import CodeIcon from '@mui/icons-material/Code';
import type { SubmissionResult } from '@/types';
import { submissionService } from '@/services/api';

const Results = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [result, setResult] = useState<SubmissionResult | null>(null);
  const [pollCount, setPollCount] = useState(0);
  const MAX_POLLS = 30; // Poll for up to 30 seconds

  useEffect(() => {
    const fetchResults = async () => {
      try {
        const resultData = await submissionService.getResult(id || '');
        setResult(resultData);
        setLoading(false);
      } catch (error: any) {
        if (error.response?.status === 404 && pollCount < MAX_POLLS) {
          // Results not ready yet, poll again
          setPollCount(prev => prev + 1);
        } else {
          console.error('Failed to fetch results:', error);
          setLoading(false);
        }
      }
    };

    if (id) {
      fetchResults();

      // Poll every 1 second if results not ready
      const interval = setInterval(() => {
        if (!result && pollCount < MAX_POLLS) {
          fetchResults();
        } else {
          clearInterval(interval);
        }
      }, 1000);

      return () => clearInterval(interval);
    }
  }, [id, pollCount, result]);

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
              Running Tests...
            </Typography>
            <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
              Executing test cases. This may take a few seconds...
            </Typography>
            <LinearProgress sx={{ mb: 2 }} />
            <Box sx={{ display: 'flex', justifyContent: 'center', gap: 1 }}>
              <CircularProgress size={16} />
              <Typography variant="body2" color="text.secondary">
                Checking results: {pollCount + 1} / {MAX_POLLS}
              </Typography>
            </Box>
          </CardContent>
        </Card>
      </Box>
    );
  }

  if (!result) {
    return (
      <Box sx={{ maxWidth: 800, mx: 'auto', mt: 4 }}>
        <Alert severity="error" sx={{ mb: 2 }}>
          No results found. Please submit your solution first.
        </Alert>
        <Button variant="contained" onClick={() => navigate('/tasks')}>
          Back to Tasks
        </Button>
      </Box>
    );
  }

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'SUCCESS':
      case 'PASSED':
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

  const getVerdictIcon = (verdict: string) => {
    switch (verdict) {
      case 'OK':
      case 'ACCEPTED':
        return <CheckCircleIcon color="success" />;
      case 'WRONG_ANSWER':
        return <ErrorIcon color="error" />;
      default:
        return <ErrorIcon color="warning" />;
    }
  };

  const passedCount = result.testResults.filter(r => r.status === 'PASSED').length;
  const totalCount = result.testResults.length;
  const successRate = Math.round((passedCount / totalCount) * 100);

  return (
    <Box sx={{ maxWidth: 1200, mx: 'auto', mt: 4 }}>
      <Typography variant="h4" gutterBottom sx={{ mb: 4, fontWeight: 'bold' }}>
        Submission Results
      </Typography>

      {/* Overall Status */}
      <Paper sx={{ p: 3, mb: 3, bgcolor: getStatusColor(result.status) + '.lighter' }}>
        <Grid container spacing={3} alignItems="center">
          <Grid item xs={12} md={6}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              {getStatusColor(result.status) === 'success' ? (
                <CheckCircleIcon color="success" sx={{ fontSize: 48 }} />
              ) : (
                <ErrorIcon color="error" sx={{ fontSize: 48 }} />
              )}
              <div>
                <Typography variant="h5" color={getStatusColor(result.status)} sx={{ fontWeight: 'bold' }}>
                  {result.status.replace('_', ' ')}
                </Typography>
                <Typography variant="body1" color="text.secondary">
                  {passedCount} / {totalCount} tests passed ({successRate}%)
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
                  <Typography variant="caption" color="text.secondary">Total Time</Typography>
                </Box>
              </Grid>
              <Grid item xs={4}>
                <Box sx={{ textAlign: 'center' }}>
                  <MemoryIcon sx={{ fontSize: 24, mb: 1, color: 'text.secondary' }} />
                  <Typography variant="h6" fontFamily="monospace">{result.memoryUsedKb}KB</Typography>
                  <Typography variant="caption" color="text.secondary">Memory</Typography>
                </Box>
              </Grid>
              <Grid item xs={4}>
                <Box sx={{ textAlign: 'center' }}>
                  <Typography variant="h6" fontFamily="monospace">{result.passedTests}/{result.totalTests}</Typography>
                  <Typography variant="caption" color="text.secondary">Tests</Typography>
                </Box>
              </Grid>
            </Grid>
          </Grid>
        </Grid>
      </Paper>

      {/* Test Results Table */}
      <Paper sx={{ mb: 3 }}>
        <Typography variant="h6" sx={{ p: 2, borderBottom: 1, borderColor: 'divider', fontWeight: 'bold' }}>
          Test Cases Summary
        </Typography>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 'bold' }}>#</TableCell>
                <TableCell sx={{ fontWeight: 'bold' }}>Status</TableCell>
                <TableCell sx={{ fontWeight: 'bold' }}>Verdict</TableCell>
                <TableCell align="right" sx={{ fontWeight: 'bold' }}>Time</TableCell>
                <TableCell align="right" sx={{ fontWeight: 'bold' }}>Memory</TableCell>
                <TableCell sx={{ fontWeight: 'bold' }}>Details</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {result.testResults.map((test, index) => (
                <TableRow
                  key={test.testId}
                  sx={{
                    bgcolor:
                      test.status === 'PASSED'
                        ? 'success.lighter'
                        : test.status === 'FAILED'
                        ? 'error.lighter'
                        : 'warning.lighter',
                  }}
                >
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
                      <Typography variant="body2" fontWeight="medium">{test.verdict.replace('_', ' ')}</Typography>
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
                      <Chip label="Error" color="error" size="small" />
                    ) : test.output ? (
                      <Chip label="Has Output" size="small" color="info" />
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
        Detailed Results
      </Typography>
      {result.testResults.map((test, index) => (
        <Accordion key={test.testId} sx={{ mb: 2 }} defaultExpanded={test.status !== 'PASSED'}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, width: '100%' }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 100 }}>
                {test.status === 'PASSED' ? (
                  <CheckCircleIcon color="success" />
                ) : (
                  <ErrorIcon color={test.status === 'FAILED' ? 'error' : 'warning'} />
                )}
                <Typography variant="body1" fontWeight="medium">
                  Test #{index + 1}
                </Typography>
              </Box>
              <Chip
                label={test.verdict.replace('_', ' ')}
                color={test.status === 'PASSED' ? 'success' : test.status === 'FAILED' ? 'error' : 'warning'}
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
                    Output
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
                    Error Details
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

      <Box sx={{ mt: 3, display: 'flex', gap: 2 }}>
        <Button variant="contained" onClick={() => navigate(`/tasks/${id}`)}>
          Back to Task
        </Button>
        <Button variant="outlined" onClick={() => navigate('/tasks')}>
          All Tasks
        </Button>
        {result.status !== 'SUCCESS' && (
          <Button variant="outlined" color="warning" onClick={() => navigate(`/tasks/${id}`)}>
            Try Again
          </Button>
        )}
      </Box>
    </Box>
  );
};

export default Results;
