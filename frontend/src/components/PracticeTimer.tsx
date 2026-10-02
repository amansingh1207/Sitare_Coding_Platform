import { usePracticeTimer } from '../hooks/usePracticeTimer';
import { formatElapsed } from '../utils/duration';

interface PracticeTimerProps {
  /** Practice time only. Never sent to the judge. */
  problemTitle?: string;
}

export function PracticeTimer({ problemTitle }: PracticeTimerProps) {
  const { elapsedMs, running, start, pause, reset } = usePracticeTimer();

  return (
    <div className="practice-timer">
      <div className="practice-timer__display">
        <span className="practice-timer__label">Practice time</span>
        <span className="practice-timer__value" data-testid="practice-timer-value">
          {formatElapsed(elapsedMs)}
        </span>
      </div>
      <div className="practice-timer__controls">
        {!running && (
          <button type="button" onClick={start}>
            Start
          </button>
        )}
        {running && (
          <button type="button" onClick={pause}>
            Pause
          </button>
        )}
        <button type="button" onClick={reset} disabled={elapsedMs === 0 && !running}>
          Reset
        </button>
      </div>
      <p className="practice-timer__hint">
        Tracks your own practice time only. It is not submitted and does not
        affect judging.
        {problemTitle ? ` Currently viewing: ${problemTitle}.` : ''}
      </p>
    </div>
  );
}
