#!/usr/bin/env bash
set -euo pipefail

SDK_PATH="$(xcrun --sdk iphonesimulator --show-sdk-path)"
SWIFT_FILES=(
  ios/CallMaskIOSModels.swift
  ios/CallMaskIOSRegistry.swift
  ios/CallMaskIOSEventStore.swift
  ios/CallMaskIOSEventBus.swift
  ios/CallMaskCallKitManager.swift
  ios/CallMaskPushKitManager.swift
)

if [[ ! -e "${SWIFT_FILES[0]}" ]]; then
  echo "::error::No iOS Swift sources found"
  exit 1
fi

xcrun --sdk iphonesimulator swiftc   -typecheck   -sdk "$SDK_PATH"   -target arm64-apple-ios15.0-simulator   "${SWIFT_FILES[@]}"

echo "iOS native Swift typecheck passed."

TEST_BINARY="${TMPDIR:-/tmp}/rn-call-mask-ios-core-tests"
xcrun swiftc \
  ios/CallMaskIOSModels.swift \
  ios/CallMaskIOSRegistry.swift \
  ios/CallMaskIOSEventStore.swift \
  ios/Tests/main.swift \
  -o "$TEST_BINARY"

"$TEST_BINARY"

pod ipc spec rn-call-mask.podspec >/dev/null
echo "CocoaPods spec validation passed."
