# Android Release locale fallback qualification

The Android resource resolver uses `common/src/main/res/values/strings.xml` as the
default English catalog when a selected locale has no value for a string key.
Keep existing translations and let that platform fallback serve the known gaps.
The product and localization owners must review this policy before release; the
baseline is a candidate implementation, not approval of English copy in every
locale.

The Production Release lint report at candidate `888942c5` had exactly 180
`MissingTranslation` error groups covering 3,655 locale/key pairs in 21 locale
codes. The full list, English values, and missing locales are in
`android-release-missing-translations-2026-09-24.json`. The generated
`app/lint-baseline.xml` contains only those 180 issues. It does not baseline
other lint findings, disable `MissingTranslation`, or change the Android
resources. `app/build.gradle.kts` keeps `abortOnError` and `checkDependencies`
enabled. New missing keys, changed missing-locale sets, and other new errors
still fail lint.

`scripts/verify-production-modernization.mjs` checks that the baseline has
only the 180 recorded `MissingTranslation` issues and that each key and missing
locale list matches the handoff. A normal lint run also applies its own
location and message matching to current source. Remove baseline entries as
translations are completed, regenerate the handoff, and have language owners
review the replacement text. Do not add translated text merely to silence
lint.

Local verification on 25 September 2026:

- `node scripts/verify-production-modernization.mjs` passed with zero
  structural failures.
- `:app:processProductionReleaseResources :app:lintProductionRelease` passed
  offline with protected signing and production cloud-backup validation tasks
  excluded. Lint reported 895 warnings, one hint, and 180 known errors
  filtered by the narrow baseline.
- A temporary new English key in `common/src/main/res/values/strings.xml`
  made that same lint task fail with one new `MissingTranslation` error while
  filtering the original 180. The temporary key was removed and the source
  file restored byte for byte.
- The exact signed, minified Production Release qualification and product
  approval of the fallback policy remain open.
