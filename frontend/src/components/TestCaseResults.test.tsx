// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { TestCaseResults, statusLabel } from './TestCaseResults';
import type { TestCaseResult } from '../types';

function result(overrides: Partial<TestCaseResult> = {}): TestCaseResult {
  return {
    testCaseId: 1,
    status: 'ACCEPTED',
    actualOutput: '7',
    expectedOutput: '7',
    runtimeMs: 106,
    memoryUsedKb: 140612,
    ...overrides,
  };
}

describe('statusLabel', () => {
  it('maps known statuses to student-facing wording', () => {
    expect(statusLabel('ACCEPTED')).toBe('Passed');
    expect(statusLabel('WRONG_ANSWER')).toBe('Wrong Answer');
    expect(statusLabel('COMPILATION_ERROR')).toBe('Compilation Error');
    expect(statusLabel('RUNTIME_ERROR')).toBe('Runtime Error');
    expect(statusLabel('TIME_LIMIT_EXCEEDED')).toBe('Time Limit Exceeded');
    expect(statusLabel('MEMORY_LIMIT_EXCEEDED')).toBe('Memory Limit Exceeded');
    expect(statusLabel('INTERNAL_ERROR')).toBe('Internal Error');
  });

  it('falls back to the raw status for anything unknown', () => {
    expect(statusLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW');
  });
});

describe('TestCaseResults', () => {
  afterEach(cleanup);

  it('says so when there is nothing to show', () => {
    render(<TestCaseResults results={[]} />);
    expect(screen.getByText('No test results.')).toBeTruthy();
  });

  it('uses a default title and renders expected against actual', () => {
    render(<TestCaseResults results={[result()]} />);
    expect(screen.getByRole('heading', { name: 'Test Results' })).toBeTruthy();
    expect(screen.getByText('Test 1')).toBeTruthy();
    expect(screen.getByText('Expected:')).toBeTruthy();
    expect(screen.getByText('Actual:')).toBeTruthy();
  });

  it('accepts a custom title', () => {
    render(<TestCaseResults results={[result()]} title="Visible test results" />);
    expect(screen.getByRole('heading', { name: 'Visible test results' })).toBeTruthy();
  });

  it('renders the runtime when present', () => {
    render(<TestCaseResults results={[result({ runtimeMs: 42 })]} />);
    expect(screen.getByText('42 ms')).toBeTruthy();
  });

  it('omits the runtime when the judge did not measure one', () => {
    render(<TestCaseResults results={[result({ runtimeMs: null })]} />);
    expect(screen.queryByText(/ ms$/)).toBeNull();
  });

  // The judge returns compiler diagnostics and stack traces as the actual
  // output; they must reach the student rather than an empty box.
  it('shows compiler diagnostics in the actual output', () => {
    const diagnostics = "Main.java:1: error: ';' expected";
    render(
      <TestCaseResults
        results={[result({ status: 'COMPILATION_ERROR', actualOutput: diagnostics })]}
      />,
    );
    expect(screen.getByText('Compilation Error')).toBeTruthy();
    expect(screen.getByText(diagnostics)).toBeTruthy();
  });

  it('falls back to a placeholder when there is no output at all', () => {
    render(<TestCaseResults results={[result({ actualOutput: null })]} />);
    expect(screen.getByText('(no output)')).toBeTruthy();
  });

  it('hides the input/output block when no expected output is supplied', () => {
    render(<TestCaseResults results={[result({ expectedOutput: undefined })]} />);
    expect(screen.queryByText('Expected:')).toBeNull();
  });

  it('numbers multiple tests and marks each status', () => {
    render(
      <TestCaseResults
        results={[
          result({ testCaseId: 1, status: 'ACCEPTED' }),
          result({ testCaseId: 2, status: 'WRONG_ANSWER', actualOutput: '8' }),
        ]}
      />,
    );
    expect(screen.getByText('Test 1')).toBeTruthy();
    expect(screen.getByText('Test 2')).toBeTruthy();
    expect(screen.getByText('Passed')).toBeTruthy();
    expect(screen.getByText('Wrong Answer')).toBeTruthy();
    expect(screen.getByText('8')).toBeTruthy();
  });
});