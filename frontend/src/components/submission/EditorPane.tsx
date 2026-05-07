import React from 'react';
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
import CodeIcon from '@mui/icons-material/Code';
import { brand } from '@/theme/theme';

type Language = 'java' | 'kotlin' | 'python';

const LANGUAGE_LABELS: Record<Language, string> = {
  java: 'Java',
  kotlin: 'Kotlin',
  python: 'Python',
};

const FILE_NAMES: Record<Language, string> = {
  java: 'Solution.java',
  kotlin: 'Solution.kt',
  python: 'solution.py',
};

const LANGUAGE_COLORS: Record<Language, string> = {
  java: '#f59e0b',
  kotlin: '#a855f7',
  python: '#3b82f6',
};

interface EditorPaneProps {
  code: string;
  language: Language;
  submitting: boolean;
  onCodeChange: (value: string) => void;
  onLanguageChange: (lang: Language) => void;
  onSubmit: () => void;
  onEditorMount?: OnMount;
}

const EditorPane: React.FC<EditorPaneProps> = ({
  code,
  language,
  submitting,
  onCodeChange,
  onLanguageChange,
  onSubmit,
  onEditorMount,
}) => {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));

  const borderColor = isDark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.08)';
  const tabBg = isDark ? '#13152a' : '#f0f1f8';
  const editorBg = isDark ? '#1e1e1e' : '#ffffff';
  const langColor = LANGUAGE_COLORS[language];

  const canSubmit = code.trim().length > 0 && !submitting;

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
        {/* Active "tab" for the file */}
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

        {/* Spacer */}
        <Box sx={{ flex: 1 }} />

        {/* Language selector + keyboard hint */}
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1.5,
            px: 2,
          }}
        >
          {/* Keyboard shortcut hint — only on desktop */}
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

      {/* Monaco editor — grows to fill all available space */}
      <Box
        sx={{
          flex: 1,
          minHeight: 0,
          // On mobile give it a floor so code is usable
          ...(isMobile && { minHeight: '60vh' }),
        }}
      >
        <Editor
          height="100%"
          language={language}
          value={code}
          onChange={(v) => onCodeChange(v ?? '')}
          theme={isDark ? 'vs-dark' : 'light'}
          onMount={onEditorMount}
          options={{
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
      </Box>

      {/* Footer — submit button */}
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
        {/* Disabled hint */}
        {!canSubmit && !submitting && code.trim().length === 0 && (
          <Typography variant="caption" sx={{ color: 'text.disabled' }}>
            Напишите код перед отправкой
          </Typography>
        )}

        <Button
          variant="contained"
          size="large"
          disabled={!canSubmit}
          onClick={onSubmit}
          startIcon={
            submitting ? (
              <CircularProgress size={16} color="inherit" />
            ) : (
              <SendIcon sx={{ fontSize: 18 }} />
            )
          }
          sx={{
            minWidth: 200,
            fontWeight: 700,
            fontSize: '0.9rem',
            background: canSubmit
              ? `linear-gradient(135deg, ${brand.indigo} 0%, ${brand.indigoDark} 100%)`
              : undefined,
            boxShadow: canSubmit
              ? `0 4px 16px ${alpha(brand.indigo, 0.4)}`
              : undefined,
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
          {submitting ? 'Отправляется...' : 'Отправить решение'}
        </Button>
      </Box>
    </Box>
  );
};

export default EditorPane;
