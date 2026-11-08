# Crypto security compatibility evidence

xcrypto is built from source in this repository, as the `:xcrypto` module (`xcrypto/README.md`).
Its Kotlin/Java sources are those of the former `xcrypto:1.2.8-sora.2` security fork:
`soramitsu/x-crypto@2346144a127c1121ae3166800b7ab06ed9c5bf20` plus the SORA patch that replaces
the old EdDSA implementation with BouncyCastle's RFC 8032 Ed25519 (BC 1.86, LZ4 1.12.1).

The sr25519 JNI library is no longer the Rust `sr25519-java` crate (schnorrkel 0.9.1 with
backported Dalek/Rand fixes). It is `libsora_sr25519.so`, built by Gradle with ndk-build from the
C sources in `xcrypto/src/main/jni`. Those are sr25519-donna (Apache-2.0) under a
schnorrkel-compatible API, the same files the iOS app builds. Byte formats and Java-visible
behaviour are unchanged. Evidence:

- sora-ios `VendorPackages/shared-features-spm/Tests/CryptoCompatibility` checks 69,000
  deterministic outputs for 3,000 seeded cases (64- and 32-bit arithmetic) against the old
  library: keypairs, hard/soft/chained derivation and ed25519 conversions. Signatures are
  cross-verified both ways, and it also runs RFC 9496 Ristretto vectors and Substrate's
  //Alice and //Bob keys.
- The same harness, built with NDK r28 for arm64, reproduced every digest on an Android 36
  emulator with 16 KB pages. All 300 retained signatures of the old library verified there.
- The connected `Sr25519NativeContractTest`, `HistoricalSr25519NativeParityTest`,
  `CryptoRuntimeCompatibilityTest` and `CryptoSecurityCompatibilityTest` exercise the packaged
  library on a device.

The Rust fork's rebuild inputs, native regression crate, Cargo audit and LLVM IR capture are in
git history; they no longer describe anything the app ships.

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
