import type { SubmissionSummary } from '@/types';

// ── lang / status helpers (moved from StatisticsPage) ──────────────────────

export function buildLangData(submissions: SubmissionSummary[]): { name: string; value: number }[] {
  const counts: Record<string, number> = {};
  for (const s of submissions) {
    counts[s.language] = (counts[s.language] ?? 0) + 1;
  }
  return Object.entries(counts).map(([name, value]) => ({ name, value }));
}

const STATUS_LABELS: Record<SubmissionSummary['status'], string> = {
  SUCCESS: 'Принято',
  PARTIAL_SUCCESS: 'Частично',
  FAILED: 'Не принято',
  ERROR: 'Ошибка',
  PENDING: 'В ожидании',
  PROCESSING: 'Выполняется',
};

export function buildStatusData(submissions: SubmissionSummary[]): { name: string; value: number }[] {
  const relevant: Array<SubmissionSummary['status']> = ['SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'ERROR'];
  const counts: Record<string, number> = {};
  for (const s of submissions) {
    if (relevant.includes(s.status)) {
      const label = STATUS_LABELS[s.status];
      counts[label] = (counts[label] ?? 0) + 1;
    }
  }
  return Object.entries(counts).map(([name, value]) => ({ name, value }));
}

// ── activity helpers ────────────────────────────────────────────────────────

/** Returns ISO date string «yyyy-MM-dd» for a given Date */
function isoDate(d: Date): string {
  const dd = String(d.getDate()).padStart(2, '0');
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}

/**
 * Group submissions by ISO date, fill missing days with 0.
 * @param days number of past days to include (default 30), ending today.
 */
export function buildActivityData(
  submissions: SubmissionSummary[],
  days = 30
): { date: string; value: number }[] {
  const counts: Record<string, number> = {};
  for (const s of submissions) {
    const key = isoDate(new Date(s.createdAt));
    counts[key] = (counts[key] ?? 0) + 1;
  }

  const result: { date: string; value: number }[] = [];
  const today = new Date();
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(today);
    d.setDate(today.getDate() - i);
    const key = isoDate(d);
    result.push({ date: key, value: counts[key] ?? 0 });
  }
  return result;
}

const RU_WEEKDAYS = ['Вс', 'Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб'];

/**
 * Group submissions by last 7 days.
 */
export function buildWeeklyData(
  submissions: SubmissionSummary[]
): { day: string; value: number }[] {
  const counts: Record<string, number> = {};
  for (const s of submissions) {
    const key = isoDate(new Date(s.createdAt));
    counts[key] = (counts[key] ?? 0) + 1;
  }

  const result: { day: string; value: number }[] = [];
  const today = new Date();
  for (let i = 6; i >= 0; i--) {
    const d = new Date(today);
    d.setDate(today.getDate() - i);
    const key = isoDate(d);
    result.push({ day: RU_WEEKDAYS[d.getDay()], value: counts[key] ?? 0 });
  }
  return result;
}

// ── streak helpers ──────────────────────────────────────────────────────────

/**
 * Compute current and max consecutive active days from submissions.
 * An «active day» = any day with at least 1 submission.
 */
export function buildStreak(submissions: SubmissionSummary[]): {
  currentStreak: number;
  maxStreak: number;
  activeDaysSet: Set<string>;
} {
  const activeDaysSet = new Set<string>();
  for (const s of submissions) {
    activeDaysSet.add(isoDate(new Date(s.createdAt)));
  }

  if (activeDaysSet.size === 0) {
    return { currentStreak: 0, maxStreak: 0, activeDaysSet };
  }

  // Sort all unique active days ascending
  const sorted = Array.from(activeDaysSet).sort();

  // Compute max streak
  let maxStreak = 1;
  let streak = 1;
  for (let i = 1; i < sorted.length; i++) {
    const prev = new Date(sorted[i - 1]);
    const curr = new Date(sorted[i]);
    const diff = Math.round((curr.getTime() - prev.getTime()) / 86_400_000);
    if (diff === 1) {
      streak++;
      if (streak > maxStreak) maxStreak = streak;
    } else {
      streak = 1;
    }
  }

  // Compute current streak (from today backwards)
  const today = isoDate(new Date());
  const yesterday = isoDate(new Date(Date.now() - 86_400_000));
  let currentStreak = 0;

  // Current streak must start from today or yesterday (grace period)
  if (activeDaysSet.has(today) || activeDaysSet.has(yesterday)) {
    const startDay = activeDaysSet.has(today) ? today : yesterday;
    const startDate = new Date(startDay);
    currentStreak = 1;
    let checkDate = new Date(startDate);
    checkDate.setDate(checkDate.getDate() - 1);
    while (activeDaysSet.has(isoDate(checkDate))) {
      currentStreak++;
      checkDate.setDate(checkDate.getDate() - 1);
    }
  }

  return { currentStreak, maxStreak, activeDaysSet };
}
