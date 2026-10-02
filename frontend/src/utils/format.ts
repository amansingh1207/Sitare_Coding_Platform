const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pending',
  JUDGING: 'Judging',
  ACCEPTED: 'Accepted',
  WRONG_ANSWER: 'Wrong Answer',
  COMPILATION_ERROR: 'Compilation Error',
  RUNTIME_ERROR: 'Runtime Error',
  TIME_LIMIT_EXCEEDED: 'Time Limit Exceeded',
  MEMORY_LIMIT_EXCEEDED: 'Memory Limit Exceeded',
  INTERNAL_ERROR: 'Internal Error',
};

const LANGUAGE_LABELS: Record<string, string> = {
  JAVA: 'Java',
  CPP: 'C++',
  PYTHON: 'Python',
};

export function statusLabel(status: string): string {
  return STATUS_LABELS[status] ?? status;
}

export function languageLabel(language: string): string {
  return LANGUAGE_LABELS[language] ?? language;
}

/** Runtime in milliseconds, or a dash when the judge did not record one. */
export function formatRuntime(runtimeMs: number | null | undefined): string {
  if (runtimeMs === null || runtimeMs === undefined) {
    return '—';
  }
  if (runtimeMs < 1000) {
    return `${runtimeMs} ms`;
  }
  return `${(runtimeMs / 1000).toFixed(2)} s`;
}

/** Memory in kilobytes, rendered with a human-friendly unit. */
export function formatMemory(memoryKb: number | null | undefined): string {
  if (memoryKb === null || memoryKb === undefined) {
    return '—';
  }
  if (memoryKb < 1024) {
    return `${memoryKb} KB`;
  }
  return `${(memoryKb / 1024).toFixed(1)} MB`;
}

/** Absolute timestamp, rendered in the viewer's locale. */
export function formatTimestamp(iso: string): string {
  const parsed = new Date(iso);
  if (Number.isNaN(parsed.getTime())) {
    return iso;
  }
  return parsed.toLocaleString();
}

/**
 * True for statuses that still describe an in-flight submission.
 * Used to decide whether to offer a "refresh" affordance.
 */
export function isPendingStatus(status: string): boolean {
  return status === 'PENDING' || status === 'JUDGING';
}
