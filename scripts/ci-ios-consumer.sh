#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

cd "$ROOT_DIR"
npm run build

PACK_JSON="$(npm pack --json --ignore-scripts --pack-destination "$TMP_DIR")"
TARBALL="$(node -e 'const p=JSON.parse(process.argv[1]); process.stdout.write(p[0].filename)' "$PACK_JSON")"
TARBALL_PATH="$TMP_DIR/$TARBALL"
PACKAGE_DIR="$TMP_DIR/package"
CONSUMER_DIR="$TMP_DIR/consumer"

mkdir -p "$CONSUMER_DIR"
tar -xzf "$TARBALL_PATH" -C "$TMP_DIR"

cat > "$CONSUMER_DIR/package.json" <<'JSON'
{
  "name": "rn-call-mask-ios-consumer",
  "version": "0.0.0",
  "private": true
}
JSON

export RN_CALL_MASK_REACT_NATIVE_PATH="$ROOT_DIR/node_modules/react-native"
export RN_CALL_MASK_PACKAGE_PATH="$PACKAGE_DIR"
export RN_CALL_MASK_CONSUMER_PATH="$CONSUMER_DIR"

cat > "$CONSUMER_DIR/Podfile" <<'RUBY'
ENV["RCT_NEW_ARCH_ENABLED"] = "1"

react_native_path = ENV.fetch("RN_CALL_MASK_REACT_NATIVE_PATH")
package_path = ENV.fetch("RN_CALL_MASK_PACKAGE_PATH")
consumer_path = ENV.fetch("RN_CALL_MASK_CONSUMER_PATH")

require File.join(react_native_path, "scripts/react_native_pods")

platform :ios, "15.1"
prepare_react_native_project!
install! "cocoapods", :integrate_targets => false, :deterministic_uuids => false

target "RnCallMaskConsumer" do
  use_react_native!(
    :path => react_native_path,
    :app_path => consumer_path,
    :privacy_file_aggregation_enabled => false
  )

  pod "rn-call-mask", :path => package_path
end

post_install do |installer|
  react_native_post_install(
    installer,
    react_native_path,
    :mac_catalyst_enabled => false
  )
end
RUBY

cd "$CONSUMER_DIR"
pod install --no-repo-update

PODS_PROJECT="$CONSUMER_DIR/Pods/Pods.xcodeproj"
if [[ ! -d "$PODS_PROJECT" ]]; then
  echo "::error::CocoaPods did not generate Pods.xcodeproj"
  exit 1
fi

xcodebuild \
  -project "$PODS_PROJECT" \
  -target rn-call-mask \
  -configuration Debug \
  -sdk iphonesimulator \
  CODE_SIGNING_ALLOWED=NO \
  ONLY_ACTIVE_ARCH=YES \
  build

echo "iOS packed consumer pod install/build passed."
