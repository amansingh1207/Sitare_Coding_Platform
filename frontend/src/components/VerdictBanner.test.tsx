// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { cleanup, render as renderComponent, screen } from '@testing-library/react';
import { afterEach } from 'vitest';
import { VerdictBanner } from './VerdictBanner';
import type { TestCaseResult } from '../types';

function result(status: TestCaseResult['status']): TestCaseResult {
  return { testCaseId: 1, status, actualOutput: 'x', runtimeMs: 10, memoryUsedKb: null };
}

describe('VerdictBanner', () => {
  afterEach(() => {
    cleanup();
  });

  it('shows a big Accepted verdict with the pass count', () => {
    renderComponent(
      <VerdictBanner
        status="ACCEPTED"
        results={[result('ACCEPTED'), result('ACCEPTED'), result('ACCEPTED')]}
        runtimeMs={84}
        title="Submission #42"
      />,
    );
    expect(screen.getByText('Accepted')).toBeTruthy();
    expect(screen.getByText('Submission #42')).toBeTruthy();
    expect(screen.getByText(/3 \/ 3 testcases passed/)).toBeTruthy();
    expect(screen.getByText(/84 ms/)).toBeTruthy();
  });

  it('shows Wrong Answer with partial pass count', () => {
    renderComponent(
      <VerdictBanner
        status="WRONG_ANSWER"
        results={[result('ACCEPTED'), result('WRONG_ANSWER')]}
      />,
    );
    expect(screen.getByText('Wrong Answer')).toBeTruthy();
    expect(screen.getByText(/1 \/ 2 testcases passed/)).toBeTruthy();
  });

  it('shows the compilation error detail when provided', () => {
    renderComponent(
      <VerdictBanner
        status="COMPILATION_ERROR"
        results={[]}
        detail="Main.java:3: error: ';' expected"
      />,
    );
    expect(screen.getByText('Compilation Error')).toBeTruthy();
    expect(screen.getByText(/;' expected/)).toBeTruthy();
    expect(screen.getByText(/No testcases evaluated/)).toBeTruthy();
  });
});
