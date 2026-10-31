#!/usr/bin/env bash
set -euo pipefail

SRC=app/build/outputs/apk/release/app-release.apk
DST=app/build/outputs/apk/release/expense_tracker.apk

if [ ! -f "$SRC" ]; then
  echo "::error::release APK missing at $SRC"
  exit 1
fi

rm -f "$DST"
cp "$SRC" "$DST"
echo "Renamed release APK -> expense_tracker.apk"
ls -l "$DST"
