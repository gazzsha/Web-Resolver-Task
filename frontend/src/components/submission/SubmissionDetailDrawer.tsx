import React, { useEffect, useRef } from 'react';
import {
  Box,
  Drawer,
  Typography,
  IconButton,
  Divider,
  Link as MuiLink,
  alpha,
  useTheme,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import ResultsView from '@/components/results/ResultsView';

const DRAWER_HEADER_ID = 'submission-drawer-header';

interface SubmissionDetailDrawerProps {
  submissionId: string | null;
  open: boolean;
  onClose: () => void;
  /** Ref to the element that should receive focus when the drawer closes */
  returnFocusRef?: React.RefObject<HTMLElement>;
}

const SubmissionDetailDrawer: React.FC<SubmissionDetailDrawerProps> = ({
  submissionId,
  open,
  onClose,
  returnFocusRef,
}) => {
  const theme = useTheme();
  const closeBtnRef = useRef<HTMLButtonElement>(null);

  // Return focus to trigger element on close
  useEffect(() => {
    if (!open && returnFocusRef?.current) {
      returnFocusRef.current.focus();
    }
  }, [open, returnFocusRef]);

  return (
    <Drawer
      anchor="right"
      variant="temporary"
      open={open}
      onClose={onClose}
      aria-labelledby={DRAWER_HEADER_ID}
      ModalProps={{ keepMounted: false }}
      PaperProps={{
        sx: {
          width: 480,
          maxWidth: 'calc(100vw - 320px)',
          display: 'flex',
          flexDirection: 'column',
        },
      }}
    >
      {/* Header */}
      <Box
        sx={{
          px: 2.5,
          py: 1.5,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexShrink: 0,
          borderBottom: `1px solid ${theme.palette.divider}`,
          bgcolor: theme.palette.background.paper,
        }}
      >
        <Typography
          id={DRAWER_HEADER_ID}
          variant="h6"
          component="h2"
          sx={{ fontWeight: 700, fontSize: '1rem' }}
        >
          Попытка
        </Typography>
        <IconButton
          ref={closeBtnRef}
          autoFocus
          onClick={onClose}
          aria-label="Закрыть панель результатов"
          size="small"
        >
          <CloseIcon fontSize="small" />
        </IconButton>
      </Box>

      {/* Body — scrollable */}
      <Box
        sx={{
          flex: 1,
          minHeight: 0,
          overflowY: 'auto',
          '&::-webkit-scrollbar': { width: '5px' },
          '&::-webkit-scrollbar-track': { background: 'transparent' },
          '&::-webkit-scrollbar-thumb': {
            background: theme.palette.mode === 'dark'
              ? 'rgba(255,255,255,0.12)'
              : 'rgba(0,0,0,0.14)',
            borderRadius: '3px',
          },
        }}
      >
        {submissionId && (
          <ResultsView
            submissionId={submissionId}
            embedded={true}
          />
        )}
      </Box>

      {/* Footer */}
      {submissionId && (
        <>
          <Divider />
          <Box
            sx={{
              px: 2.5,
              py: 1.25,
              flexShrink: 0,
              bgcolor: theme.palette.mode === 'dark'
                ? alpha('#ffffff', 0.02)
                : alpha('#000000', 0.01),
            }}
          >
            <MuiLink
              href={`/results/${submissionId}`}
              target="_blank"
              rel="noopener noreferrer"
              variant="body2"
              sx={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 0.5,
                fontWeight: 600,
              }}
            >
              Открыть полные результаты
              <OpenInNewIcon sx={{ fontSize: 14 }} />
            </MuiLink>
          </Box>
        </>
      )}
    </Drawer>
  );
};

export default SubmissionDetailDrawer;
