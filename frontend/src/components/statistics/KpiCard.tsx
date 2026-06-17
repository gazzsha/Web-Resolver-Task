import React from 'react';
import { Card, CardContent, Box, Typography, Skeleton, alpha, useTheme } from '@mui/material';

export interface KpiCardProps {
  label: string;
  value: string | number;
  icon: React.ReactNode;
  accentColor: string;
  description: string;
  loading: boolean;
  /** Reserved for future use — declared but not rendered yet */
  trend?: { direction: 'up' | 'down'; value: number };
}

const KpiCard: React.FC<KpiCardProps> = ({
  label,
  value,
  icon,
  accentColor,
  description,
  loading,
}) => {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

  return (
    <Card
      sx={{
        height: '100%',
        transition: 'transform 0.2s, box-shadow 0.2s',
        '&:hover': {
          transform: 'translateY(-3px)',
          boxShadow: isDark
            ? `0 8px 30px ${alpha(accentColor, 0.25)}`
            : `0 8px 24px ${alpha(accentColor, 0.18)}`,
        },
      }}
    >
      <CardContent sx={{ p: 3 }}>
        <Box
          sx={{
            width: 48,
            height: 48,
            borderRadius: 2,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            mb: 2,
            color: accentColor,
            background: alpha(accentColor, isDark ? 0.18 : 0.1),
          }}
        >
          {icon}
        </Box>
        {loading ? (
          <>
            <Skeleton variant="text" width="50%" height={48} />
            <Skeleton variant="text" width="70%" />
          </>
        ) : (
          <>
            <Typography
              variant="h3"
              sx={{
                fontWeight: 700,
                fontSize: '2rem',
                mb: 0.5,
                color: 'text.primary',
                fontFamily: '"JetBrains Mono", monospace',
              }}
            >
              {value}
            </Typography>
            <Typography variant="subtitle2" sx={{ fontWeight: 600, color: 'text.primary', mb: 0.25 }}>
              {label}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {description}
            </Typography>
          </>
        )}
      </CardContent>
    </Card>
  );
};

export default KpiCard;
