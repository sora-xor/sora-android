# xcrypto

SORA's cryptography library: BIP-39, sr25519, Ed25519, ECDSA, scrypt,
XSalsa20-Poly1305 and hashing. It is built from source here, replacing the
`jp.co.soramitsu:xcrypto:1.2.8-sora.2` AAR that used to be vendored in
`vendor/soramitsu-maven`.

- `src/main/java`: the Kotlin/Java sources of that release, without the files the app
  cannot reach (see "Removed sources" below). They are
  https://github.com/soramitsu/x-crypto at `2346144a127c1121ae3166800b7ab06ed9c5bf20`
  plus the SORA security patches, which replace the EdDSA library with BouncyCastle
  Ed25519. The files that remain are unchanged, except that `Sr25519JNI` loads
  `libsora_sr25519.so`.
- `src/main/jni`: the sr25519 JNI library, built by Gradle with ndk-build for
  arm64-v8a, armeabi-v7a, x86 and x86_64. It has 16 KB page alignment.
  - `sr25519/`: the same C sources as sora-ios
    `VendorPackages/shared-features-spm/Sources/sr25519lib`. That is sr25519-donna
    (Apache-2.0) under a schnorrkel-compatible API; see `sr25519/README.md`.
  - `sr25519_jni.c`: the `Sr25519JNI` natives. It replaces the Rust `sr25519-java`
    library (schnorrkel 0.9.1) and keeps its byte formats and behaviour:
    - Invalid keys throw `java.lang.Exception`.
    - `verify` returns false for a malformed signature and throws for a public
      key that is not a point.
    - Two things are new: a chain code that is not 32 bytes throws instead of
      aborting the app, and the invalid-pair message no longer contains the key pair.

The C sources were compared with the old Rust/Rust-equivalent library outputs:
69,000 deterministic results for 3,000 random seeds and chain codes, in 64-bit and
32-bit arithmetic, plus cross-verification of signatures. The harness is
sora-ios `VendorPackages/shared-features-spm/Tests/CryptoCompatibility`. On Android,
`Sr25519NativeContractTest`, `HistoricalSr25519NativeParityTest` and
`CryptoRuntimeCompatibilityTest` check the packaged library on a device.

## Removed sources

These parts of the release were deleted because nothing can reach them: no source in this
repository (any module, flavor or test, ProGuard rules, build scripts, the JNI code)
names them, no class file in the vendored binaries or the locked third-party
dependencies refers to their packages, and nothing loads them by reflection,
`ServiceLoader` or JNI `FindClass`. The app compiles without them, and the key-derivation,
mnemonic, sr25519/ed25519 and JSON-keystore tests pass unchanged.

- `bip32/` (33 files): NovaCrypto's BIP32 extended keys, derivation and networks.
- `bip39/wordlists/` (English, French, Japanese, Spanish). Mnemonics use
  `seed/EnglishWordList.kt`.
- The rest of NovaCrypto's BIP39 apart from what `seed/MnemonicCreator.kt` uses:
  `MnemonicValidator`, `SeedCalculator`, `SeedCalculatorByWordListLookUp`, the three
  `*PBKDF2WithHmacSHA512` classes, `Normalization`, `NFKDNormalizer`,
  `WordListMapNormalization`, `Words`, `CharSequenceComparators`, `CharSequenceSplitter`,
  `Validation/*` and `hashing/Hash160`. Seeds come from `seed/SeedCreator.kt`
  (BouncyCastle PBKDF2), and phrases are checked by `MnemonicCreator`.
- `encryption/xsalsa20poly1305/SimpleBox.kt`. JSON keystores use `SecretBox` through
  `XSalsa20Poly1305Cryptor`.

Unused members inside the files that remain (`MnemonicCreator.fromEntropy`,
`ECDSAUtils.decompressed*`, `Keys.generatePrivateKey`) were kept: they sit in the
key-generation and encryption code, and the files stay as released.

## Licenses

- `bip39/` (the nine files that remain: `MnemonicGenerator`, `WordList`, `ByteUtils`,
  `CheckedExceptionToRuntime`, `SecureString/*` and `hashing/Sha256`) and
  `encoding/base58/` are NovaCrypto's BIP39 and Base58 libraries
  (https://github.com/NovaCrypto), GPL-3.0-or-later as their file headers say. The
  x-crypto release kept them unchanged, so the AAR the app shipped before contained the
  same code. NovaCrypto's BIP32 library and the rest of its BIP39 code were removed (see
  above).
- The rest of `src/main/java` is Soramitsu's x-crypto. Its repository is private and has
  no license file; the release's sources jar was already published in
  `vendor/soramitsu-maven` before this module replaced it.
- `src/main/jni/sr25519/donna` is sr25519-donna, Apache-2.0 (`donna/LICENSE`).

The app itself is Apache-2.0. Shipping the GPL-3.0 parts makes the distributed app
subject to GPL-3.0 as a whole, which was already true of the binary it replaces.
