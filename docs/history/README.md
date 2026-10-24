# History

Records from the 2026 modernization work, kept for reference. Nothing here is read by the
build, by CI or by the app.

`modernization/` was `docs/modernization/` until release 3.9.0. Release 3.9.0 retired the
qualification workflow, so these files describe a process that no longer runs:

- `production-release-checklist.md` and the `qualification/*-README.md` files describe
  qualification receipts, controllers, canaries and rollout gates. Their scripts were deleted.
  The current release steps are in `docs/release-3.9.0/RELEASE-CHECKLIST.md`.
- The `*-16kb-rebuild-*.md` and `android-16kb-*` reports explain how the vendored native
  libraries in `vendor/soramitsu-maven` were rebuilt for 16 KB pages. The xcrypto, lazysodium
  and graphics-path reports still describe binaries that ship. The camera-core, rootbeer and
  tensorflow-lite libraries were removed with SORA Card in 3.9.0, so their reports are history
  only.
- `config/gradle-dependency-provenance.json` was `config/gradle-dependency-provenance.json`.
  Only the deleted verifier read it. Hashes and statuses in it are a snapshot and are not
  kept up to date.
- `qualification/android-release-missing-translations-2026-09-24.json` lists strings that had no
  translation in September 2026. Android lint now tracks missing translations.

Links inside these files that start with `docs/modernization/` now resolve under
`docs/history/modernization/`. Links to `scripts/` files that no longer exist refer to
deleted qualification tooling.
