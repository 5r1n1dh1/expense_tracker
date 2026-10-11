# Kharch

A manual-entry expense tracker for Android with glassmorphic UI, a shared Google-account
ledger, a home-screen widget, and a year-wise monthly dashboard.

**Status:** under active development — see [`plan.md`](plan.md) for the full implementation plan.

## Highlights

- **Manual entry only** — name, category, date, amount (INR). Refunds supported as a first-class type.
- **Shared ledger** — link multiple Google accounts; all of them see the same expenses.
- **Widget** — current-month total plus one-tap quick add.
- **Dashboard** — 12-month split for any year, category breakdown, drill-down.
- **Glassmorphism** — frosted glass surfaces built on a pre-baked downsampled backdrop, so blur
  costs effectively nothing. Light and dark both first-class.
- **Legibility first** — glass colours are contrast-tested in CI (≥7:1 headings, ≥4.5:1 body).

## Design principles

| Principle | What it means |
|---|---|
| Free forever | Firebase **Spark** plan — no billing account, no credit card. |
| Nothing leaves your device | No analytics, no crash reporting, no advertising SDKs. |
| Offline-first | Every feature works with no network; nothing is lost when you reconnect. |
| Never silently destroy data | Destructive database migrations are banned by a CI guard. |
| Build in CI | Everything is compiled, tested and verified in GitHub Actions. |

## Privacy

See [`PRIVACY.md`](PRIVACY.md).

## Building

There is nothing to build locally. Compilation, unit tests, lint and instrumented tests all run
in GitHub Actions against emulators. See [`plan.md` §14](plan.md) for the workflow set.

To follow the build locally (optional, via the GitHub CLI):

```bash
gh run list
gh run watch
gh run view <run-id> --log-failed
```

## Release

Releases are published to GitHub Releases as signed APKs on a `v*` tag
(Android Auto Backup is disabled; users export their own backups).

## License

See [`LICENSE`](LICENSE).