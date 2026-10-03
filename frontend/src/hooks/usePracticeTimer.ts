import { useCallback, useEffect, useRef, useState } from 'react';

interface PracticeTimerState {
  /** Total time the timer has been running, excluding paused spans. */
  elapsedMs: number;
  running: boolean;
  start: () => void;
  pause: () => void;
  reset: () => void;
  /** Countdown target in ms, or null for plain count-up mode. */
  targetMs: number | null;
  /** Time left in countdown mode, or null in count-up mode. */
  remainingMs: number | null;
  /** True once a countdown reaches zero. */
  finished: boolean;
  /**
   * Set a countdown duration in minutes (clamped to 1-180). Pass null to go
   * back to plain count-up mode. Setting a duration stops the timer and
   * restores the full duration.
   */
  setDuration: (minutes: number | null) => void;
}

/**
 * Optional practice timer, scoped to a single problem view.
 *
 * This tracks how long a student has been practising. It is deliberately kept
 * out of every submission payload so it can never influence judging, and it is
 * never persisted: reloading the page resets it.
 */
export function usePracticeTimer(): PracticeTimerState {
  const [elapsedMs, setElapsedMs] = useState(0);
  const [running, setRunning] = useState(false);
  const [targetMs, setTargetMs] = useState<number | null>(null);
  const [finished, setFinished] = useState(false);
  const startedAtRef = useRef<number | null>(null);
  /**
   * Time banked by earlier run segments.
   *
   * Resuming starts a fresh interval, so without accumulating here the span
   * before the last pause would be dropped from the total.
   */
  const bankedMsRef = useRef(0);

  useEffect(() => {
    if (!running) {
      return undefined;
    }
    const interval = window.setInterval(() => {
      if (startedAtRef.current !== null) {
        const total = bankedMsRef.current + (Date.now() - startedAtRef.current);
        if (targetMs !== null && total >= targetMs) {
          bankedMsRef.current = targetMs;
          startedAtRef.current = null;
          setElapsedMs(targetMs);
          setFinished(true);
          setRunning(false);
        } else {
          setElapsedMs(total);
        }
      }
    }, 1000);
    return () => window.clearInterval(interval);
  }, [running, targetMs]);

  const start = useCallback(() => {
    // Restarting a finished countdown begins a fresh full duration.
    if (finished) {
      bankedMsRef.current = 0;
      setElapsedMs(0);
      setFinished(false);
    }
    setRunning((wasRunning) => {
      if (!wasRunning) {
        startedAtRef.current = Date.now();
      }
      return true;
    });
  }, [finished]);

  const pause = useCallback(() => {
    setRunning((wasRunning) => {
      if (wasRunning && startedAtRef.current !== null) {
        const total = bankedMsRef.current + (Date.now() - startedAtRef.current);
        bankedMsRef.current = total;
        setElapsedMs(total);
        startedAtRef.current = null;
      }
      return false;
    });
  }, []);

  const reset = useCallback(() => {
    startedAtRef.current = null;
    bankedMsRef.current = 0;
    setRunning(false);
    setFinished(false);
    setElapsedMs(0);
  }, []);

  const setDuration = useCallback((minutes: number | null) => {
    startedAtRef.current = null;
    bankedMsRef.current = 0;
    setRunning(false);
    setFinished(false);
    setElapsedMs(0);
    if (minutes === null || !Number.isFinite(minutes) || minutes <= 0) {
      setTargetMs(null);
    } else {
      const clamped = Math.min(180, Math.max(1, Math.floor(minutes)));
      setTargetMs(clamped * 60_000);
    }
  }, []);

  // Stop the interval when the view unmounts so a paused-away timer does not
  // keep ticking in the background.
  useEffect(() => () => {
    startedAtRef.current = null;
  }, []);

  const remainingMs = targetMs === null ? null : Math.max(0, targetMs - elapsedMs);

  return { elapsedMs, running, start, pause, reset, targetMs, remainingMs, finished, setDuration };
}
