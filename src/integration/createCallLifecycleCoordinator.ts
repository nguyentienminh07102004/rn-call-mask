import { CallMask } from '../CallMask';
import { CallLifecycleCoordinator } from './CallLifecycleCoordinator';
import type { CallLifecycleCoordinatorOptions } from './types';

export function createCallLifecycleCoordinator(
  options: CallLifecycleCoordinatorOptions,
): CallLifecycleCoordinator {
  return new CallLifecycleCoordinator(options, CallMask);
}
