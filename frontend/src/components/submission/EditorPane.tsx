import React, { useEffect, useRef } from 'react';
import {
  Box,
  Typography,
  Chip,
  Select,
  MenuItem,
  InputLabel,
  Button,
  CircularProgress,
  Tooltip,
  alpha,
  useTheme,
  useMediaQuery,
} from '@mui/material';
import Editor, { type OnMount } from '@monaco-editor/react';
import SendIcon from '@mui/icons-material/Send';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import CodeIcon from '@mui/icons-material/Code';
import { brand } from '@/theme/theme';

// P0-1: Kotlin исключён из MVP (kotlinc cold start > sandbox-timeout). Sandbox
// и DB-схема поддерживают Kotlin, но фронт его не предлагает в выборе языка.
type Language = 'java' | 'python';
type SubmitState = 'idle' | 'submitting' | 'polling' | 'result';

const LANGUAGE_LABELS: Record<Language, string> = {
  java: 'Java',
  python: 'Python',
};

const FILE_NAMES: Record<Language, string> = {
  java: 'Solution.java',
  python: 'solution.py',
};

const LANGUAGE_COLORS: Record<Language, string> = {
  java: '#f59e0b',
  python: '#3b82f6',
};

interface EditorPaneProps {
  code: string;
  language: Language;
  /** Legacy prop kept for callers that still pass it — maps to submitState==='submitting' */
  submitting?: boolean;
  /** New 4-state machine prop; takes precedence over `submitting` if provided */
  submitState?: SubmitState;
  onCodeChange: (value: string) => void;
  onLanguageChange: (lang: Language) => void;
  onSubmit: () => void;
  onEditorMount?: OnMount;
  /** Navigate to task list — shown as second button in result state */
  onNavigateToTasks?: () => void;
}

const EditorPane: React.FC<EditorPaneProps> = ({
  code,
  language,
  submitting: submittingProp,
  submitState: submitStateProp,
  onCodeChange,
  onLanguageChange,
  onSubmit,
  onEditorMount,
  onNavigateToTasks,
}) => {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));

  // Resolve effective state — prefer 4-state prop, fall back to legacy boolean
  const effectiveState: SubmitState = submitStateProp ?? (submittingProp ? 'submitting' : 'idle');

  const isReadOnly = effectiveState === 'submitting' || effectiveState === 'polling';
  const showOverlay = effectiveState === 'submitting';
  const isProcessing = effectiveState === 'submitting' || effectiveState === 'polling';
  const isResult = effectiveState === 'result';

  const borderColor = isDark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.08)';
  const tabBg = isDark ? '#13152a' : '#f0f1f8';
  const editorBg = isDark ? '#1e1e1e' : '#ffffff';
  const langColor = LANGUAGE_COLORS[language];

  const canSubmit = code.trim().length > 0 && !isProcessing && !isResult;

  // Ref for «Отправить снова» button — autoFocus when state transitions to result
  const resubmitBtnRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (isResult && resubmitBtnRef.current) {
      resubmitBtnRef.current.focus();
    }
  }, [isResult]);

  // Button label and icon per state
  const getSubmitButtonContent = () => {
    if (effectiveState === 'submitting') {
      return {
        label: 'Отправляется...',
        icon: <CircularProgress size={16} color="inherit" />,
      };
    }
    if (effectiveState === 'polling') {
      return {
        label: 'Ожидание результата...',
        icon: <CircularProgress size={16} color="inherit" />,
      };
    }
    if (effectiveState === 'result') {
      return {
        label: 'Отправить снова',
        icon: <SendIcon sx={{ fontSize: 18 }} />,
      };
    }
    return {
      label: 'Отправить решение',
      icon: <SendIcon sx={{ fontSize: 18 }} />,
    };
  };

  const { label: btnLabel, icon: btnIcon } = getSubmitButtonContent();
  const gradientBg = `linear-gradient(135deg, ${brand.indigo} 0%, ${brand.indigoDark} 100%)`;

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        minHeight: 0,
        bgcolor: editorBg,
      }}
    >
      {/* Tab bar */}
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 0,
          px: 0,
          flexShrink: 0,
          bgcolor: tabBg,
          borderBottom: `1px solid ${borderColor}`,
          minHeight: 44,
        }}
      >
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
            px: 2,
            height: 44,
            borderRight: `1px solid ${borderColor}`,
            borderBottom: `2px solid ${brand.indigo}`,
            bgcolor: editorBg,
            flexShrink: 0,
          }}
        >
          <CodeIcon sx={{ fontSize: 14, color: alpha(langColor, 0.9) }} />
          <Typography
            sx={{
              fontFamily: '"JetBrains Mono", monospace',
              fontSize: '0.78rem',
              fontWeight: 600,
              color: 'text.primary',
              whiteSpace: 'nowrap',
            }}
          >
            {FILE_NAMES[language]}
          </Typography>
        </Box>

        <Box sx={{ flex: 1 }} />

        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1.5,
            px: 2,
          }}
        >
          {!isMobile && (
            <Tooltip title="Отправить решение (⌘+Enter / Ctrl+Enter)">
              <Chip
                label={navigator.platform.includes('Mac') ? '⌘+Enter' : 'Ctrl+Enter'}
                size="small"
                sx={{
                  fontFamily: '"JetBrains Mono", monospace',
                  fontSize: '0.7rem',
                  fontWeight: 500,
                  height: 22,
                  background: isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)',
                  color: 'text.disabled',
                  border: `1px solid ${borderColor}`,
                  cursor: 'default',
                }}
              />
            </Tooltip>
          )}

          <InputLabel id="lang-select-label" sx={{ display: 'none' }}>Язык</InputLabel>
          <Select
            value={language}
            onChange={(e) => onLanguageChange(e.target.value as Language)}
            size="small"
            variant="outlined"
            labelId="lang-select-label"
            inputProps={{ 'aria-label': 'Язык программирования' }}
            sx={{
              minWidth: 100,
              fontSize: '0.8125rem',
              fontWeight: 600,
              height: 30,
              '& .MuiOutlinedInput-notchedOutline': {
                borderColor: borderColor,
                borderRadius: '8px',
              },
              '&:hover .MuiOutlinedInput-notchedOutline': {
                borderColor: alpha(brand.indigo, 0.5),
              },
              '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                borderColor: brand.indigo,
              },
              '& .MuiSelect-select': {
                py: '4px',
                display: 'flex',
                alignItems: 'center',
                gap: 0.75,
              },
            }}
            renderValue={(val) => (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                <Box
                  sx={{
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    bgcolor: LANGUAGE_COLORS[val as Language],
                    flexShrink: 0,
                  }}
                />
                <Typography sx={{ fontSize: '0.8125rem', fontWeight: 600 }}>
                  {LANGUAGE_LABELS[val as Language]}
                </Typography>
              </Box>
            )}
          >
            {(Object.keys(LANGUAGE_LABELS) as Language[]).map((lang) => (
              <MenuItem key={lang} value={lang}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Box
                    sx={{
                      width: 8,
                      height: 8,
                      borderRadius: '50%',
                      bgcolor: LANGUAGE_COLORS[lang],
                      flexShrink: 0,
                    }}
                  />
                  <Typography sx={{ fontWeight: 600, fontSize: '0.875rem' }}>
                    {LANGUAGE_LABELS[lang]}
                  </Typography>
                </Box>
              </MenuItem>
            ))}
          </Select>
        </Box>
      </Box>

      {/* Monaco editor — grows to fill available space; overlay on submitting */}
      <Box
        sx={{
          flex: 1,
          minHeight: 0,
          position: 'relative',
          ...(isMobile && { minHeight: '60vh' }),
        }}
        {...(isReadOnly
          ? { 'aria-label': 'Редактор кода, только чтение', 'aria-readonly': 'true' }
          : {})}
      >
        <Editor
          height="100%"
          language={language}
          value={code}
          onChange={(v) => onCodeChange(v ?? '')}
          theme={isDark ? 'vs-dark' : 'light'}
          onMount={onEditorMount}
          options={{
            readOnly: isReadOnly,
            domReadOnly: isReadOnly,
            minimap: { enabled: !isMobile && true },
            fontSize: isMobile ? 13 : 14,
            fontFamily: '"JetBrains Mono", "Fira Code", "Cascadia Code", monospace',
            fontLigatures: true,
            automaticLayout: true,
            scrollBeyondLastLine: false,
            padding: { top: 16, bottom: 16 },
            wordWrap: 'on',
            lineNumbers: 'on',
            renderLineHighlight: 'line',
            smoothScrolling: true,
            cursorBlinking: 'smooth',
            cursorSmoothCaretAnimation: 'on',
            tabSize: 4,
            bracketPairColorization: { enabled: true },
            renderWhitespace: 'selection',
            scrollbar: {
              verticalScrollbarSize: 6,
              horizontalScrollbarSize: 6,
            },
            overviewRulerLanes: 0,
          }}
        />

        {/* Submitting overlay */}
        {showOverlay && (
          <Box
            sx={{
              position: 'absolute',
              inset: 0,
              bgcolor: isDark ? 'rgba(0,0,0,0.18)' : 'rgba(255,255,255,0.5)',
              pointerEvents: 'none',
              zIndex: 1,
            }}
          />
        )}
      </Box>

      {/* Footer */}
      <Box
        sx={{
          flexShrink: 0,
          px: 2,
          py: 1.5,
          borderTop: `1px solid ${borderColor}`,
          bgcolor: isDark ? theme.palette.background.paper : '#fafafa',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'flex-end',
          gap: 1.5,
        }}
      >
        {/* Hint when editor is empty in idle state */}
        {effectiveState === 'idle' && !code.trim() && (
          <Typography variant="caption" sx={{ color: 'text.disabled' }}>
            Напишите код перед отправкой
          </Typography>
        )}

        {/* In result state: show two buttons */}
        {isResult ? (
          <>
            <Button
              ref={resubmitBtnRef}
              variant="contained"
              size="large"
              onClick={onSubmit}
              startIcon={<SendIcon sx={{ fontSize: 18 }} />}
              sx={{
                flex: 1,
                fontWeight: 700,
                fontSize: '0.9rem',
                background: gradientBg,
                boxShadow: `0 4px 16px ${alpha(brand.indigo, 0.4)}`,
                '&:hover': {
                  background: `linear-gradient(135deg, ${brand.indigoDark} 0%, ${brand.indigoDeep} 100%)`,
                  boxShadow: `0 6px 24px ${alpha(brand.indigo, 0.55)}`,
                },
                transition: 'all 0.2s',
              }}
            >
              Отправить снова
            </Button>
            {onNavigateToTasks && (
              <Button
                variant="outlined"
                size="large"
                onClick={onNavigateToTasks}
                startIcon={<ArrowBackIcon />}
                sx={{
                  flex: '0 0 auto',
                  fontWeight: 700,
                  fontSize: '0.9rem',
                }}
              >
                Все задачи
              </Button>
            )}
          </>
        ) : (
          // Idle / submitting / polling: single button
          <Button
            variant="contained"
            size="large"
            disabled={!canSubmit || isProcessing}
            onClick={isProcessing ? undefined : onSubmit}
            startIcon={btnIcon}
            sx={{
              minWidth: 200,
              fontWeight: 700,
              fontSize: '0.9rem',
              background: canSubmit && !isProcessing ? gradientBg : undefined,
              boxShadow: canSubmit && !isProcessing ? `0 4px 16px ${alpha(brand.indigo, 0.4)}` : undefined,
              '&:hover': {
                background: `linear-gradient(135deg, ${brand.indigoDark} 0%, ${brand.indigoDeep} 100%)`,
                boxShadow: `0 6px 24px ${alpha(brand.indigo, 0.55)}`,
              },
              '&:disabled': {
                background: isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)',
                color: isDark ? 'rgba(255,255,255,0.3)' : 'rgba(0,0,0,0.3)',
                boxShadow: 'none',
              },
              transition: 'all 0.2s',
            }}
          >
            {btnLabel}
          </Button>
        )}
      </Box>
    </Box>
  );
};

export default EditorPane;
