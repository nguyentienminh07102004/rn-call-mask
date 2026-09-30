#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

cd "$ROOT_DIR"

PACK_JSON="$(npm pack --json --pack-destination "$TMP_DIR")"
TARBALL="$(node -e 'const p=JSON.parse(process.argv[1]); process.stdout.write(p[0].filename)' "$PACK_JSON")"
TARBALL_PATH="$TMP_DIR/$TARBALL"

if [[ ! -f "$TARBALL_PATH" ]]; then
  echo "::error::npm pack did not produce a tarball"
  exit 1
fi

CONTENTS="$TMP_DIR/contents.txt"
tar -tzf "$TARBALL_PATH" | sort > "$CONTENTS"

required=(
  "package/package.json"
  "package/README.md"
  "package/lib/index.js"
  "package/lib/index.d.ts"
  "package/android/build.gradle"
  "package/android/src/main/AndroidManifest.xml"
  "package/ios/CallMaskModule.swift"
  "package/ios/CallMaskCallKitManager.swift"
  "package/rn-call-mask.podspec"
  "package/react-native.config.js"
)

for path in "${required[@]}"; do
  if ! grep -Fxq "$path" "$CONTENTS"; then
    echo "::error::Packed npm artifact is missing $path"
    exit 1
  fi
done

if grep -q '^package/ios/Tests/' "$CONTENTS"; then
  echo "::error::iOS native test sources must not be published"
  exit 1
fi

if grep -q '^package/tests/' "$CONTENTS"; then
  echo "::error::JavaScript test sources must not be published"
  exit 1
fi

node - "$TARBALL_PATH" <<'NODE'
const fs = require('fs');
const { execFileSync } = require('child_process');
const tarball = process.argv[2];
const listing = execFileSync('tar', ['-xOf', tarball, 'package/package.json'], { encoding: 'utf8' });
const pkg = JSON.parse(listing);
for (const key of ['main', 'types', 'react-native']) {
  if (!pkg[key]) {
    console.error(`::error::Packed package is missing package.json field ${key}`);
    process.exit(1);
  }
}
if (pkg.private === true) {
  console.error('::error::Packed package cannot be private');
  process.exit(1);
}
NODE

echo "Packed npm artifact validation passed: $TARBALL"
