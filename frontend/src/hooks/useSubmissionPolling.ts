import { useEffect, useRef, useState } from 'react';
import { submissionService } from '@/services/api';
import type { SubmissionSummary } from '@/types';

const POLL_INTERVAL_MS = 2000;
const MAX_POLL_DURATION_MS = 120_000;

const TERMINAL_STATUSES = new Set<SubmissionSummary['status']>([
  'SUCCESS',
  'PARTIAL_SUCCESS',
  'FAILED',
  'ERROR',
]);

export interface SubmissionPollingResult {
  status: SubmissionSummary['status'] | null;
  passedTests: number | null;
  totalTests: number | null;
  timedOut: boolean;
}

/**
 * Polls a submission by id until it reaches a terminal status or times out.
 * Returns null values when submissionId is null (no interval started).
 * Does NOT call aiService — ResultsView (inside Drawer) handles AI.
 */
export function useSubmissionPolling(
  submissionId: string | null
): SubmissionPollingResult {
  const [status, setStatus] = useState<SubmissionSummary['status'] | null>(null);
  const [passedTests, setPassedTests] = useState<number | null>(null);
  const [totalTests, setTotalTests] = useState<number | null>(null);
  const [timedOut, setTimedOut] = useState(false);

  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const startedAtRef = useRef<number>(Date.now());

  useEffect(() => {
    // Reset on new submission id
    setStatus(null);
    setPassedTests(null);
    setTotalTests(null);
    setTimedOut(false);

    if (!submissionId) return;

    let cancelled = false;
    let inFlight = false;

    startedAtRef.current = Date.now();

    const clearPolling = () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };

    const poll = async () => {
      if (cancelled || inFlight) return;

      const elapsed = Date.now() - startedAtRef.current;
      if (elapsed >= MAX_POLL_DURATION_MS) {
        clearPolling();
        if (!cancelled) setTimedOut(true);
        return;
      }

      inFlight = true;
      try {
        const data = await submissionService.getResult(submissionId);
        if (cancelled) return;

        const s = data.status as SubmissionSummary['status'];
        setStatus(s);
        setPassedTests(data.passedTests ?? null);
        setTotalTests(data.totalTests ?? null);

        if (TERMINAL_STATUSES.has(s)) {
          clearPolling();
        }
      } catch (err: unknown) {
        if (cancelled) return;
        const httpStatus = (err as { response?: { status?: number } })?.response?.status;
        // 404 is expected while still processing — keep polling.
        // Any other error is fatal — surface as `timedOut` so consumer can exit polling state
        // (otherwise UI would hang on PENDING forever).
        if (httpStatus !== 404) {
          clearPolling();
          setTimedOut(true);
        }
      } finally {
        inFlight = false;
      }
    };

    void poll();
    intervalRef.current = setInterval(() => { void poll(); }, POLL_INTERVAL_MS);

    return () => {
      cancelled = true;
      clearPolling();
    };
  }, [submissionId]);

  return { status, passedTests, totalTests, timedOut };
}
