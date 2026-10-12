# Recovery and transaction messages in production languages

The production language picker and locale configuration offer English, Russian,
Spanish, French, German, Norwegian Bokmål, Simplified Chinese, Indonesian and
Turkish. The recovery screen previously had English/Japanese strings, and many
shared safety messages in these eight production translations were still English.

All 87 recovery-screen strings and 201 shared recovery, account repair/removal,
transaction-outcome and preserved-generation strings now have translations in
those eight languages (2,304 module/locale entries). Format argument positions,
argument types and explicit line breaks are unchanged. Existing strings outside
this inventory remain untouched. New generation controls in the test-only
languages keep the existing English fallback policy; they are not presented as
translated production strings. No lint baseline or suppressions were changed.

Validation on 12 October 2026:

- All 16 XML module/locale combinations parse; no duplicate string keys.
- Every scoped source key is present and every positional format argument matches.
- Application/test APK assembly and production lint passed. Lint reported no new
  errors; the unchanged baseline still filters 179 pre-existing errors (1,037
  warnings and one hint remain). Logs: `audit-2026-10-12/android/localization-build-lint.log` in the
  parent workspace; text inventory and validation: `audit-2026-10-12/translations`.
- The 12 existing `SafeModeScreensTest` device tests passed in both English and
  German on an isolated API 34 emulator, including forward actions, light/dark
  themes, veiled phrase access, read-only behavior and retry timing. Logs:
  `recovery-screens-english.log` and `recovery-screens-german.log`.
- Visual review completed for the U6 recovery screen in all nine production
  languages in light and dark on a graphics-capable API 36 emulator. Text wraps
  within buttons, and all lower actions remain reachable by scrolling. All 36
  top/bottom screenshots and UI hierarchies are retained in the parent workspace
  under `audit-2026-10-12/android/recovery-locale-screens-api36`. This is one screen
  at normal font size; it does not replace the wider recovery/device matrix.
- The API 34 ATD image produced black screenshots, so only its instrumentation
  and hierarchy results are counted; its PNGs are not visual evidence.

These are implementation translations. Native-language review has not been
performed; resource validation does not establish linguistic or layout quality.
