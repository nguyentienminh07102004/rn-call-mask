#!/usr/bin/env bash
set -euo pipefail

required_files=(
  "AGENTS.md"
  "docs/REQUIREMENTS.md"
  "docs/ROADMAP.md"
  ".agents/skills/call-state-machine/SKILL.md"
  ".agents/skills/android-call-notifications/SKILL.md"
  ".agents/skills/ios-callkit-pushkit/SKILL.md"
  ".agents/skills/ci-validation/SKILL.md"
)

for path in "${required_files[@]}"; do
  if [[ ! -s "$path" ]]; then
    echo "::error file=$path::Required repository file is missing or empty"
    exit 1
  fi
done

if [[ -f package.json ]]; then
  node <<'NODE'
const fs = require('fs');
const pkg = JSON.parse(fs.readFileSync('package.json', 'utf8'));
const scripts = pkg.scripts || {};
const required = ['lint', 'typecheck', 'test'];
for (const name of required) {
  if (!scripts[name]) {
    console.error(`::error file=package.json::Missing required script: ${name}`);
    process.exitCode = 1;
  }
}

const hasAndroid = fs.existsSync('android') || fs.existsSync('example/android');
const hasIOS = fs.existsSync('ios') || fs.existsSync('example/ios');

if (hasAndroid && !scripts['ci:android']) {
  console.error('::error file=package.json::Android source exists but script ci:android is missing');
  process.exitCode = 1;
}
if (hasIOS && !scripts['ci:ios']) {
  console.error('::error file=package.json::iOS source exists but script ci:ios is missing');
  process.exitCode = 1;
}
NODE
fi

echo "Repository policy validation passed."
