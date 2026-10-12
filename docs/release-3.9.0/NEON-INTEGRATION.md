# Android Neon screen integration — 12 October 2026

This release connects the previously separate Neon component kit to the actual app screens.
The original design allows phases to ship independently. This is a coherent component and
screen integration, not a claim that every optional §8 concept is implemented.

## Shipped scope

- Standard fragment screens use the static aurora, transparent scaffold and toolbar. Toolbar
  chrome appears after 8 dp of scroll with a 160 ms fade, or immediately with reduced motion.
  Onboarding after Welcome and account backup/export use the mint/cyan safe aurora.
- Production cards and actions use the shared glass and Neon button variants. Financial
  review cards and sensitive backup/import cards are opaque. The existing card order,
  visibility settings, navigation callbacks and financial checks remain authoritative.
- The wallet asset hero uses a chamfered spectrum frame, grid and tabular balance. Quick
  actions use 56 dp neumorphic discs, wrapping into two columns at large text sizes.
- Native bottom navigation has opaque chrome and a 56 dp gradient Polkaswap disc. Its static
  halo is removed with reduced effects/high contrast; its route remains the existing swap route.
- Welcome exposes distinct create, import and optional Drive actions. Import offers Phrase,
  Raw seed and JSON segments with the existing file-picker/password paths. Switching source
  resets its input and invalidates an earlier asynchronous input/file validation.
- The PIN screen uses 72/80 dp keys, 14 dp dots, 4/6-digit state from the existing presenter,
  digit-count accessibility and long-press delete. Migration checks replace the keypad.
  Reduced motion suppresses shaking and press scaling. No PIN/signing policy is changed.
- App settings writes the existing System/Dark/Light settings. Only an otherwise empty install
  gets the new dark default. Existing files/preferences and every explicit theme choice win.
  Long option labels can wrap instead of clipping into fixed-height rows.
- Swap has a 48 dp neumorphic direction control; its turn animation respects reduced motion.
  Receive retains a white QR plate. Signing, quotes, slippage and submission logic are unchanged.

## Sensitive screens and clipboard

Ordinary phrase/seed backup starts veiled and requires an explicit reveal. It hides on pause
or stop and after 60 seconds; the reveal state is not saved. Phrase, seed, JSON/password input,
mnemonic confirmation, PIN and removal-backup screens hold reference-counted `FLAG_SECURE`
leases, including the dialog window when applicable. A departing screen cannot clear another
active lease or a flag that predated the lease.

Explicit secret copies use Android's sensitive-content marker and a 45-second deadline. Only
an ownership token and monotonic timestamps are persisted, never the copied secret. Cleanup
checks that the current clipboard still belongs to the copy. Android denies background
clipboard reads, so cleanup retries on foreground; a restart before expiry schedules the
remaining interval. A newer clipboard item is preserved. The deadline is best effort while
the OS prevents background access; it is not a promise of background clipboard access.

## Verification

Evidence is saved in `../audit-2026-10-12/android/` relative to the repository.

- `neon-wiring-build-4.log`: production debug and instrumentation APKs built; 271 common JVM
  tests passed. `neon-production-screens-3.log`: seven focused device tests passed.
- `neon-production-screens-2.log`: 42 instrumented cases completed with no failures and one
  existing API-36 permission assumption skipped. Includes actual wallet/Polkaswap navigation,
  send review and pause states, scanner recovery and Safe Mode/read-only screens.
- `neon-final-jvm-counts.json`: productionDebug and developDebug each completed 1,767 JVM
  tests with 17 skips, zero failures and zero errors. Build 6 also assembled debug/test APKs
  and the R8-minified local benchmark APK. Its initial lint failure was corrected (`app:tint`
  and declared fallback strings). `neon-final-profile-lint-8.log` passes final lint with
  no new errors; the unchanged baseline filters 179 historical errors, with 1,047 warnings
  and two hints remaining. The full report is `neon-final-lint-8.txt`.
- `neon-final-device-7.log`: clean disposable API 36 ARM64 emulator, 54 instrumented cases,
  zero failures and one existing API-specific permission assumption skipped. Covers the
  clipboard service recreation before expiry, background expiry, newer clipboard preservation,
  secure-window leases, veiled backup, PIN, import sources, read-only/Safe Mode screens and
  actual wallet/Swap/send/scanner navigation.
- The preceding device run 6 retained the first-run disclaimer preference from an earlier test
  and failed its fresh-install precondition. Its AX evidence showed the correct Swap route;
  the exact unchanged 54-case suite passed after clearing only disposable app data.
- `neon-minified-artifact-checks-6.log`: the local R8 benchmark APK passes batch JSON-backup
  encoder and packaged native 16 KB checks. This is not a Play-signed release artifact.
- `neon-final-localization-check.json`: seven new interface strings plus the three previously
  unfinished Reduce effects/Loading strings are present in all ten production languages with
  both PIN count placeholders preserved. Non-shipping/test locales retain the explicitly
  documented English fallback policy. Translations need native review.
- `neon-production-captures/ux-acceptance/` contains actual app journey captures; the
  `neon-production` directory contains focused production-composable captures. These are
  public synthetic wallets, with no funded transfer or real recovery phrase.

## Deferred visual polish

The release preserves current functional screen structures rather than adding new money-flow
behavior to satisfy illustration details. Optional splash scanline/ignite, tutorial paging,
backup reminder snooze, a new Forgot-PIN shortcut, a redesigned security dashboard, bespoke
chart treatment, swap countdown/price-impact checkbox and new transaction-result animations
are not asserted as implemented by this patch. Existing recovery routes, backup checks,
quote/submission guards and read-only/pending-check controls remain in force.

This evidence does not establish physical-device migration, Play upload acceptance, historical
Drive OAuth continuity, or account-service sign-in. Those are separate release checks.
