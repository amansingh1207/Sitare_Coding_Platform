import { describe, expect, it } from 'vitest';
import {
  formatMemory,
  formatRuntime,
  formatTimestamp,
  isPendingStatus,
  languageLabel,
  statusLabel,
} from './format';

describe('statusLabel', () => {
  it('uses readable labels for every submission status', () => {
    expect(statusLabel('ACCEPTED')).toBe('Accepted');
    expect(statusLabel('WRONG_ANSWER')).toBe('Wrong Answer');
    expect(statusLabel('COMPILATION_ERROR')).toBe('Compilation Error');
    expect(statusLabel('RUNTIME_ERROR')).toBe('Runtime Error');
    expect(statusLabel('TIME_LIMIT_EXCEEDED')).toBe('Time Limit Exceeded');
    expect(statusLabel('MEMORY_LIMIT_EXCEEDED')).toBe('Memory Limit Exceeded');
    expect(statusLabel('INTERNAL_ERROR')).toBe('Internal Error');
  });

  it('falls back to the raw value for unknown statuses', () => {
    expect(statusLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW');
  });
});

describe('languageLabel', () => {
  it('maps language codes to display names', () => {
    expect(languageLabel('JAVA')).toBe('Java');
    expect(languageLabel('CPP')).toBe('C++');
    expect(languageLabel('PYTHON')).toBe('Python');
  });

  it('falls back to the raw code', () => {
    expect(languageLabel('RUST')).toBe('RUST');
  });
});

describe('formatRuntime', () => {
  it('shows milliseconds below one second', () => {
    expect(formatRuntime(0)).toBe('0 ms');
    expect(formatRuntime(150)).toBe('150 ms');
    expect(formatRuntime(999)).toBe('999 ms');
  });

  it('shows seconds at one second and above', () => {
    expect(formatRuntime(1000)).toBe('1.00 s');
    expect(formatRuntime(2500)).toBe('2.50 s');
  });

  it('shows a dash when no runtime was recorded', () => {
    expect(formatRuntime(null)).toBe('—');
    expect(formatRuntime(undefined)).toBe('—');
  });
});

describe('formatMemory', () => {
  it('shows kilobytes below 1024 KB', () => {
    expect(formatMemory(512)).toBe('512 KB');
    expect(formatMemory(1023)).toBe('1023 KB');
  });

  it('shows megabytes at 1024 KB and above', () => {
    expect(formatMemory(1024)).toBe('1.0 MB');
    expect(formatMemory(25600)).toBe('25.0 MB');
  });

  it('shows a dash when no memory was recorded', () => {
    expect(formatMemory(null)).toBe('—');
  });
});

describe('formatTimestamp', () => {
  it('renders a parseable ISO timestamp', () => {
    const result = formatTimestamp('2026-10-02T05:00:00Z');
    expect(result).not.toBe('2026-10-02T05:00:00Z');
    expect(result.length).toBeGreaterThan(0);
  });

  it('returns the input unchanged when it cannot be parsed', () => {
    expect(formatTimestamp('not-a-date')).toBe('not-a-date');
  });
});

describe('isPendingStatus', () => {
  it('is true only for in-flight submissions', () => {
    expect(isPendingStatus('PENDING')).toBe(true);
    expect(isPendingStatus('JUDGING')).toBe(true);
  });

  it('is false for every terminal status', () => {
    expect(isPendingStatus('ACCEPTED')).toBe(false);
    expect(isPendingStatus('WRONG_ANSWER')).toBe(false);
    expect(isPendingStatus('TIME_LIMIT_EXCEEDED')).toBe(false);
  });
});
