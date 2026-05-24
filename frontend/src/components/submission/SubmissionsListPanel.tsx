import React from 'react';
import {
  Box,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  CircularProgress,
  IconButton,
  Tooltip,
  alpha,
  useTheme,
  Link as MuiLink,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import { Link as RouterLink } from 'react-router-dom';
import type { SubmissionSummary } from '@/types';
import { StatusChip, LanguageChip } from '@/components/submissions/chips';
import { formatTimeAgo } from '@/utils/dateUtils';

const MAX_ROWS = 20;

interface SubmissionsListPanelProps {
  taskId: string;
  submissions: SubmissionSummary[];
  onSelect: (id: string) => void;
  currentPollingId: string | null;
  onRefresh?: () => void;
}

const SubmissionsListPanel: React.FC<SubmissionsListPanelProps> = ({
  taskId,
  submissions,
  onSelect,
  currentPollingId,
  onRefresh,
}) => {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

  const displayed = submissions.slice(0, MAX_ROWS);
  const totalCount = submissions.length;

  const handleRowKeyDown = (e: React.KeyboardEvent, id: string) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      onSelect(id);
    }
  };

  return (
    <Box
      sx={{
        borderTop: `1px solid ${theme.palette.divider}`,
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        height: '100%',
      }}
    >
      {/* Header */}
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          px: 2,
          py: 0.75,
          flexShrink: 0,
          bgcolor: isDark ? alpha('#ffffff', 0.02) : alpha('#000000', 0.02),
          borderBottom: `1px solid ${theme.palette.divider}`,
        }}
      >
        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
          Мои попытки ({totalCount})
        </Typography>
        {onRefresh && (
          <Tooltip title="Обновить список">
            <IconButton size="small" onClick={onRefresh} aria-label="Обновить список попыток">
              <RefreshIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
      </Box>

      {/* Empty state */}
      {submissions.length === 0 && (
        <Box sx={{ px: 2, py: 3, textAlign: 'center' }}>
          <Typography variant="body2" color="text.secondary">
            Решений по этой задаче пока нет. Отправь первое.
          </Typography>
        </Box>
      )}

      {/* Table */}
      {submissions.length > 0 && (
        <TableContainer sx={{ flex: 1, overflowY: 'auto', overflowX: 'hidden' }}>
          <Table size="small" stickyHeader>
            <TableHead>
              <TableRow>
                <TableCell sx={{ py: 0.75, width: 40 }}>#</TableCell>
                <TableCell sx={{ py: 0.75 }}>Статус</TableCell>
                <TableCell sx={{ py: 0.75 }}>Язык</TableCell>
                <TableCell sx={{ py: 0.75, width: 70 }}>Тесты</TableCell>
                <TableCell sx={{ py: 0.75 }}>Время</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {displayed.map((item, i) => {
                const isPolling = item.id === currentPollingId && item.status === 'PENDING';
                const rowNumber = totalCount - i;

                return (
                  <TableRow
                    key={item.id}
                    role="button"
                    tabIndex={0}
                    onClick={() => onSelect(item.id)}
                    onKeyDown={(e) => handleRowKeyDown(e, item.id)}
                    hover
                    sx={{
                      cursor: 'pointer',
                      '&:last-child td': { border: 0 },
                      '&:focus-visible': {
                        outline: `2px solid ${theme.palette.primary.main}`,
                        outlineOffset: -2,
                      },
                      transition: 'background 0.12s',
                    }}
                  >
                    <TableCell sx={{ py: 0.75 }}>
                      <Typography
                        variant="caption"
                        sx={{ fontFamily: '"JetBrains Mono", monospace', fontWeight: 700 }}
                      >
                        {rowNumber}
                      </Typography>
                    </TableCell>
                    <TableCell
                      sx={{ py: 0.75 }}
                      aria-live={isPolling ? 'polite' : undefined}
                      aria-atomic={isPolling ? 'true' : undefined}
                    >
                      {isPolling ? (
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                          <CircularProgress
                            size={12}
                            aria-label="Проверяется"
                            sx={{ color: 'info.main' }}
                          />
                          <Typography variant="caption" color="info.main" sx={{ fontWeight: 600 }}>
                            Проверяется
                          </Typography>
                        </Box>
                      ) : (
                        <StatusChip status={item.status} />
                      )}
                    </TableCell>
                    <TableCell sx={{ py: 0.75 }}>
                      <LanguageChip language={item.language} />
                    </TableCell>
                    <TableCell sx={{ py: 0.75 }}>
                      {item.passedTests !== null && item.totalTests !== null ? (
                        <Typography
                          variant="caption"
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
                        <Typography variant="caption" color="text.disabled">
                          —
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell sx={{ py: 0.75 }}>
                      <Typography variant="caption" color="text.secondary">
                        {formatTimeAgo(item.createdAt)}
                      </Typography>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Show all link */}
      {totalCount > MAX_ROWS && (
        <Box
          sx={{
            px: 2,
            py: 0.75,
            borderTop: `1px solid ${theme.palette.divider}`,
            flexShrink: 0,
          }}
        >
          <MuiLink
            component={RouterLink}
            to={`/submissions?task=${taskId}`}
            variant="caption"
            sx={{ fontWeight: 600 }}
          >
            Показать все ({totalCount}) →
          </MuiLink>
        </Box>
      )}
    </Box>
  );
};

export default SubmissionsListPanel;
