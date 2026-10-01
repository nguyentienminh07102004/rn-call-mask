import type { CallState } from './types';

const transitions: Readonly<Record<CallState, readonly CallState[]>> = {
  incoming: ['ringing', 'ended'],
  ringing: ['connecting', 'ended'],
  connecting: ['active', 'ended'],
  active: ['held', 'ending', 'ended'],
  held: ['active', 'ending', 'ended'],
  ending: ['ended'],
  ended: [],
};

export function canTransition(from: CallState, to: CallState): boolean {
  return from === to || transitions[from].includes(to);
}

export function assertTransition(from: CallState, to: CallState): void {
  if (!canTransition(from, to)) {
    throw new Error(`Invalid call transition: ${from} -> ${to}`);
  }
}
