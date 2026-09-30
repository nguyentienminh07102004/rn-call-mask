## Summary

Describe the behavior changed and the call-lifecycle scenario it affects.

## Requirement IDs

List relevant IDs from `docs/REQUIREMENTS.md`, for example `CALL-008`, `AND-008`.

## Validation

- [ ] Lint/typecheck/tests pass
- [ ] Multi-call isolation considered
- [ ] Duplicate/stale action behavior tested
- [ ] No critical incoming-call path depends on JS startup
- [ ] No sensitive call data added to logs

## Device validation

Mark N/A where not applicable.

- [ ] Android foreground
- [ ] Android background
- [ ] Android screen locked/off
- [ ] Android full-screen permission disabled fallback
- [ ] Android two-call sequence A/B
- [ ] iOS foreground CallKit
- [ ] iOS background/wake-by-VoIP-push on physical device
- [ ] Remote cancellation before answer

## Notes / logs

Include redacted logs and exact OS/device versions for lifecycle bugs.
