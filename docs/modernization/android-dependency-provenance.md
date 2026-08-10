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

The finalized materialized dependency snapshot is bound by these aggregate identities:

- vendor source provenance SHA-256:
  `39264fee02d09548e04806fbffcdaedebef29ce4715f3aa804093e44b51f5118`
- vendor whole-tree manifest SHA-256:
  `d632afc3ebbd1d801a41d444d63c2879cb78c7241da3ed259667825a0c366c1f`
- strict verification metadata SHA-256:
  `0d4c6923096c5fc39eeae458b20f935a1ec53ac90beff390ac024f6fbf946615`
- 31-file lock-set SHA-256:
  `1b91b6168ff2c0ec74e0f239e90ff8125742ae8a8fea3458f2a7cbfe472eb9ea`
- 271-entry configuration inventory SHA-256 (270 `productionRelease` entries plus the settings
  catalog configuration):
  `d35d7512074d387ae71dfad3f52833af70875e3afe4a84d46b667a31e3bbfd1f`

These identities record the frozen bytes accepted by the structural gate. They are not reviewer
signatures, attestations, or evidence that the snapshot is production-qualified.

The 31 lock inputs are the 30 module-local `gradle.lockfile` files plus the root
`settings-gradle.lockfile`; an inventory that searches only for the literal basename
`gradle.lockfile` will therefore report 30 and is incomplete.

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
