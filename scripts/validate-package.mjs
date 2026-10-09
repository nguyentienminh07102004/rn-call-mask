import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';

function fail(message) {
  console.error(`::error::${message}`);
  process.exit(1);
}

const npmExecPath = process.env.npm_execpath;
if (!npmExecPath) {
  fail('npm_execpath is unavailable; run this script through npm run ci:pack');
}

let raw;
try {
  raw = execFileSync(
    process.execPath,
    [
      npmExecPath,
      'pack',
      '--json',
      '--dry-run',
      '--ignore-scripts',
    ],
    {
      cwd: process.cwd(),
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'inherit'],
    },
  );
} catch (error) {
  fail(`npm pack --dry-run failed: ${error instanceof Error ? error.message : String(error)}`);
}

let pack;
try {
  const parsed = JSON.parse(raw);
  if (!Array.isArray(parsed) || parsed.length !== 1) {
    fail('npm pack returned an unexpected JSON payload');
  }
  pack = parsed[0];
} catch (error) {
  fail(`Unable to parse npm pack JSON: ${error instanceof Error ? error.message : String(error)}`);
}

if (!pack || !Array.isArray(pack.files)) {
  fail('npm pack JSON did not include a files list');
}

const packedFiles = new Set(
  pack.files
    .map(file => String(file.path ?? '').replaceAll('\\\\', '/'))
    .map(path => path.startsWith('package/') ? path.slice('package/'.length) : path),
);

const required = [
  'package.json',
  'README.md',
  'lib/index.js',
  'lib/index.d.ts',
  'android/build.gradle',
  'android/src/main/AndroidManifest.xml',
  'ios/CallMaskModule.swift',
  'ios/CallMaskCallKitManager.swift',
  'rn-call-mask.podspec',
  'react-native.config.js',
];

for (const path of required) {
  if (!packedFiles.has(path)) {
    fail(`Packed npm artifact is missing ${path}`);
  }
}

const forbiddenPrefixes = [
  'ios/Tests/',
  'android/src/test/',
  'tests/',
];

for (const path of packedFiles) {
  for (const prefix of forbiddenPrefixes) {
    if (path.startsWith(prefix)) {
      fail(`Packed npm artifact must not include ${path}`);
    }
  }
}

const pkg = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8'));

for (const key of ['main', 'types', 'react-native']) {
  if (!pkg[key]) {
    fail(`package.json is missing required field ${key}`);
  }
}

if (pkg.private === true) {
  fail('Package cannot be private');
}

if (pkg.main !== 'lib/index.js') {
  fail(`Unexpected main entry: ${pkg.main}`);
}

if (pkg.types !== 'lib/index.d.ts') {
  fail(`Unexpected types entry: ${pkg.types}`);
}

console.log(
  `Packed npm artifact validation passed: ${pack.filename ?? `${pkg.name}-${pkg.version}.tgz`} (${packedFiles.size} files)`,
);
