#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
EXAMPLE_DIR="$ROOT_DIR/example"
TMP_DIR="$(mktemp -d)"
PACKAGE_BACKUP="$TMP_DIR/example-package.json"
LOCK_BACKUP="$TMP_DIR/example-package-lock.json"

cp "$EXAMPLE_DIR/package.json" "$PACKAGE_BACKUP"
if [[ -f "$EXAMPLE_DIR/package-lock.json" ]]; then
  cp "$EXAMPLE_DIR/package-lock.json" "$LOCK_BACKUP"
fi

cleanup() {
  cp "$PACKAGE_BACKUP" "$EXAMPLE_DIR/package.json"
  if [[ -f "$LOCK_BACKUP" ]]; then
    cp "$LOCK_BACKUP" "$EXAMPLE_DIR/package-lock.json"
  else
    rm -f "$EXAMPLE_DIR/package-lock.json"
  fi
  rm -rf "$EXAMPLE_DIR/node_modules" "$TMP_DIR"
}
trap cleanup EXIT

cd "$ROOT_DIR"
PACK_JSON="$(npm pack --json --ignore-scripts --pack-destination "$TMP_DIR")"
TARBALL="$(node -e 'const p=JSON.parse(process.argv[1]); process.stdout.write(p[0].filename)' "$PACK_JSON")"
TARBALL_PATH="$TMP_DIR/$TARBALL"

node - "$EXAMPLE_DIR/package.json" "$TARBALL_PATH" <<'NODE'
const fs = require('fs');
const path = process.argv[2];
const tarball = process.argv[3];
const pkg = JSON.parse(fs.readFileSync(path, 'utf8'));
pkg.dependencies = pkg.dependencies || {};
pkg.dependencies['rn-call-mask'] = `file:${tarball}`;
fs.writeFileSync(path, JSON.stringify(pkg, null, 2) + '\n');
NODE

npm install --prefix "$EXAMPLE_DIR" --ignore-scripts --no-audit --no-fund

cd "$EXAMPLE_DIR"
node - <<'NODE'
const config = require('@react-native-community/cli').bin
  ? null
  : null;
const pkg = require('rn-call-mask/package.json');
if (pkg.name !== 'rn-call-mask') {
  throw new Error('Packed rn-call-mask dependency was not installed');
}
console.log('Installed packed dependency:', pkg.name, pkg.version);
NODE

cd "$ROOT_DIR"
gradle -p example/android clean assembleDebug lintDebug
