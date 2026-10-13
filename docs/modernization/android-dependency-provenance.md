# Android dependency provenance

Production Android dependency resolution no longer depends on the unavailable
`nexus.iroha.tech` repository. The exact private/package artifacts required by the current source
are materialized under `vendor/soramitsu-maven` and isolated with exact `includeModule` filters in
`settings.gradle.kts`. The repository admits Gradle module metadata and Maven POM metadata only;
it has no artifact-only fallback.

## Stable materialized inventory

| Coordinate | Origin identity | Current evidence state |
| --- | --- | --- |
| `jp.co.soramitsu:xcrypto:1.2.7` | `soramitsu/x-crypto@2346144a127c1121ae3166800b7ab06ed9c5bf20` | source-built, hashes pinned |
| `jp.co.soramitsu:xsubstrate:1.2.7` | `soramitsu/x-substrate@f020817a10590f94d6e7c75d040557e1b9afad56` | source-built with the preserved dependency-coordinate normalization patch |
| `jp.co.soramitsu:xbackup:1.2.3` | `soramitsu/x-backup@851999f4fb3d8610c500c4879cb4bcc7082aae55` | source-built, hashes pinned |
| `jp.co.soramitsu:ui-core:0.2.39` | `soramitsu/android-ui-libraries@f2b6f27adef101fa997432e46886dcbf3625ac77` | source-built, hashes pinned |
| `jp.co.soramitsu.xnetworking:lib-android:1.0.15-K2` | `soramitsu/x-networking@fa94a1d1c0b5457b50885ec72727934a2fea12d5` | source-built, hashes pinned |
| `jp.co.soramitsu:android-foundation:0.0.4` | `soramitsu/android-foundation@732d87fde58a2662a55b2d8b71825d6de7ba6702` | source-built, hashes pinned |
| `jp.co.soramitsu:android-sora-card:1.2.1-K2` | `sora-xor/sora-card-android@60113f80237a7c6fa4329eca55ddb22d89e12831` | source-built, hashes pinned |
| `io.emeraldpay.polkaj:polkaj-scale:0.2.3` | tracked at `soramitsu/sora2-load-library-java@4deeca68a5560462c13aec704a50e7ba02ece70e` | tracked binary/POM, hashes pinned |
| `com.paywings.oauth:android-sdk:2.0.0` | public PayWings GitHub Packages version page | publisher MD5/SHA-1 sidecars match; SHA-256 pinned |
| `com.paywings.kyc:android-sdk:1.2.2` | public PayWings GitHub Packages version page | publisher MD5/SHA-1 sidecars match; SHA-256 pinned |
| `com.paywings.onboarding.kyc.android-libs:idensic-mobile-sdk:1.31.3` | public PayWings GitHub Packages version page | publisher MD5/SHA-1 sidecars match; SHA-256 pinned |

`vendor/soramitsu-maven/SOURCE_PROVENANCE.json` binds every materialized filename and SHA-256 to
the exact source commit/tree or package-version page. `vendor/soramitsu-maven/CONTENTS.sha256`
covers every other regular file in the repository exactly once, in bytewise relative-path order,
and excludes only itself. Symbolic links, unmanifested files, missing files, checksum drift,
unexpected module coordinates, and publisher-sidecar mismatches fail dependency preflight.

The current materialized dependency snapshot (updated 2026-09-24) is bound by these aggregate identities:

- vendor source provenance SHA-256:
  `39264fee02d09548e04806fbffcdaedebef29ce4715f3aa804093e44b51f5118`
- vendor whole-tree manifest SHA-256:
  `d632afc3ebbd1d801a41d444d63c2879cb78c7241da3ed259667825a0c366c1f`
- strict verification metadata SHA-256:
  `8390862f04516357a66c51e761c4225204ed02fe9d1d8102687106dc13b0a039`
- 31-file lock-set SHA-256:
  `478dd2362dc67238df7bf381e75354ca4c4c082baca011fbe8be03c27db7d4f6`
- 464-entry configuration inventory SHA-256 (463 `productionRelease` entries plus the settings
  catalog configuration):
  `6a29d71b10b5cfe823ffa608a740dae1cb60ed2840b980a7c30265d671cfb927`

These identities record observed bytes accepted by the structural gate. Both dependency verification
and locking remain `materialized-unreviewed`, with `independentlyReviewed=false`. They are not reviewer
signatures, attestations, or evidence that the snapshot is production-qualified. The current metadata
contains 161 additional artifact entries across 89 added coordinates relative to the prior snapshot;
that full dependency set still needs independent review. The real old-writer/current-reader encrypted
storage experiment covers its declared adapter/DataStore/Tink closure only (see
`scripts/qualification/legacy-encrypted-preferences/README.md`). Historical release-probe and
interoperability fixtures retain their original source and frozen-input hashes.

The current sidecar SHA-256 is
`a11320aaae95f04a1b19adcfe561536c76b63b536e7c1e4ba8c2adcd7613f1e1`. The app lock records 17
configurations, including the empty `productionReleaseBaselineProfile` configuration; its SHA-256 is
`07349c4cc796f699e74aa15e32c7a00e3650cd7e12082cdb44bc84c8ec2c6abb`. The 2026-09-24 update
adds one verification-metadata entry and rebinds its observed hashes. Enabling the AGP 9
production Release unit-test task materializes 191 more locked configurations; three existing
app configurations also changed with the current Compose graph. These locks remain unreviewed.

The verification snapshot includes twelve transitive plugin and compile-classpath metadata files
first requested by the clean Linux CI resolver: Guava parent POMs `32.1.3-jre`, `33.0.0-jre`,
`33.2.1-jre`, and `33.3.1-jre`, JUnit BOM module metadata `5.9.2`, `5.10.2`, `5.11.0-M2`, and
`5.11.2`, the JUnit BOM POM `5.8.2`, Kotlin Gradle plugins BOM module/POM metadata `2.2.10`, and
the IntelliJ coroutines BOM POM `1.8.0-intellij-14`. Their SHA-256 values were matched
independently against Maven Central; no trusted-artifact exception or verification downgrade was
added.

The Linux-specific `com.android.tools.build:aapt2:9.2.1-15009934` executable JAR is also pinned at
SHA-256 `755f6727fb3f4cce5e319eac0f3618ed4b36b49a46d4bb2cbb6fa8e9175a54d6`. Direct HTTPS fetches
from both official Google Maven hostnames (`dl.google.com` and `maven.google.com`) produced the
same digest after the clean Linux connected-test resolver exposed the classifier-specific input.
No trusted-artifact exception or dependency-verification downgrade was added.

The Kotlin serialization BOM `1.8.0` POM is pinned at SHA-256
`c43e487529ccfd1209eba8653ab8e89598203464aafbfe5f0448ded992871509`.
The cached POM bytes and Maven Central's POM and SHA-256 sidecar agree. This adds one
strict metadata entry without changing the production lock set or claiming independent
review of the full dependency snapshot.

The 31 lock inputs are the 30 module-local `gradle.lockfile` files plus the root
`settings-gradle.lockfile`; an inventory that searches only for the literal basename
`gradle.lockfile` will therefore report 30 and is incomplete. `baselineprofile` is the sole test-only
exception: its offline Gradle model has 36 resolvable configurations and zero `productionRelease`
configurations, so the root production locking rule creates no module lock. The source gate retains
an exact thirty-project production inventory, and binds the baseline module, root build, settings,
and plugin catalog bytes. It also checks the test plugin, namespace, `:app` target and production
dimension selection. New plugins, namespaces, targets, configurations, root hooks or included projects
invalidate that exception; production modules cannot silently disappear from lock coverage. The
shared guard and its mutation tests are bound into the migration qualification source contract.

The xcrypto Rust lock state and the complete xsubstrate semantic normalization are retained in
`vendor/soramitsu-maven/build-inputs`. Temporary absolute staging-repository edits are not build
semantics and are not retained.

The pinned IDensic 1.31.3 graph also pins `org.tensorflow:tensorflow-lite:2.12.0` and
`org.tensorflow:tensorflow-lite-api:2.12.0`. Those two artifacts expose the same legacy manifest
namespace under AGP 9.2.1, so the repository currently carries the documented migration-only
`android.uniquePackageNames=false` compatibility property. The provenance policy binds that exact
property to those three coordinates. It is not a general namespace exception, dependency review,
or production qualification, and removal/upgrade remains part of the dependency review lane.

## Gate and remaining review

Run the isolated dependency gate without invoking Gradle:

```sh
node scripts/verify-production-modernization.mjs --dependency-preflight
```

The production qualification workflow runs this command after checkout before dependency-related
setup and again immediately before every Gradle invocation, including calls made in the clean
reproduction checkout and emulator runner. A stable inventory may still return a non-zero exit
because production review is deliberately separate from byte integrity.

The materialized inventory does **not** claim independent source-to-binary reproduction,
license/notice review, SBOM review, build-attestation review, dependency-verification metadata
review, production lock review, or credential-incident closure. `productionAllowed` remains false,
aggregate dependency provenance remains blocked, and production mutation/release gates must stay
closed until those reviews and the out-of-repository token revocation/rotation evidence are real.

Even after those source-state fields are updated, repository assertions do not qualify the review.
The production environment must also supply the canonical dual-P-256-signed manifest described in
`docs/modernization/qualification/android-dependency-signing-review-README.md`. Dependency preflight
admits it only against distinct protected producer/reviewer pins and independently protected exact
sequence, candidate source revision, and review-contract SHA-256. Qualified evidence is deliberately
absent from the checkout.

The current local observation is retained in
`docs/modernization/qualification/android-dependency-materialization-2026-09-06.json`. The release
source gate compares its materialization, public signing-fingerprint, review, credential and
qualification fields against the current policy. Its historical evidence path/hash binds the
untouched `docs/modernization/release-probe-evidence-2026-08-02.json`; no live network probe or
protected signing qualification is newly claimed.

In that record, `observedAtUtc` dates the new materialization observation; `retainedBaselineAssessedAt`
preserves the prior vendor/config assessment date. The new digests are not backdated to that baseline.
