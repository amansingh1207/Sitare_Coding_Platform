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

  useEffect(() => {
    if (!running) {
      return undefined;
    }
    const interval = window.setInterval(() => {
      if (startedAtRef.current !== null) {
        setElapsedMs(Date.now() - startedAtRef.current);
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
        setElapsedMs(Date.now() - startedAtRef.current);
        startedAtRef.current = null;
      }
      return false;
    });
  }, []);

  const reset = useCallback(() => {
    startedAtRef.current = null;
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
