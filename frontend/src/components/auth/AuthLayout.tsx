import React from 'react';
import { Box, Typography } from '@mui/material';

const animations = `
  @keyframes wrt-mesh-1 { 0%,100% { transform: translate(-10%, -10%) scale(1); } 50% { transform: translate(10%, 5%) scale(1.15); } }
  @keyframes wrt-mesh-2 { 0%,100% { transform: translate(20%, 30%) scale(1.05); } 50% { transform: translate(-15%, 10%) scale(0.95); } }
  @keyframes wrt-mesh-3 { 0%,100% { transform: translate(40%, -10%) scale(1); } 50% { transform: translate(20%, 30%) scale(1.2); } }
  @keyframes wrt-fade-up { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: translateY(0); } }
  @keyframes wrt-cursor-blink { 0%,100% { opacity: 1; } 50% { opacity: 0; } }
  .wrt-cursor { display: inline-block; width: 2px; height: 0.9em; background: #7dd3fc; vertical-align: text-bottom; margin-left: 1px; animation: wrt-cursor-blink 1.1s step-start infinite; }
`;

interface AuthLayoutProps {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
}

const AuthLayout: React.FC<AuthLayoutProps> = ({ title, subtitle, children, footer }) => {
  return (
    <Box
      sx={{
        minHeight: '100vh',
        width: '100%',
        flex: 1,
        position: 'relative',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #0a0e27 0%, #11163a 50%, #0a0e27 100%)',
        overflow: 'hidden',
        px: 2,
        py: 4,
      }}
    >
      <style>{animations}</style>

      {/* gradient-mesh blobs (background) */}
      <Box sx={{
        position: 'absolute', top: 0, left: 0, width: '60vmax', height: '60vmax',
        background: 'radial-gradient(circle, rgba(99,102,241,0.45) 0%, transparent 60%)',
        filter: 'blur(80px)', animation: 'wrt-mesh-1 22s ease-in-out infinite', pointerEvents: 'none',
      }} />
      <Box sx={{
        position: 'absolute', bottom: 0, right: 0, width: '55vmax', height: '55vmax',
        background: 'radial-gradient(circle, rgba(236,72,153,0.35) 0%, transparent 60%)',
        filter: 'blur(80px)', animation: 'wrt-mesh-2 26s ease-in-out infinite', pointerEvents: 'none',
      }} />
      <Box sx={{
        position: 'absolute', top: '50%', left: '50%', width: '40vmax', height: '40vmax',
        background: 'radial-gradient(circle, rgba(56,189,248,0.30) 0%, transparent 60%)',
        filter: 'blur(80px)', animation: 'wrt-mesh-3 30s ease-in-out infinite', pointerEvents: 'none',
        transform: 'translate(-50%, -50%)',
      }} />

      {/* subtle dot grid */}
      <Box sx={{
        position: 'absolute', inset: 0, pointerEvents: 'none', opacity: 0.4,
        backgroundImage: 'radial-gradient(rgba(255,255,255,0.06) 1px, transparent 1px)',
        backgroundSize: '24px 24px',
      }} />

      {/* center card */}
      <Box
        sx={{
          position: 'relative',
          width: '100%',
          maxWidth: 460,
          zIndex: 1,
          background: 'rgba(17, 22, 58, 0.55)',
          backdropFilter: 'blur(24px) saturate(180%)',
          WebkitBackdropFilter: 'blur(24px) saturate(180%)',
          border: '1px solid rgba(255,255,255,0.08)',
          borderRadius: '20px',
          boxShadow: '0 24px 80px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255,255,255,0.06)',
          p: { xs: 3.5, sm: 5 },
          animation: 'wrt-fade-up 0.5s ease-out both',
        }}
      >
        {/* logo */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1.5 }}>
          <Box
            sx={{
              width: 38, height: 38, borderRadius: '10px',
              background: 'linear-gradient(135deg, #6366f1 0%, #ec4899 100%)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: '0 8px 24px rgba(99,102,241,0.4)',
            }}
          >
            <Typography sx={{ fontFamily: '"JetBrains Mono", "Fira Code", monospace', fontSize: 18, fontWeight: 700, color: '#fff' }}>
              {'</>'}
            </Typography>
          </Box>
          <Box>
            <Typography sx={{ fontFamily: '"JetBrains Mono", "Fira Code", monospace', fontWeight: 700, fontSize: 16, color: '#e6edf3', letterSpacing: '-0.5px', lineHeight: 1 }}>
              Web-Resolver-Task
            </Typography>
            <Typography sx={{ fontFamily: '"JetBrains Mono", "Fira Code", monospace', fontSize: 10, color: '#7dd3fc', letterSpacing: '0.1em', textTransform: 'uppercase', mt: 0.25 }}>
              auto-judge · ai feedback
            </Typography>
          </Box>
        </Box>

        {/* title */}
        <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#f5f7fb', mb: 0.5, letterSpacing: '-0.5px' }}>
          {title}
          <span className="wrt-cursor" />
        </Typography>
        {subtitle && (
          <Typography sx={{ fontSize: 14, color: '#9ca3c4', mb: 3 }}>{subtitle}</Typography>
        )}
        {!subtitle && <Box sx={{ mb: 2 }} />}

        {/* form */}
        {children}

        {/* footer */}
        {footer && (
          <Box sx={{ mt: 3, pt: 2.5, borderTop: '1px solid rgba(255,255,255,0.08)', textAlign: 'center' }}>
            {footer}
          </Box>
        )}
      </Box>

      {/* tagline at bottom-left */}
      <Box
        sx={{
          position: 'absolute',
          bottom: 16,
          left: 24,
          display: { xs: 'none', md: 'block' },
          zIndex: 0,
          pointerEvents: 'none',
        }}
      >
        <Typography sx={{ fontFamily: '"JetBrains Mono", monospace', fontSize: 11, color: 'rgba(255,255,255,0.35)' }}>
          $ wrt --start
        </Typography>
      </Box>
    </Box>
  );
};

export default AuthLayout;
