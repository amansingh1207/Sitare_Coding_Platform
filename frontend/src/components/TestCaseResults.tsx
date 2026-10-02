import type { TestCaseResult } from '../types';

interface TestCaseResultsProps {
  results: TestCaseResult[];
  title?: string;
}

const STATUS_LABELS: Record<string, string> = {
  ACCEPTED: 'Passed',
  WRONG_ANSWER: 'Wrong Answer',
  COMPILATION_ERROR: 'Compilation Error',
  RUNTIME_ERROR: 'Runtime Error',
  TIME_LIMIT_EXCEEDED: 'Time Limit Exceeded',
  MEMORY_LIMIT_EXCEEDED: 'Memory Limit Exceeded',
  INTERNAL_ERROR: 'Internal Error',
};

export function statusLabel(status: string): string {
  return STATUS_LABELS[status] ?? status;
}

export function TestCaseResults({ results, title = 'Test Results' }: TestCaseResultsProps) {
  if (results.length === 0) {
    return <p>No test results.</p>;
  }
  return (
    <div className="test-results">
      <h3>{title}</h3>
      {results.map((result, index) => (
        <div
          key={result.testCaseId ?? index}
          className={`test-case test-case--${result.status.toLowerCase()}`}
        >
          <div className="test-case__header">
            <strong>Test {index + 1}</strong>
            <span className="test-case__status">{statusLabel(result.status)}</span>
            {result.runtimeMs !== null && result.runtimeMs !== undefined && (
              <span className="test-case__meta">{result.runtimeMs} ms</span>
            )}
          </div>
          {result.inputData !== undefined && result.inputData !== null && (
            <div className="test-case__io">
              <div>
                <span>Input:</span>
                <pre>{result.inputData}</pre>
              </div>
            </div>
          )}
          {result.expectedOutput !== undefined && result.expectedOutput !== null && (
            <div className="test-case__io">
              <div>
                <span>Expected:</span>
                <pre>{result.expectedOutput}</pre>
              </div>
              <div>
                <span>Actual:</span>
                <pre>{result.actualOutput ?? '(no output)'}</pre>
              </div>
            </div>
          )}
          {(result.expectedOutput === undefined || result.expectedOutput === null) &&
            (result.inputData === undefined || result.inputData === null) &&
            result.actualOutput !== undefined &&
            result.actualOutput !== null && (
              <div className="test-case__io">
                <div>
                  <span>Output:</span>
                  <pre>{result.actualOutput}</pre>
                </div>
              </div>
            )}
        </div>
      ))}
    </div>
  );
}
