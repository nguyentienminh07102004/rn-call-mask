#!/usr/bin/env bash
set -euo pipefail

required_files=(
  "AGENTS.md"
  "docs/REQUIREMENTS.md"
  "docs/ROADMAP.md"
  "docs/TEST_MATRIX.md"
  ".agents/skills/call-state-machine/SKILL.md"
  ".agents/skills/android-call-notifications/SKILL.md"
  ".agents/skills/ios-callkit-pushkit/SKILL.md"
  ".agents/skills/ci-validation/SKILL.md"
  "package.json"
  "tsconfig.json"
  "src/index.ts"
  "example/App.tsx"
  "example/android/app/src/main/AndroidManifest.xml"
)

for path in "${required_files[@]}"; do
  if [[ ! -s "$path" ]]; then
    echo "::error file=$path::Required repository file is missing or empty"
    exit 1
  fi
done

node <<'NODE'
const fs = require('fs');
const pkg = JSON.parse(fs.readFileSync('package.json', 'utf8'));
const scripts = pkg.scripts || {};
for (const name of ['lint', 'typecheck', 'test', 'build']) {
  if (!scripts[name]) {
    console.error(`::error file=package.json::Missing required script: ${name}`);
    process.exitCode = 1;
  }
}

if (fs.existsSync('android')) {
  for (const path of [
    'android/build.gradle',
    'android/settings.gradle',
    'android/src/main/AndroidManifest.xml',
    'android/src/main/java/com/rncallmask/CallMaskModule.kt',
  ]) {
    if (!fs.existsSync(path)) {
      console.error(`::error file=${path}::Android scaffold is incomplete`);
      process.exitCode = 1;
    }
  }
  if (!scripts['ci:android']) {
    console.error('::error file=package.json::Android source exists but script ci:android is missing');
    process.exitCode = 1;
  }
}

if (fs.existsSync('ios') && !scripts['ci:ios']) {
  console.error('::error file=package.json::iOS source exists but script ci:ios is missing');
  process.exitCode = 1;
}
NODE

echo "Repository policy validation passed."
