import React from 'react';
import {
  Box,
  Typography,
  LinearProgress,
  Alert,
  Button,
  CircularProgress,
} from '@mui/material';
import CodeIcon from '@mui/icons-material/Code';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';

interface PollingViewProps {
  elapsedSec: number;
  loadingPhase: 'running' | 'analyzing';
  submissionId: string;
  /** When true renders compact variant for the embedded right panel */
  compact?: boolean;
}

const PollingView: React.FC<PollingViewProps> = ({
  elapsedSec,
  loadingPhase,
  submissionId,
  compact = false,
}) => {
  const phaseLabel =
    loadingPhase === 'running' ? 'Запускаем тесты...' : 'Анализируем код с AI...';

  if (compact) {
    // Compact variant — used inside the embedded right panel
    return (
      <Box sx={{ pt: 1.5 }}>
        {/* Indeterminate progress bar at the top */}
        <LinearProgress
          role="progressbar"
          aria-valuemin={0}
          aria-valuemax={120}
          aria-valuenow={elapsedSec}
          aria-label="Ожидание результата"
          sx={{ mb: 2, borderRadius: 1 }}
        />

        <Box
          sx={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            textAlign: 'center',
            py: 2,
            gap: 1.5,
          }}
        >
          <CircularProgress size={40} thickness={3.5} />
          <Typography variant="h6" sx={{ fontWeight: 700 }}>
            Проверяем решение...
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {phaseLabel}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Прошло: {elapsedSec} сек.
          </Typography>

          {elapsedSec >= 30 && (
            <Alert severity="info" sx={{ textAlign: 'left', width: '100%', mt: 0.5 }}>
              Docker-образ может загружаться дольше обычного
            </Alert>
          )}

          <Button
            variant="text"
            size="small"
            endIcon={<OpenInNewIcon fontSize="small" />}
            href={`/results/${submissionId}`}
            target="_blank"
            rel="noopener noreferrer"
            sx={{ mt: 0.5 }}
          >
            Открыть в новой вкладке
          </Button>
        </Box>
      </Box>
    );
  }

  // Full-page variant — used by standalone Results page
  return (
    <Box sx={{ maxWidth: 640, mx: 'auto', mt: 8 }}>
      <Box
        role="status"
        aria-live="polite"
        aria-label="Ожидание результатов"
        sx={{
          border: 1,
          borderColor: 'divider',
          borderRadius: 2,
          overflow: 'hidden',
        }}
      >
        <LinearProgress
          role="progressbar"
          aria-valuemin={0}
          aria-valuemax={120}
          aria-valuenow={elapsedSec}
          aria-label="Ожидание результата"
        />
        <Box sx={{ textAlign: 'center', py: 6, px: 4 }}>
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
          <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>
            Прошло: {elapsedSec} сек.
          </Typography>
          {elapsedSec >= 30 && (
            <Alert severity="info" sx={{ mt: 1, textAlign: 'left' }}>
              Docker-образ может загружаться дольше обычного
            </Alert>
          )}
        </Box>
      </Box>
    </Box>
  );
};

export default PollingView;
