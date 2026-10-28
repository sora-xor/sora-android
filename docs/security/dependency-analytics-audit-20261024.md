> Historical evidence for snapshot `81c1f909`, before the public 3.9.x merge.
> The current branch intentionally removed Card/KYC, enabled Analytics without ads, and
> retired the old admission scripts/hosted CI. This report is retained as provenance,
> not current release qualification. See `release-consolidation-20261025.md` for
> the selected changes and current validation.

# Android dependency and analytics audit — 24 October 2026

The production Maven dependency inventory contained **39 advisory matches across nine packages**. After remediation, the fresh runtime scan reports **zero matches across 396 coordinates**, including removal of all five previously remaining Bouncy Castle/EdDSA matches. This audit upgrades dependency families, rebuilds xcrypto from source with strict BC Ed25519 verification, constrains transitive backup/OAuth dependencies, replaces the unmaintained LZ4 artifact, and restricts custom Crashlytics breadcrumbs to fixed event names. The scan does **not** establish that the wallet is free of vulnerabilities or production-qualified; source-fork compatibility and malicious-signature checks are separate evidence below.

## Scope and method

- Examined the version catalog, all production Gradle locks, strict checksum metadata, repository/vendor policy, Firebase initialization/logging, and the production Polkaswap Indexer transport.
- Queried OSV with the exact 415 baseline and 396 final Maven coordinates in `app:productionReleaseRuntimeClasspath`. The repeatable script is `scripts/audit-runtime-dependencies.py`; before/after reports bind the actual lock-file SHA-256. An affected-version match does not prove the vulnerable algorithm or parser is reachable in this wallet.
- Checked publisher Maven metadata for 80 explicitly versioned catalog modules; results and inaccessible vendor/JitPack coordinates are recorded in `android-version-inventory-20261024.json`. Many UI/framework packages have newer releases. Their broad upgrades, major migrations, and repackaged vendor updates remain separate from these targeted security repairs.
- Compared the 116 newly admitted Gradle checksum entries against artifact bytes: 114 fetched from official Google Maven or Maven Central, and two explicitly identified local-fork AAR/module entries matched to retained source-build provenance. Evidence is `android-added-artifact-checksums-20261024.json`; publisher checks and local byte bindings do not supply the independent approval required by the release policy.
- A separate Cargo scan and source review now covers xcrypto's native JNI subgraph. It found three advisory families: the unused vulnerable zeroize_derive was removed, and Dalek/Rand received upstream source backports. The raw post-patch scan intentionally retains four version-match records across two patched crates (two alias-deduplicated advisory families), not a misleading clean native scan. Other vendor native code, cloud console settings and production release signing remain outside comprehensive review.

## Remediation

| Dependency | Previous runtime / declared version | Selected version | Rationale |
| --- | --- | --- | --- |
| Jackson core/databind | 2.10.0 | BOM 2.22.4 | Removes old parser/resource-exhaustion and deserialization advisory matches; aligns annotations/core/databind. Android 26 minimum matches this app. |
| Google OAuth client | 1.26.0 runtime, 1.25.0 compile | 1.39.0 | Fixes token signature verification and improper authorization advisories; aligns compile/runtime. |
| Guava | 30.1.1-android runtime, 20.0 compile | 33.7.2-android | Fixes temporary-directory vulnerabilities; preserves Android variant. |
| Nimbus JOSE JWT | 9.37.1 | 9.37.4 | Security patch for encrypted-token and nested-JSON denial of service. |
| Ktor | 2.3.1 | 2.3.13 | Backported fix for CVE-2024-49580; BOM aligns vendor compile/runtime artifacts. The app does not install the affected HttpCache plugin. |
| LZ4 Java | org.lz4:lz4-java:1.8.0 | at.yawk.lz4:lz4-java:1.12.1 | Maintainer-documented API-compatible continuation, including October security fixes. xcrypto's bytecode uses its xxHash API. |
| Firebase Android BoM | 34.6.0 | 34.19.0 | Latest 34.x line, resolving Crashlytics 20.1.1 instead of 20.0.3. |
| Crashlytics Gradle plugin | 3.0.6 | 3.0.8 | Current compatible patch line. |
| Bouncy Castle provider/utilities | 1.78.1 / 1.71 | 1.86 / 1.86 | Coordinated current security release. |
| JMRTD | 0.7.34 | 0.8.9 | Current ASN.1 API and publisher-aligned BC 1.86 closure. |
| xcrypto / EdDSA | xcrypto 1.2.7 / EdDSA 0.3.0 | local xcrypto 1.2.8-sora.2 / EdDSA removed | Rebuilt source wrapper delegates to BC Ed25519 with strict verification. |

Newer major lines exist for Firebase (35.1.0), Ktor (3.6.0), and Nimbus (10.10); these are not silently substituted into vendored SDK contracts. Source and checksum/lock inventory changes retain `materialized-unreviewed`, strict verification, and every existing production-review requirement.

## Crypto remediation and remaining release requirements

**A-01 — Bouncy Castle provider advisories remediated in source and dependency policy.** Provider and utility versions are aligned at 1.86, with JMRTD 0.8.9 (whose publisher POM also targets BC 1.86). This replaces the incompatible mix of provider 1.78.1, utilities 1.71 and JMRTD 0.7.34. The four original matches were [GHSA-574f-3g2m-x479](https://github.com/advisories/GHSA-574f-3g2m-x479), [GHSA-9pwp-9qqc-pr26](https://github.com/advisories/GHSA-9pwp-9qqc-pr26), [GHSA-c3fc-8qff-9hwx](https://github.com/advisories/GHSA-c3fc-8qff-9hwx), and [GHSA-qp49-qgx5-5m26](https://github.com/advisories/GHSA-qp49-qgx5-5m26).

The retained ICAO BAC, PACE, CMS tagged-content and EC signing/agreement probes exercise the coordinated dependency closure. The EC round-trip checks curve/point/order/cofactor and signature/agreement behavior; JCA providers may use different valid named-curve or explicit-parameter DER encodings. It does not require those encodings to be byte-identical. Static checking found no missing direct symbols in xcrypto, IDensic, JMRTD, cert-cvc or web3j against the upgraded graph. The complete raw report retains 37 unavailable optional Nimbus FIPS/PKIX and OkHttp BC-JSSE symbols; those provider classes were also absent from the previous provider/utility artifacts. Physical passport hardware, live vendor KYC and reflective paths still require qualification.

**A-02 — Vulnerable EdDSA removed through a source-built SDK replacement.** The local `jp.co.soramitsu:xcrypto:1.2.8-sora.2` fork replaces the `net.i2p.crypto:eddsa:0.3.0` wrapper with Bouncy Castle's maintained RFC 8032 Ed25519 implementation. No EdDSA dependency or bytecode reference remains in the fork, and the old AAR is removed from the vendor repository. The patch, complete sources JAR, POM/module metadata, four rebuilt JNI ABIs and exact Cargo lock are retained with provenance/checksums; this is a source fix, not a renamed vulnerable binary.

The public API retains 32-byte seed/private keys, derives the public key internally while signing, checks signature/key lengths, validates the full public point, and rejects noncanonical signature scalars. The public regression probe reproduces [GHSA-p53j-g8pw-4w5f / CVE-2020-36843](https://github.com/advisories/GHSA-p53j-g8pw-4w5f) against the old SDK (`S+L` accepted) and confirms rejection by the replacement. Sixteen retained old-SDK key/signature vectors match byte-for-byte. Old-writer/new-reader and new-writer/old-reader encrypted backup checks pass. RFC 8032, malformed-length, small-order/noncanonical-point, tamper and wrong-password cases are retained in normal tests.

The managed security fork passed all 19 upstream tests. The subsequent native fork rebuilds all four JNI ABIs with the backports described in A-05; its managed source is unchanged. A Rust macro syntax adjustment allows compilation with Rust 1.97.1 without changing sr25519 math or derivation. Per-file upstream notices are preserved; the upstream tree has no top-level license file, and independent source-to-binary/license review remains unqualified. Reproduction instructions and old-writer fixture provenance are in `scripts/qualification/crypto-security/README.md`.

**A-03 — Existing release and credential review remains blocked.** The dependency preflight requires independent lock/verification/vendor review, the scoped IDensic/TensorFlow namespace review, dependency/signing admission, and closure of the recorded credential incident. No approval, signature, token revocation, credential rotation, or signing continuity is asserted by this audit.

**A-04 — Unused production test dependencies removed; foundation packaging debt remains.** Source and bytecode inspection found no JUnit/Mockito/MockK use in xsubstrate 1.2.7, and no MockK use in foundation 0.0.4 or 318 other inspected runtime artifacts. Exact-version consumer metadata rules remove those unused edges. The redundant `feature_account_api` implementation dependency on coroutine-test is removed; common tests now declare JUnit/MockK explicitly. This removes 19 runtime coordinates, including the MockK/Jupiter closure, without republishing these vendor binaries. Foundation's public testing helper classes actually reference JUnit, Mockito and coroutine-test, so their required closure is retained; removing it requires a reviewed SDK packaging change. Evidence: `android-test-dependency-usage-20261024.json`.

**A-05 — Native crypto advisories repaired with retained upstream backports.** The original native scan found six raw OSV records across three affected crates (three distinct advisory families). Schnorrkel 0.9.1 uses Dalek 2.1.3 scalar math in native derivation/signing, so its [RUSTSEC-2024-0344](https://rustsec.org/advisories/RUSTSEC-2024-0344.html) timing issue is relevant code, although no wallet exploit was demonstrated. The local source fork backports the original u64/u32 underflow-mask optimization barriers from upstream commits `415892acf1cdf9161bd6a4c99bc2f4cb8fae5e6a` and `b4f9e4df92a4689fb59e312a21f940ba06ba7013`. It preserves scalar arithmetic and Schnorrkel APIs rather than changing wallet cryptography.

Rand 0.7.3's [RUSTSEC-2026-0097](https://rustsec.org/advisories/RUSTSEC-2026-0097.html) requires a reentrant custom logger and its optional logging feature. That feature was absent from the JNI graph. The source fork nevertheless removes internal logging callbacks following upstream `50bf18ab96c04b0312852d182f415228e77a2e4d` and makes the legacy logging feature inert. A regression with logging explicitly enabled observes seven callbacks against unpatched Rand and zero after the patch. The unused direct zeroize_derive 0.7.0 affected by [RUSTSEC-2021-0115](https://rustsec.org/advisories/RUSTSEC-2021-0115.html) and its obsolete closure are removed; required zeroize_derive 1.4.2 remains.

Complete original crates.io archives and licenses, source patches, the final Cargo lock and four rebuilt JNI libraries are retained. Every surviving registry dependency version remains unchanged. Reapplying the native patch reproduces all seven changed source files exactly. Both scalar backends pass boundary/callback regressions. Optimized Rust 1.97.1 IR for both backends across all four Android targets retains the volatile barriers and contains no branch instructions in the examined subtraction functions; this is specific compiler evidence, not a general constant-time proof. The existing JNI Gradle plugin ships its dev-profile native build; optimized IR is a separate additional check. Later upstream PR #662 refactoring is not claimed as part of this minimal backport.

The fresh native scan covers 51 packages and retains four raw matches for the two patched source versions; no advisory is suppressed. Consult `android-native-cargo-osv-before-20261024.json`, `android-native-cargo-osv-after-20261024.json`, `android-native-security-assessment-20261024.json`, `android-native-source-reproduction-20261024.json` and `native-scalar-ir-20261024/manifest.json`. Independent source-to-binary/license review and protected release admission remain required.

## Analytics and API data handling

- No Firebase Analytics, Amplitude or Mixpanel SDK is in the production runtime lock. Crashlytics is the active diagnostic service. The manifest now explicitly deactivates Firebase Analytics and advertising-ID collection to preserve that policy if a future transitive SDK adds Analytics.
- `FirebaseWrapper` previously accepted arbitrary log strings. Its API now accepts an enum, and all callers send fixed labels. Manual exceptions remain replaced with an allowlisted error class and no original cause/suppressed exceptions; regression tests exercise the enabled reporting path.
- Crashlytics automatic fatal-crash reporting remains enabled where Firebase is configured. The manual wrapper cannot sanitize automatic fatal reports, and no in-app collection opt-out was found. Review fatal exception content, consent/disclosure and Firebase retention/access controls before asserting an opt-in or comprehensive redaction policy.
- The manifest explicitly denies cleartext traffic. No trust-all TLS manager or permissive hostname verifier was found in the inspected app sources.
- The production PI client uses `https://pi.soramitsu.io/graphql`, not a user-controlled GraphQL endpoint. Its bounded HTTPS transport disables response caching, rejects POST redirects, enforces timeouts/body limits/JSON media type, and rejects malformed or duplicate-key JSON. Account-specific history requests necessarily disclose the requested public account to that service; no seed/private-key fields are intentionally included in that transport.
- Dynamic card/payment endpoints and credentials come from build configuration and vendored SDKs. Their live service/account policies cannot be certified from repository source. Existing credential-incident blockers remain relevant.

## Verification

The final native fork and dependency cleanup passed whole-app Kotlin compilation and both APK assemblies (16m 52s). Final packaged-device tests and all-four-ABI byte matching passed. The separate strict offline run passed all 565 production configurations, repeated app Kotlin compilation and an explicit rerun of the 18 credential tests in 1m 53s, without writing locks or checksums. Final scan/test/build details are retained in `android-verification-20261024.json`.

- The patched DevelopDebug whole-app Kotlin compilation and main manifest merge passed, followed by a successful **offline, strict-verification resolution of all 565 production configurations** and repeated app compile without lock/checksum writes. The merged manifest retains the cleartext ban and both analytics privacy metadata settings.
- **141 focused JVM tests passed**, with zero failures/errors/skips: 47 common (Ed25519 security and retained-key/backup compatibility, ICAO/PACE/CMS/EC, telemetry, bounded HTTPS/strict JSON, Iroha derivation and xcrypto hash vectors), 18 credential repository (explicitly rerun after the final lock/checksum refresh), and 76 Polkaswap Indexer client tests. All 19 upstream xcrypto unit tests passed for the unchanged managed fork; the final native rebuild completed all four ABI tasks, with those managed tests up to date. Four additional native regression executions passed across both scalar backends.
- **Two packaged crypto tests passed on a fresh Android 34 arm64 emulator**: retained SORA2 sr25519 native derivation/signing and conversion, plus BC 1.86 Ed25519 and noncanonical-signature rejection. All four APK JNI hashes match the rebuilt AAR, and none of the 38 packaged DEX files references the legacy EdDSA, MockK or Jupiter types. The dedicated emulator was stopped and deleted afterward. The same test is wired into normal CI on API 36 x86_64; that second ABI was not executed locally. Evidence: `android-xcrypto-package-20261024.json` and `android-crypto-device-20261024.txt`.
- Eight offline audit-script regression tests passed and run in the normal Android Verification CI workflow, including malformed/error batch and pagination responses, repeated tokens, runtime scope filtering, and empty-inventory rejection. Incomplete OSV results exit with an error and cannot generate a clean report.
- Dependency/signing admission tests passed the positive P-256 case and 29 rejection mutations. Lock-inventory tests passed 112 rejection mutations; candidate-package seal/download validation and 30 rejection mutations also passed against the refreshed snapshot. The source dependency preflight reports **STABLE**, zero integrity failures and the six pre-existing independent-review/credential blockers; its nonzero exit deliberately continues to block release. The snapshot binds 31 lockfiles and 566 configurations (565 production plus settings), with review flags unchanged.
- Production Release compilation encountered the existing missing `SORA_GOOGLE_TOKEN_PROD` validation and was not bypassed. R8 release packaging, physical passport hardware and live vendor KYC are not qualified by these checks. The retained public wallet-key and old-writer backup compatibility checks are documented separately above.

Reproduce the source-level checks from the Android repository:

```sh
python3 scripts/test-audit-runtime-dependencies.py
python3 scripts/audit-runtime-dependencies.py --output /tmp/android-osv-current.json
node scripts/test-android-production-lock-inventory-v1.mjs
node scripts/test-android-dependency-signing-review-v1.mjs
node scripts/verify-production-modernization.mjs --dependency-preflight
```

The live audit intentionally exits 1 while advisory matches remain; API/inventory failures exit 2.

## Code references

- Crypto security source patches: `vendor/soramitsu-maven/build-inputs/xcrypto-1.2.8-sora.1-security.patch` and `xcrypto-1.2.8-sora.2-native-security.patch`; retained-fixture and reproduction instructions: `scripts/qualification/crypto-security/README.md`.
- Dependency policy: `build.gradle.kts:27`, `common/build.gradle.kts:147`, `gradle/libs.versions.toml:38`.
- Telemetry boundary: `common/src/main/java/jp/co/soramitsu/common/logger/FirebaseWrapper.kt:40` and `:92`; initialization: `app/src/main/java/jp/co/soramitsu/sora/SoraApp.kt:108`.
- Manifest policy: `app/src/main/AndroidManifest.xml:24` and `:37`.
- PI production transport: `feature_blockexplorer_api/src/main/java/jp/co/soramitsu/feature_blockexplorer_api/data/PolkaswapIndexerClient.kt:2423`.
- HTTPS admission: `common/src/main/java/jp/co/soramitsu/common/network/BoundedHttpTextClient.kt:125` and `:190`.

## Primary sources

- [Ktor releases](https://ktor.io/docs/releases.html) and [Ktor caching advisory](https://github.com/advisories/GHSA-8qv4-773j-c979).
- [Jackson 2.22 compatibility and release notes](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.22), [Guava releases](https://github.com/google/guava/releases), [Google OAuth client releases](https://github.com/googleapis/google-oauth-java-client/releases).
- [LZ4 continuation and migration instructions](https://github.com/yawkat/lz4-java), [LZ4 security release 1.12.1](https://github.com/yawkat/lz4-java/releases/tag/v1.12.1).
- [Firebase Android release notes](https://firebase.google.com/support/release-notes/android), [Crashlytics report customization](https://firebase.google.com/docs/crashlytics/customize-crash-reports?platform=android).
- [Bouncy Castle current download/release inventory](https://www.bouncycastle.org/download/bouncy-castle-java/). Retained local linkage/compatibility notes: `docs/workspace-cleanup-20261009/reports/ANDROID-BOUNCYCASTLE-LINKAGE-AUDIT-20260924.md` and the associated KYC probes.

Crypto follow-up sources: [BC 1.86 release](https://www.bouncycastle.org/resources/new-release-bouncy-castle-java-1-86/), [JMRTD 0.8.9 publisher POM](https://repo.maven.apache.org/maven2/org/jmrtd/jmrtd/0.8.9/jmrtd-0.8.9.pom), [RFC 8032](https://www.rfc-editor.org/rfc/rfc8032.html).
