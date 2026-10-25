# Crypto security compatibility evidence

The `xcrypto:1.2.8-sora.2` artifact is a local security fork rebuilt from the complete
upstream tree at `soramitsu/x-crypto@2346144a127c1121ae3166800b7ab06ed9c5bf20`.
It preserves the upstream source/license headers and the 32-byte Ed25519 private seed API.
The retained patch replaces the old EdDSA implementation with BC's RFC 8032 implementation,
removes that dependency, selects BC 1.86/LZ4 1.12.1, changes the local artifact version,
omits an unused Javadoc publication, and removes a Rust macro's trailing semicolon so the
same JNI source compiles with Rust 1.97.1. The subsequent native patch preserves the sr25519 math and API while adding the upstream scalar
optimization barriers and disabling internal Rand logging callbacks; it removes unused zeroize_derive 0.7.0.

Rebuild in a new checkout (JDK 17, Android SDK 34/NDK 25.2.9519653, Rust 1.97.1 with
armv7-linux-androideabi, aarch64-linux-android, i686-linux-android and x86_64-linux-android targets):

```sh
git checkout --detach 2346144a127c1121ae3166800b7ab06ed9c5bf20
git apply "$ANDROID_REPO/vendor/soramitsu-maven/build-inputs/xcrypto-1.2.8-sora.1-security.patch"
mkdir -p sr25519-java/vendor-security
tar -xf "$ANDROID_REPO/vendor/soramitsu-maven/build-inputs/curve25519-dalek-2.1.3.crate" -C sr25519-java/vendor-security
tar -xf "$ANDROID_REPO/vendor/soramitsu-maven/build-inputs/rand-0.7.3.crate" -C sr25519-java/vendor-security
git apply "$ANDROID_REPO/vendor/soramitsu-maven/build-inputs/xcrypto-1.2.8-sora.2-native-security.patch"
cp "$ANDROID_REPO/vendor/soramitsu-maven/build-inputs/xcrypto-1.2.8-sora.2-Cargo.lock" sr25519-java/Cargo.lock
./gradlew :lib:testReleaseUnitTest :lib:publishReleasePublicationToScnRepoLocalRepository
```

After reproducing this original security publication, apply the 16 KB repack described in
`vendor/soramitsu-maven/build-inputs/xcrypto-1.2.8-sora.2-16kb-rebuild.md`. The current app
uses that repack, whose managed code and 32-bit JNI entries are unchanged.

The local publication is `lib/build/scnrepo/jp/co/soramitsu/xcrypto/1.2.8-sora.2`.
The AAR, sources JAR, POM and module metadata are retained in the app's exact-filtered vendor
repository and bound by `SOURCE_PROVENANCE.json`, `CONTENTS.sha256` and Gradle verification.
The original upstream tree has no top-level license file; existing per-file copyright/license
notices are retained in the sources JAR. This observation does not close the existing license
or independent source-to-binary review gates.

`GenerateLegacyXcryptoVectors.java` generated `common/src/test/resources/legacy-xcrypto-v1.json`
using the **old** AAR (SHA-256 `a701705120918cc3c66d7217590035c9d385466e1b00836191c917845e9ff56b`),
BC 1.78.1 and EdDSA 0.3.0 before replacing them. It contains sixteen deterministic public test
seeds and old SDK public/private keys and signatures, plus a genuine old-writer scrypt/XSalsa20
backup envelope. No user wallet or credential is used. Do not regenerate these fixtures with
the new SDK; that would destroy their value as old-writer compatibility evidence.

The focused JVM tests compare those exact retained bytes, RFC 8032 test vector 1, Substrate
Twox128 prefixes, SORA2/Iroha retained derivation inputs, and retained Iroha derivation behavior.
Negative tests cover S+L/S>=L, invalid encodings, small-order keys, malformed lengths, tampered
messages and backup authentication. The connected `CryptoSecurityCompatibilityTest` additionally
exercises the packaged provider and newly rebuilt native sr25519 JNI using a retained public
SORA2 mini-seed/public-key vector. These checks do not qualify live KYC/passport hardware,
production signatures, or a minified release APK.

Focused app verification (from this Android repository, JDK 17 and the configured SDK):

```sh
./gradlew :common:testDevelopDebugUnitTest \
  --tests jp.co.soramitsu.common.util.XcryptoSecurityCompatibilityTest \
  --tests jp.co.soramitsu.common.util.XcryptoHashCompatibilityTest \
  --tests jp.co.soramitsu.common.nexus.IrohaKeyDerivationTest \
  --dependency-verification=strict
./gradlew :app:compileDevelopDebugKotlin :app:assembleDevelopDebug \
  :app:assembleDevelopDebugAndroidTest --dependency-verification=strict
./gradlew :app:connectedDevelopDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=jp.co.soramitsu.sora.ux.CryptoSecurityCompatibilityTest \
  --dependency-verification=strict
python3 scripts/audit-runtime-dependencies.py --output /tmp/android-osv-current.json
```

The connected test runs on a fresh test emulator with public vectors only. It is included in the
local production device qualification suite; hosted Android CI has been retired. Maven OSV results do not cover the local fork's implementation;
the source patch, retained vectors, malicious-signature regression and packaged JNI test are
separate evidence. Full source-to-binary review and protected release admission remain required.

## Native source backports

The Dalek patch backports the exact underflow-mask optimization barriers from upstream
[415892a](https://github.com/dalek-cryptography/curve25519-dalek/commit/415892acf1cdf9161bd6a4c99bc2f4cb8fae5e6a)
and [b4f9e4d](https://github.com/dalek-cryptography/curve25519-dalek/commit/b4f9e4df92a4689fb59e312a21f940ba06ba7013)
for both serial scalar backends. It does not claim to backport subsequent refactoring in PR #662.
The Rand patch disables internal logging following
[50bf18a](https://github.com/rust-random/rand/commit/50bf18ab96c04b0312852d182f415228e77a2e4d):
all internal log macros are no-ops and the obsolete `log` feature is inert. The app did not enable
that feature before the patch, but the backport also prevents reentrant logger callbacks if a
future consumer enables it. Neither patch changes RNG output generation or scalar arithmetic.

Copy `native-regression` to a temporary directory and append the following paths to its
Cargo.toml, replacing `/path/to/sdk` with the patched SDK checkout:

```toml
[patch.crates-io]
curve25519-dalek = { path = "/path/to/sdk/sr25519-java/vendor-security/curve25519-dalek-2.1.3" }
rand = { path = "/path/to/sdk/sr25519-java/vendor-security/rand-0.7.3" }
```

Run `cargo test --locked --features u64_backend` and again with `u32_backend`. The retained
tests cover modular subtraction boundaries and a logger callback probe with `rand/log` enabled.
The unpatched Rand control produces seven logger callbacks; the patched crate produces zero.
This probes the vulnerable callback path, not an exploitation attempt. Compile the same harness's
Dalek package with `cargo rustc --locked -p curve25519-dalek --release --lib --features u64_backend
--target aarch64-linux-android -- --emit=llvm-ir`, repeating for each backend and all four Android
targets. Retained optimized IR excerpts show the volatile barriers and absence of conditional
branches in the examined scalar subtraction functions; this is compiler-specific evidence,
not a proof of constant-time behavior on every compiler or processor. The original October 24 JNI artifact used its existing dev profile. The October 25 consolidation
rebuilds the two 64-bit JNI libraries with release optimization and 16 KB linker alignment; its
recipe and exact artifact hashes are retained in the vendor build inputs.

`audit-native-cargo.py` queries every registry package plus the two patched local crates, retains
all OSV version matches and exits nonzero when matches remain. The raw matches for Dalek 2.1.3
and Rand 0.7.3 remain visible because these backports intentionally preserve upstream version
numbers and dependent APIs. Review their exact retained source diffs and compiler/test evidence
alongside the raw report; no advisory is suppressed or package renamed to avoid matching.
