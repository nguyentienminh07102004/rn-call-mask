import { describe, expect, it } from 'vitest';

import { assertTransition, canTransition } from '../src/stateMachine';

describe('call state machine', () => {
  it('accepts the normal incoming call path', () => {
    expect(canTransition('incoming', 'ringing')).toBe(true);
    expect(canTransition('ringing', 'connecting')).toBe(true);
    expect(canTransition('connecting', 'active')).toBe(true);
    expect(canTransition('active', 'ending')).toBe(true);
    expect(canTransition('ending', 'ended')).toBe(true);
  });

  it('treats same-state transitions as idempotent', () => {
    expect(canTransition('ringing', 'ringing')).toBe(true);
  });

  it('rejects invalid resurrection', () => {
    expect(canTransition('ended', 'ringing')).toBe(false);
    expect(() => assertTransition('ended', 'ringing')).toThrow(
      'Invalid call transition: ended -> ringing',
    );
  });

  it('supports ending a ringing call without answering', () => {
    expect(canTransition('ringing', 'ended')).toBe(true);
  });
});
