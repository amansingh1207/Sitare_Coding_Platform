// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render as renderComponent, screen } from '@testing-library/react';
import { PracticeTimer } from './PracticeTimer';

/**
 * The timer is intentionally client-only, so its behaviour is verified here in
 * the browser environment it actually runs in. Nothing in this file touches a
 * submission payload; `submissions.test.ts` is what proves the timer never
 * reaches the judge.
 */
describe('PracticeTimer', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-02T10:00:00Z'));
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  function render(props: { problemTitle?: string } = {}) {
    return renderComponent(<PracticeTimer {...props} />);
  }

  function value(): string {
    return screen.getByTestId('practice-timer-value').textContent ?? '';
  }

  it('starts at zero and only offers Start', () => {
    render();
    expect(value()).toBe('00:00:00');
    expect(screen.getByRole('button', { name: 'Start' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Pause' })).toBeNull();
  });

  it('disables Reset while there is nothing to reset', () => {
    render();
    expect((screen.getByRole('button', { name: 'Reset' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('counts up once started', () => {
    render();
    fireEvent.click(screen.getByRole('button', { name: 'Start' }));

    act(() => {
      vi.advanceTimersByTime(5000);
    });

    expect(value()).toBe('00:00:05');
    expect(screen.queryByRole('button', { name: 'Start' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Pause' })).toBeTruthy();
  });

  it('keeps counting across repeated ticks without drifting', () => {
    render();
    fireEvent.click(screen.getByRole('button', { name: 'Start' }));

    act(() => {
      vi.advanceTimersByTime(3000);
    });
    act(() => {
      vi.advanceTimersByTime(3000);
    });

    expect(value()).toBe('00:00:06');
  });

  it('excludes paused time from the total', () => {
    render();
    fireEvent.click(screen.getByRole('button', { name: 'Start' }));
    act(() => {
      vi.advanceTimersByTime(4000);
    });

    fireEvent.click(screen.getByRole('button', { name: 'Pause' }));
    expect(value()).toBe('00:00:04');

    // Time passing while paused must not be counted.
    act(() => {
      vi.advanceTimersByTime(10000);
    });
    expect(value()).toBe('00:00:04');

    // Resuming continues from where it stopped, not from a fresh interval.
    fireEvent.click(screen.getByRole('button', { name: 'Start' }));
    act(() => {
      vi.advanceTimersByTime(2000);
    });
    expect(value()).toBe('00:00:06');
  });

  it('resets to zero and stops running', () => {
    render();
    fireEvent.click(screen.getByRole('button', { name: 'Start' }));
    act(() => {
      vi.advanceTimersByTime(7000);
    });

    fireEvent.click(screen.getByRole('button', { name: 'Reset' }));

    expect(value()).toBe('00:00:00');
    expect(screen.getByRole('button', { name: 'Start' })).toBeTruthy();
    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(value()).toBe('00:00:00');
  });

  it('formats beyond an hour', () => {
    render();
    fireEvent.click(screen.getByRole('button', { name: 'Start' }));
    act(() => {
      vi.advanceTimersByTime(3_723_000);
    });

    expect(value()).toBe('01:02:03');
  });

  it('stops ticking once unmounted', () => {
    const { unmount } = render();
    fireEvent.click(screen.getByRole('button', { name: 'Start' }));
    unmount();

    // No interval should survive the unmount to update a gone component.
    expect(() => {
      act(() => {
        vi.advanceTimersByTime(10000);
      });
    }).not.toThrow();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('states that the time is not submitted', () => {
    render({ problemTitle: 'Sum of Two Numbers' });
    expect(screen.getByText(/not submitted and does not affect judging/)).toBeTruthy();
    expect(screen.getByText(/Currently viewing: Sum of Two Numbers/)).toBeTruthy();
  });

  it('works without a problem title', () => {
    render();
    expect(screen.queryByText(/Currently viewing/)).toBeNull();
  });

  it('accepts a manual countdown duration', () => {
    render();
    const input = screen.getByLabelText('Countdown duration in minutes');
    fireEvent.change(input, { target: { value: '1' } });
    fireEvent.blur(input);
    expect(value()).toBe('00:01:00');
  });

  it('counts down to zero and announces when time is up', () => {
    render();
    const input = screen.getByLabelText('Countdown duration in minutes');
    fireEvent.change(input, { target: { value: '1' } });
    fireEvent.blur(input);

    fireEvent.click(screen.getByRole('button', { name: 'Start' }));
    act(() => {
      vi.advanceTimersByTime(30_000);
    });
    expect(value()).toBe('00:00:30');

    act(() => {
      vi.advanceTimersByTime(30_000);
    });
    expect(value()).toBe('00:00:00');
    expect(screen.getByText("Time's up!")).toBeTruthy();
    // Stopped: Start is offered again, Pause is gone.
    expect(screen.getByRole('button', { name: 'Start' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Pause' })).toBeNull();
  });

  it('reset restores the full countdown duration', () => {
    render();
    const input = screen.getByLabelText('Countdown duration in minutes');
    fireEvent.change(input, { target: { value: '1' } });
    fireEvent.blur(input);

    fireEvent.click(screen.getByRole('button', { name: 'Start' }));
    act(() => {
      vi.advanceTimersByTime(70_000);
    });
    expect(screen.getByText("Time's up!")).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Reset' }));
    expect(value()).toBe('00:01:00');
    expect(screen.queryByText("Time's up!")).toBeNull();
  });

  it('clearing the duration returns to count-up mode', () => {
    render();
    const input = screen.getByLabelText('Countdown duration in minutes');
    fireEvent.change(input, { target: { value: '2' } });
    fireEvent.blur(input);
    expect(value()).toBe('00:02:00');

    fireEvent.change(input, { target: { value: '' } });
    fireEvent.blur(input);

    fireEvent.click(screen.getByRole('button', { name: 'Start' }));
    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(value()).toBe('00:00:05');
  });
});