# Privacy Policy — Kharch

**Last updated:** 2026-10-11 · **Applies to:** Kharch for Android

Kharch is a manual expense tracker. This policy is short because the app is designed to
collect almost nothing.

## What is stored

- **Your expenses** — name, category, date, amount, optional note, and any refund you record.
- **Your categories** — including any you create, rename, reorder or hide.
- **Your ledger membership** — the email address and display name of each Google account you
  have linked, so members of a shared ledger can be identified.

## Where it is stored

Your data is stored in **your own Firebase project**, in two places:

1. **On your device**, in a private app database.
2. **In the cloud**, so that expenses sync across your devices and across the Google accounts
   you have linked to the same ledger.

Google provides the Firebase infrastructure. We do not operate our own servers and we cannot
access your data.

## What is never collected

Kharch contains **no analytics, no crash reporting, and no advertising SDKs**. Specifically,
Kharch does **not**:

- collect analytics or usage events;
- send crash reports or stack traces;
- collect device identifiers, advertising IDs, or install IDs;
- show ads;
- share your data with any third party.

Your financial data is never written to the system log (Logcat). This is enforced at build
time: the release build is configured to strip logging calls, and a CI check fails the build
if a logging call is introduced.

## What network requests the app makes

Exactly two:

1. **Firestore sync** — to save and retrieve your ledger in your own Firebase project.
2. **An update check** — only when *you* explicitly tap "Check for updates" in Settings, and
   only to read a small public version file. It sends no data about you.

Nothing is transmitted in the background. There is no background analytics, no beacons, and no
third-party network calls.

## Data backup

Android's automatic backup is **turned off** for Kharch. Your ledger is not uploaded to
Google's backup service by the app. If you want a backup, you can export it yourself at any
time: **Settings → Data → Export** produces a JSON or CSV file that you choose where to save.

## Your rights and controls

- **Export your data** — Settings → Data → Export (JSON or CSV). The JSON export is lossless
  and can be re-imported.
- **Import** — Settings → Data → Import restores from a Kharch JSON export.
- **Delete everything** — Settings → Ledger → "Erase this ledger" removes your data from this
  device and from the cloud.
- **Remove a linked account** — Settings → Accounts removes a Google account from the ledger.
  Your expenses are not deleted, because they belong to the ledger rather than to any account.

## Contact

Questions or requests: open an issue at https://github.com/5r1n1dh1/expense_tracker/issues

## Changes

Material changes to this policy will be noted in the app's release notes.