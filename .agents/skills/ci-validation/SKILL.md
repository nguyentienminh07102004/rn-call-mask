# Skill: CI Validation for rn-call-mask

Use this skill when editing GitHub Actions, package scripts, native build commands, release gates, or repository policy checks.

## CI philosophy

CI must call stable repository scripts. GitHub Actions YAML should orchestrate; it should not become the only place where build logic exists.

Expected package scripts after scaffold:

```json
{
  "scripts": {
    "lint": "...",
    "typecheck": "...",
    "test": "...",
    "ci:android": "...",
    "ci:ios": "..."
  }
}
```

`ci:android` is required once Android source exists.  
`ci:ios` is required once iOS source exists.

## Validation layers

### Repository policy

Always validate:

- `AGENTS.md` exists;
- `docs/REQUIREMENTS.md` exists;
- `docs/ROADMAP.md` exists;
- required skill files exist;
- shell validation script passes.

### JavaScript/TypeScript

When `package.json` exists:

- deterministic dependency install using lockfile;
- lint;
- typecheck;
- unit tests;
- optional build/package validation.

### Android

Once Android code exists:

- Java version pinned;
- Gradle cache used through supported actions;
- `ci:android` must run lint/build/unit tests required by the project;
- no signing secrets required for PR validation.

### iOS

Once iOS code exists:

- run on macOS;
- dependency resolution must be deterministic;
- `ci:ios` should build/test a simulator-safe target or lint the pod/package as appropriate;
- real PushKit device tests remain a manual release gate.

## Supply-chain guidance

Prefer official GitHub actions:

- `actions/checkout`;
- `actions/setup-node`;
- `actions/setup-java`.

Pin major versions intentionally and review upgrades. Avoid adding third-party actions only for convenience when a small shell step can do the job.

## Change checklist

When modifying CI:

1. keep bootstrap behavior valid while repo is still being scaffolded;
2. do not silently skip a platform after its source exists;
3. fail if native source exists but corresponding `ci:*` script is missing;
4. keep local command equivalent documented;
5. validate YAML syntax;
6. validate shell syntax with `bash -n`;
7. never require production signing credentials for pull-request CI.
