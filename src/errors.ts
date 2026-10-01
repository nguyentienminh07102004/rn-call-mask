export type CallMaskErrorCode =
  | 'E_NATIVE_MODULE_UNAVAILABLE'
  | 'E_INVALID_ARGUMENT'
  | 'E_UNKNOWN_CALL'
  | 'E_INVALID_STATE'
  | 'E_PRESENTATION_FAILED';

export class CallMaskError extends Error {
  constructor(
    public readonly code: CallMaskErrorCode,
    message: string,
  ) {
    super(message);
    this.name = 'CallMaskError';
  }
}
