/**
 * Formats an elapsed duration as HH:MM:SS.
 *
 * Used by the practice timer only. This is a *practice* concept and is
 * deliberately unrelated to the execution time limit enforced by the judge.
 */
export function formatElapsed(elapsedMs: number): string {
  if (!Number.isFinite(elapsedMs) || elapsedMs < 0) {
    elapsedMs = 0;
  }
  const totalSeconds = Math.floor(elapsedMs / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return [hours, minutes, seconds].map(padTwo).join(':');
}

function padTwo(value: number): string {
  return value < 10 ? `0${value}` : String(value);
}
