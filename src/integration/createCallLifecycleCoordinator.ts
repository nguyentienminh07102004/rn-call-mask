import { CallMask } from '../CallMask';
import {
  CallLifecycleCoordinator,
  type CallControl,
} from './CallLifecycleCoordinator';
import type { CallLifecycleCoordinatorOptions } from './types';

export function createCallLifecycleCoordinator(
  options: CallLifecycleCoordinatorOptions,
): CallLifecycleCoordinator {
  return new CallLifecycleCoordinator(options, CallMask as CallControl);
}
