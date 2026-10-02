import { describe, expect, it } from 'vitest';
import { formatElapsed } from './duration';

describe('formatElapsed', () => {
  it('formats zero as 00:00:00', () => {
    expect(formatElapsed(0)).toBe('00:00:00');
  });

  it('pads minutes and seconds', () => {
    expect(formatElapsed(1000)).toBe('00:00:01');
    expect(formatElapsed(61_000)).toBe('00:01:01');
    expect(formatElapsed(600_000)).toBe('00:10:00');
  });

  it('formats a 42-minute practice session', () => {
    // 00:42:17 from PRODUCT_SPEC section 9.
    expect(formatElapsed(42 * 60_000 + 17_000)).toBe('00:42:17');
  });

  it('rolls over minutes into hours', () => {
    expect(formatElapsed(60 * 60_000)).toBe('01:00:00');
    expect(formatElapsed(3_661_000)).toBe('01:01:01');
  });

  it('supports long sessions beyond 99 hours', () => {
    expect(formatElapsed(100 * 3600_000)).toBe('100:00:00');
  });

  it('truncates partial seconds rather than rounding', () => {
    expect(formatElapsed(1999)).toBe('00:00:01');
  });

  it('clamps negative and non-finite input to zero', () => {
    expect(formatElapsed(-5000)).toBe('00:00:00');
    expect(formatElapsed(NaN)).toBe('00:00:00');
    expect(formatElapsed(Infinity)).toBe('00:00:00');
  });
});
