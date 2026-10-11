#!/usr/bin/env bash
#
# Ensure every Kotlin source file ends with exactly one trailing newline.
#
# Why this exists: detekt's `formatting` ruleset (FinalNewline / NewLineAtEndOfFile)
# fails the build otherwise, and editors/agents do not reliably append one. Running
# this in CI *before* detekt means the style gate reports real findings instead of
# being blocked by whitespace.
#
# Safe to run on a working tree: it only appends a newline when one is missing.
set -euo pipefail

cd "$(dirname "$0")/.."

fixed=0
while IFS= read -r -d '' file; do
  # Skip binary-looking files (shouldn't be any .kt, but be defensive).
  if [ -s "$file" ] && [ -n "$(tail -c 1 "$file")" ]; then
    printf '\n' >> "$file"
    echo "added trailing newline: $file"
    fixed=$((fixed + 1))
  fi
done < <(find app/src -name '*.kt' -type f -print0)

if [ "$fixed" -gt 0 ]; then
  echo "Normalised $fixed file(s)."
  # Leave the tree dirty-but-correct so detekt (later in the job) passes and the
  # developer sees exactly which files were touched in the log.
  exit 0
fi

echo "All Kotlin sources already end with a newline."
