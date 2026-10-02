import { useCallback, useEffect, useRef, useState } from 'react';

interface PracticeTimerState {
  /** Total time the timer has been running, excluding paused spans. */
  elapsedMs: number;
  running: boolean;
  start: () => void;
  pause: () => void;
  reset: () => void;
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
        setElapsedMs(bankedMsRef.current + (Date.now() - startedAtRef.current));
      }
    }, 1000);
    return () => window.clearInterval(interval);
  }, [running]);

  const start = useCallback(() => {
    setRunning((wasRunning) => {
      if (!wasRunning) {
        startedAtRef.current = Date.now();
      }
      return true;
    });
  }, []);

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
    setElapsedMs(0);
  }, []);

  // Stop the interval when the view unmounts so a paused-away timer does not
  // keep ticking in the background.
  useEffect(() => () => {
    startedAtRef.current = null;
  }, []);

  return { elapsedMs, running, start, pause, reset };
}
