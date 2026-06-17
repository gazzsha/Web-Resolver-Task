import type { Theme, SxProps } from '@mui/material/styles';

/**
 * Shared input styles for auth pages (LoginPage, RegisterPage).
 * Returns an SxProps object that adapts to light vs dark theme mode.
 * Auth pages always render on a dark background (AuthLayout uses a fixed
 * dark gradient), so the styles remain dark-oriented regardless of the
 * global theme mode — mirroring the original static object.
 */
export function authInputSx(_theme: Theme): SxProps {
  return {
    '& .MuiOutlinedInput-root': {
      background: 'rgba(13, 17, 41, 0.5)',
      color: '#f5f7fb',
      fontFamily: '"JetBrains Mono", monospace',
      fontSize: 14,
      transition: 'all 0.2s ease',
      '& fieldset': { borderColor: 'rgba(255,255,255,0.12)' },
      '&:hover fieldset': { borderColor: 'rgba(125,211,252,0.4)' },
      '&.Mui-focused fieldset': {
        borderColor: '#7dd3fc',
        boxShadow: '0 0 0 4px rgba(125,211,252,0.12)',
      },
    },
    '& .MuiInputLabel-root': { color: '#8a92b8', fontSize: 13 },
    '& .MuiInputLabel-root.Mui-focused': { color: '#7dd3fc' },
    '& input:-webkit-autofill': {
      WebkitBoxShadow: '0 0 0 1000px #11163a inset',
      WebkitTextFillColor: '#f5f7fb',
      caretColor: '#f5f7fb',
    },
  } as SxProps;
}
