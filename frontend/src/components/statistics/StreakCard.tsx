import React, { useMemo } from 'react';
import { Box, Typography, Divider, alpha, useTheme } from '@mui/material';
import type { SubmissionSummary } from '@/types';
import { buildStreak } from '@/utils/submissionStats';

const RU_WEEKDAYS_SHORT = ['Вс', 'Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб'];

function isoDate(d: Date): string {
  const dd = String(d.getDate()).padStart(2, '0');
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}

interface StreakCardProps {
  submissions: SubmissionSummary[];
}

const StreakCard: React.FC<StreakCardProps> = ({ submissions }) => {
  const theme = useTheme();

  const { currentStreak, maxStreak, activeDaysSet } = useMemo(
    () => buildStreak(submissions),
    [submissions]
  );

  const last7Days = useMemo(() => {
    const days: { date: Date; key: string; label: string }[] = [];
    const today = new Date();
    for (let i = 6; i >= 0; i--) {
      const d = new Date(today);
      d.setDate(today.getDate() - i);
      days.push({
        date: d,
        key: isoDate(d),
        label: RU_WEEKDAYS_SHORT[d.getDay()],
      });
    }
    return days;
  }, []);

  const ariaLabel = last7Days
    .map((d) => `${d.label}: ${activeDaysSet.has(d.key) ? 'активен' : 'нет активности'}`)
    .join(', ');

  const accentColor = currentStreak > 0 ? '#f97316' : theme.palette.text.disabled;

  return (
    <Box>
      <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1.5 }}>
        Серия дней
      </Typography>

      {/* Big number */}
      <Box sx={{ textAlign: 'center', mb: 1 }}>
        <Typography
          variant="h2"
          component="p"
          sx={{
            fontWeight: 700,
            fontSize: '2.5rem',
            color: accentColor,
            fontFamily: '"JetBrains Mono", monospace',
          }}
        >
          {currentStreak}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          дней подряд
        </Typography>
      </Box>

      <Divider sx={{ my: 1.5 }} />

      <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5, textAlign: 'center' }}>
        Лучший результат: <strong>{maxStreak} дней</strong>
      </Typography>

      {/* 7-day calendar */}
      <Box
        role="img"
        aria-label={`Активность за 7 дней: ${ariaLabel}`}
        sx={{ display: 'flex', gap: 0.75, justifyContent: 'center' }}
      >
        {last7Days.map(({ key, label }) => {
          const isActive = activeDaysSet.has(key);
          return (
            <Box key={key} sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 0.5 }}>
              <Box
                sx={{
                  width: 28,
                  height: 28,
                  borderRadius: '50%',
                  bgcolor: isActive
                    ? 'success.main'
                    : alpha(theme.palette.divider, 0.5),
                  transition: 'background 0.2s',
                }}
              />
              <Typography
                variant="caption"
                sx={{ fontSize: '0.65rem', color: 'text.secondary', lineHeight: 1 }}
              >
                {label}
              </Typography>
            </Box>
          );
        })}
      </Box>
    </Box>
  );
};

export default StreakCard;
