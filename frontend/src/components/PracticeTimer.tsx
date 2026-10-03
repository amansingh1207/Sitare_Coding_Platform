import { useState } from 'react';
import { usePracticeTimer } from '../hooks/usePracticeTimer';
import { formatElapsed } from '../utils/duration';

interface PracticeTimerProps {
  /** Practice time only. Never sent to the judge. */
  problemTitle?: string;
}

export function PracticeTimer({ problemTitle }: PracticeTimerProps) {
  const {
    elapsedMs,
    running,
    start,
    pause,
    reset,
    targetMs,
    remainingMs,
    finished,
    setDuration,
  } = usePracticeTimer();
  const [minutesText, setMinutesText] = useState('');

  const shownMs = targetMs !== null ? (remainingMs ?? 0) : elapsedMs;

  const applyDuration = (): void => {
    const minutes = Number(minutesText);
    if (minutesText.trim() === '' || !Number.isFinite(minutes) || minutes <= 0) {
      setDuration(null);
      setMinutesText('');
      return;
    }
    setDuration(minutes);
  };

  return (
    <div className="practice-timer" title="Practice timer — not submitted, does not affect judging">
      <div className="practice-timer__display">
        <span className="practice-timer__label">Practice time</span>
        <span className="practice-timer__value" data-testid="practice-timer-value">
          {formatElapsed(shownMs)}
        </span>
        {finished && <span className="practice-timer__done">Time&apos;s up!</span>}
      </div>
      <div className="practice-timer__controls">
        <input
          type="number"
          min={1}
          max={180}
          step={1}
          value={minutesText}
          onChange={(e) => setMinutesText(e.target.value)}
          onBlur={applyDuration}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              applyDuration();
            }
          }}
          placeholder="Min"
          aria-label="Countdown duration in minutes"
          title="Set your own countdown in minutes (empty for count-up)"
          className="practice-timer__duration"
        />
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
        <button
          type="button"
          onClick={reset}
          disabled={elapsedMs === 0 && !running && !finished}
        >
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
