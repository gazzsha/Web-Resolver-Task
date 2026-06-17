import { createTheme, responsiveFontSizes, alpha } from '@mui/material/styles';

// Brand palette — indigo/violet primary, rose accent (matches LoginPage gradient)
const brand = {
  indigo: '#6366f1',
  indigoDark: '#4f52d0',
  indigoDeep: '#3730a3',
  rose: '#ec4899',
  roseLight: '#f472b6',
  cyan: '#22d3ee',
  // neutrals
  surface: {
    light: '#f8f9fc',
    paper: '#ffffff',
    paperDark: '#1a1d2e',
    bgDark: '#0f1117',
  },
};

const typography = {
  fontFamily: `'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif`,
  h1: { fontWeight: 700, fontSize: '2.25rem', letterSpacing: '-0.025em' },
  h2: { fontWeight: 700, fontSize: '1.875rem', letterSpacing: '-0.02em' },
  h3: { fontWeight: 700, fontSize: '1.5rem', letterSpacing: '-0.015em' },
  h4: { fontWeight: 700, fontSize: '1.25rem', letterSpacing: '-0.01em' },
  h5: { fontWeight: 600, fontSize: '1.125rem' },
  h6: { fontWeight: 600, fontSize: '1rem' },
  subtitle1: { fontSize: '1rem', fontWeight: 500, letterSpacing: '-0.005em' },
  subtitle2: { fontSize: '0.875rem', fontWeight: 600, letterSpacing: '0' },
  body1: { fontSize: '0.9375rem', lineHeight: 1.65 },
  body2: { fontSize: '0.8125rem', lineHeight: 1.6 },
  caption: { fontSize: '0.75rem', letterSpacing: '0.01em' },
  button: { textTransform: 'none' as const, fontWeight: 600, letterSpacing: '0' },
  overline: { textTransform: 'uppercase' as const, letterSpacing: '0.1em', fontWeight: 700, fontSize: '0.6875rem' },
};

const shape = { borderRadius: 12 };

// ---------- shared component overrides factory ----------
const buildComponents = (mode: 'light' | 'dark') => {
  const isDark = mode === 'dark';
  return {
    MuiCssBaseline: {
      styleOverrides: `
        @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500;700&display=swap');
        * { box-sizing: border-box; }
        html { scroll-behavior: smooth; }
        ::-webkit-scrollbar { width: 6px; height: 6px; }
        ::-webkit-scrollbar-track { background: transparent; }
        ::-webkit-scrollbar-thumb { background: ${isDark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.14)'}; border-radius: 3px; }
        ::-webkit-scrollbar-thumb:hover { background: ${isDark ? 'rgba(255,255,255,0.22)' : 'rgba(0,0,0,0.24)'}; }
        code, pre, .monaco-editor { font-family: 'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace !important; }
      `,
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: {
          borderRadius: 10,
          padding: '8px 20px',
          fontSize: '0.875rem',
          fontWeight: 600,
          transition: 'background 0.18s, box-shadow 0.18s, transform 0.1s',
          '&:active': { transform: 'scale(0.98)' },
        },
        sizeLarge: { padding: '11px 28px', fontSize: '0.9375rem' },
        sizeSmall: { padding: '5px 14px', fontSize: '0.8125rem' },
        contained: {
          background: `linear-gradient(135deg, ${brand.indigo} 0%, ${brand.indigoDark} 100%)`,
          '&:hover': {
            background: `linear-gradient(135deg, ${brand.indigoDark} 0%, ${brand.indigoDeep} 100%)`,
            boxShadow: `0 6px 20px ${alpha(brand.indigo, 0.42)}`,
          },
        },
        containedSuccess: {
          background: 'linear-gradient(135deg, #22c55e 0%, #16a34a 100%)',
          '&:hover': { boxShadow: '0 6px 20px rgba(34,197,94,0.4)' },
        },
        outlined: {
          borderWidth: '1.5px',
          '&:hover': { borderWidth: '1.5px', background: alpha(brand.indigo, isDark ? 0.12 : 0.06) },
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          borderRadius: 16,
          boxShadow: isDark
            ? '0 1px 3px rgba(0,0,0,0.4), 0 0 0 1px rgba(255,255,255,0.04)'
            : '0 1px 4px rgba(0,0,0,0.07), 0 4px 16px rgba(0,0,0,0.06)',
          backgroundImage: 'none',
          transition: 'box-shadow 0.2s, transform 0.2s',
        },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: { backgroundImage: 'none' },
        rounded: { borderRadius: 16 },
        elevation1: {
          boxShadow: isDark
            ? '0 1px 3px rgba(0,0,0,0.4), 0 0 0 1px rgba(255,255,255,0.04)'
            : '0 1px 3px rgba(0,0,0,0.06), 0 4px 12px rgba(0,0,0,0.05)',
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: { borderRadius: 8, fontWeight: 600, fontSize: '0.78rem' },
        sizeMedium: { height: 28 },
        sizeSmall: { height: 22, fontSize: '0.72rem' },
      },
    },
    MuiTextField: {
      styleOverrides: {
        root: {
          '& .MuiOutlinedInput-root': {
            borderRadius: 10,
            fontSize: '0.9rem',
          },
        },
      },
    },
    MuiInputBase: {
      styleOverrides: {
        root: { fontSize: '0.9rem' },
      },
    },
    MuiAppBar: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
          boxShadow: isDark
            ? '0 1px 0 rgba(255,255,255,0.06)'
            : '0 1px 0 rgba(0,0,0,0.08)',
          backgroundColor: isDark ? brand.surface.paperDark : brand.surface.paper,
          color: isDark ? '#e6edf3' : '#111827',
        },
      },
    },
    MuiDrawer: {
      styleOverrides: {
        paper: {
          backgroundImage: 'none',
          backgroundColor: isDark ? brand.surface.paperDark : brand.surface.paper,
          borderRight: isDark ? '1px solid rgba(255,255,255,0.06)' : '1px solid rgba(0,0,0,0.08)',
        },
      },
    },
    MuiListItemButton: {
      styleOverrides: {
        root: {
          borderRadius: 10,
          margin: '2px 8px',
          padding: '8px 12px',
          transition: 'background 0.15s',
          '&.Mui-selected': {
            backgroundColor: isDark ? alpha(brand.indigo, 0.18) : alpha(brand.indigo, 0.1),
            '&:hover': {
              backgroundColor: isDark ? alpha(brand.indigo, 0.24) : alpha(brand.indigo, 0.14),
            },
          },
          '&:hover': {
            backgroundColor: isDark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.04)',
          },
        },
      },
    },
    MuiTableCell: {
      styleOverrides: {
        root: {
          borderColor: isDark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.07)',
          padding: '10px 16px',
        },
        head: { fontWeight: 700, fontSize: '0.8rem', letterSpacing: '0.04em', textTransform: 'uppercase' as const },
      },
    },
    MuiLinearProgress: {
      styleOverrides: {
        root: { borderRadius: 4, height: 6 },
      },
    },
    MuiAccordion: {
      styleOverrides: {
        root: {
          borderRadius: '12px !important',
          '&:before': { display: 'none' },
          boxShadow: 'none',
          border: isDark ? '1px solid rgba(255,255,255,0.07)' : '1px solid rgba(0,0,0,0.08)',
          '&.Mui-expanded': { margin: 0 },
        },
      },
    },
    MuiAccordionSummary: {
      styleOverrides: {
        root: { padding: '0 16px' },
      },
    },
    MuiTooltip: {
      styleOverrides: {
        tooltip: { fontSize: '0.78rem', borderRadius: 8 },
      },
    },
    MuiAlert: {
      styleOverrides: {
        root: { borderRadius: 10, fontSize: '0.875rem' },
      },
    },
    MuiDivider: {
      styleOverrides: {
        root: { borderColor: isDark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.08)' },
      },
    },
  };
};

// ---------- light theme ----------
const lightTheme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: brand.indigo,
      light: alpha(brand.indigo, 0.7),
      dark: brand.indigoDeep,
      contrastText: '#ffffff',
    },
    secondary: {
      main: brand.rose,
      light: brand.roseLight,
      dark: '#be185d',
      contrastText: '#ffffff',
    },
    success: { main: '#22c55e', light: '#4ade80', dark: '#15803d' },
    error: { main: '#ef4444', light: '#f87171', dark: '#b91c1c' },
    warning: { main: '#f59e0b', light: '#fcd34d', dark: '#b45309' },
    info: { main: '#0ea5e9', light: '#38bdf8', dark: '#0369a1' },
    background: {
      default: '#f0f1f8',
      paper: '#ffffff',
    },
    text: {
      primary: '#111827',
      secondary: '#4b5563',
      disabled: '#5b6472',
    },
    divider: 'rgba(0,0,0,0.08)',
  },
  typography,
  shape,
  components: buildComponents('light') as any,
});

// ---------- dark theme ----------
const darkTheme = createTheme({
  palette: {
    mode: 'dark',
    primary: {
      main: brand.indigo,
      light: '#818cf8',
      dark: brand.indigoDeep,
      contrastText: '#ffffff',
    },
    secondary: {
      main: brand.rose,
      light: brand.roseLight,
      dark: '#be185d',
      contrastText: '#ffffff',
    },
    success: { main: '#22c55e', light: '#4ade80', dark: '#15803d' },
    error: { main: '#ef4444', light: '#f87171', dark: '#b91c1c' },
    warning: { main: '#f59e0b', light: '#fcd34d', dark: '#b45309' },
    info: { main: '#0ea5e9', light: '#38bdf8', dark: '#0369a1' },
    background: {
      default: '#0f1117',
      paper: '#1a1d2e',
    },
    text: {
      primary: '#e6edf3',
      secondary: '#8b949e',
      disabled: '#484f58',
    },
    divider: 'rgba(255,255,255,0.07)',
  },
  typography,
  shape,
  components: buildComponents('dark') as any,
});

export const lightThemeResponsive = responsiveFontSizes(lightTheme);
export const darkThemeResponsive = responsiveFontSizes(darkTheme);

// Export brand colors for use in components
export { brand };
