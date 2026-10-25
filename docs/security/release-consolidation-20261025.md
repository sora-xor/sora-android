# Android release consolidation — October 25, 2026

The preserved October 24 work in `81c1f909` was reviewed against current `modernize`,
not merged wholesale. The current wallet recovery, portfolio, migration, and transaction
flows remain the base. Historical branches are retained.

## Selected security changes

- The source-built xcrypto 1.2.8-sora.2 fork removes EdDSA, uses BC 1.86 for strict
  Ed25519 verification, and retains the Dalek and Rand source backports. Its two 64-bit
  JNI libraries were rebuilt with 16 KB alignment; the snapshot artifact alone would
  have regressed the existing Android 16 support. The original artifact, source patches,
  crates, lockfile, repack script, hashes, and reproduction recipe are retained.
- The still-used Jackson, Google OAuth, Guava, Ktor, LZ4 and Firebase dependency lines
  receive the compatible updates documented in the original audit. Every production
  release lock is regenerated against the current module graph. Current DataStore/JNA
  and other 16 KB native replacements remain in place.
- Exact vendor metadata rules remove unused test dependencies. Foundation's actually
  referenced testing API dependencies remain. Common tests declare JUnit and MockK.
- Crashlytics breadcrumbs accept fixed enum labels, including current recovery events.
  Original exception messages, causes and suppressed exceptions stay outside the wrapper.
  Cleartext traffic is explicitly disabled in the app manifest.

## Deliberately preserved current decisions

SORA Card/KYC and its SDKs were removed by current modernize. Their old JMRTD/Nimbus/BC
utility constraints and KYC tests are not reintroduced. Firebase Analytics without
advertising collection was deliberately enabled in `9fb7c4ee`; the snapshot's obsolete
"no Analytics SDK" policy is not applied. Retired hosted CI and the former release
admission scripts are not restored. Current local release checks remain authoritative.

`dependency-analytics-audit-20261024.md` and its machine-readable reports describe the
old snapshot. They preserve provenance and compatibility evidence, not a claim that the
current final candidate has the same dependency inventory or release qualification.

## Current validation

Endpoint policy commit `ccc84cb9` passed 110 JVM tests, including selection migration,
the runtime RPC client, failover rules, and deferred transaction transport switching.
The security consolidation additionally runs strict production-lock resolution, the
current production-debug JVM suite, app/test APK builds, public-vector crypto tests,
native backport regressions, and native/package checks. The current Maven scan covers 353 runtime coordinates with zero advisory matches.
The native scan covers 51 packages and retains four raw matches across two source-patched
crates (Dalek and Rand); no advisory is suppressed. Both scalar-backend regression suites
pass, and the two rebuilt 64-bit libraries pass ELF alignment and exact repack checks.
Final build and device results are recorded in the accompanying JSON evidence.

The vendor inventory remains `materialized-unreviewed` with `productionAllowed: false`.
Independent source-to-binary/license review, signing identity/Drive configuration,
credential-incident closure, signed release artifacts, and final device/store release
checks are not asserted by source consolidation. Nothing was published to a package
registry or to Google Play.

## Final candidate results

The production-debug JVM suite passed 1,932 tests with 19 intentional/live skips and
zero failures. Strict verification and all 527 release configurations passed. The final
app and test APKs build, lint passes with the existing baseline unchanged, and both
packaged crypto tests pass on an isolated Android 16 emulator with 16 KB pages.
All ten 64-bit native libraries pass package alignment checks, all four crypto ABI
bytes match the retained AAR, and the JSON export bytecode check passes.

Lint also exposed ten existing resource errors. The splash navigation-bar attribute
is now API-qualified, and 205 explicit English fallback values fill nine missing keys
across locales while preserving existing translations. No lint suppression or baseline
change was added. The emulator was shut down after validation. Exact counts, artifact
hashes, decisions and evidence paths are in `release-consolidation-20261025.json`.
