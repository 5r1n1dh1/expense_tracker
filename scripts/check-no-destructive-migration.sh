#!/usr/bin/env bash
#
# CI guard: Kharch must never use a destructive Room migration. Silently deleting a user's
# ledger on upgrade is the single highest-severity failure mode for an expense tracker, so
# any occurrence of `fallbackToDestructiveMigration` anywhere under app/src/ fails the build.
set -euo pipefail

cd "$(dirname "$0")/.."

if grep -rn "fallbackToDestructiveMigration" app/src/ ; then
    echo "::error file=app/src::Destructive Room migration found."
    echo "A user ledger would be silently deleted on upgrade."
    echo "Write an explicit Migration and a MigrationTestHelper test instead."
    exit 1
fi

echo "No destructive Room migrations found."
