# Lazysodium 5.0.2 native 16 KB rebuild

This materializes the existing `com.goterl:lazysodium-android:5.0.2` coordinate with
only its `jni/arm64-v8a/libsodium.so` and `jni/x86_64/libsodium.so` members replaced.
The original Java classes, manifest, resources, and all 32-bit JNI entries are
byte-identical. The Maven POM is the original Maven Central POM, including its
JNA 5.8.0 transitive declaration; the app separately pins JNA 5.17.0.

## Source and toolchain

- Original Maven Central AAR SHA-256:
  `e38503013e03a3623bd9da01a0fbbf644a87947a35dbeb4df7b35605b71534ad`.
  Its POM SHA-256 is
  `6ae9391bb9f4200e9a104d60bbc00a4c85a726c87d1ba46485fa7fd3679e6a88`.
- Original Lazysodium 5.0.2 tag: `https://github.com/terl/lazysodium-android`,
  commit `c740c3403e08c6060e632b1c6bbaa953d558995e`. Both original 64-bit
  libraries report libsodium `1.0.18`.
- Native source: official
  [libsodium 1.0.18 release archive](https://download.libsodium.org/libsodium/releases/old/libsodium-1.0.18.tar.gz),
  SHA-256 `6f504490b342a4f8a4c4a02fc9b866cbef8622d5df4e5452b46be121e46636c1`.
  Its detached `.sig` verified with signing subkey
  `0C7983A8FD9A104C623172CB62F25B592B6F76DA`. The archive's
  `src/libsodium` matches the signed `1.0.18-RELEASE` Git tag at commit
  `940ef42797baa0278df6b7fd9e67c7590f87744b`, tree
  `a748f0c89c2243e124df3d310915c8a528135582`. The signing key was
  fetched separately; its trust has not been independently certified.
- Android NDK r28 beta2, base revision `28.0.12674087`, Clang 19, Android API 21
  wrappers. This source's old `android-build.sh` requires the removed standalone
  toolchain generator, so the pinned recipe calls its Autotools `configure`
  directly with equivalent `--enable-minimal`, architecture `-march`, and
  `--disable-soname-versions` settings. It adds both 16 KB linker flags.

## Reproduce

```sh
curl -fL https://download.libsodium.org/libsodium/releases/old/libsodium-1.0.18.tar.gz -o libsodium-1.0.18.tar.gz
vendor/soramitsu-maven/build-inputs/lazysodium-5.0.2-build-16kb.sh \
  libsodium-1.0.18.tar.gz \
  /path/to/android-sdk/ndk/28.0.12674087 \
  /new/empty/output-directory
python3 vendor/soramitsu-maven/build-inputs/lazysodium-5.0.2-repack-16kb.py \
  vendor/soramitsu-maven/build-inputs/lazysodium-5.0.2-original.aar \
  /new/empty/output-directory/arm64-v8a/libsodium.so \
  /new/empty/output-directory/x86_64/libsodium.so \
  /new/empty/output-directory/lazysodium-android-5.0.2.aar
```

The build script checks the archive and native output SHA-256 values. Two
independent builds from the same extracted archive produced byte-identical
libraries for both ABIs. A third build from a fresh archive extraction did too.
The repack script checks all AAR members and the complete result SHA-256.

| ABI | Original `libsodium.so` SHA-256 | Rebuilt SHA-256 |
| --- | --- | --- |
| arm64-v8a | `af4cbdea2d58e11a5b6955409c87fa8994b0ee6e03a04780555675d8c2833717` | `917eb22551c9792b0b5316a8dae614d7a68c2232e28075ae73afeeac94a8e3cb` |
| x86_64 | `cc7a95f48f137a449dd50296145dd7f7ac8063b7fe1694745f18fa69f1017a61` | `8e7278efed584d83f99b1a374443fdbc2612c60b0ff17b8aa5a5152d1a909b26` |

The repacked AAR SHA-256 is
`02eb05b96f8236b646f7f642c8e424cdb7f6ad6451d6b3c9eaec0e8c8138f989`.
Both rebuilt 64-bit libraries pass the repository's `PT_LOAD`, 16 KB congruence,
and GNU RELRO end checks. Every original libsodium API export is retained;
only legacy toolchain bookkeeping exports are absent.

## Runtime evidence and limits

The pinned `LazysodiumSmoke.java` uses the original Lazysodium 5.0.2
Java classes and the app's JNA 5.17.0. It exercises `cryptoSecretBoxEasy`,
`cryptoSecretBoxOpenEasy`, and tampered-nonce rejection. On a verified 16 KB
arm64 Android 16 emulator, the rebuilt library passed. On a 4 KB arm64 Android
16 emulator, the original and rebuilt libraries passed with identical output:

```text
LAZYSODIUM_JNA_SMOKE_PASS EEA926490AD1EDD9AFE218135013AE61457FB51E5B14AC8EB7737B427C5EF0E8ECA52E1AA2D7
```

The 16 KB emulator fingerprint was
`google/sdk_gphone16k_arm64/emu64a16k:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys`;
the 4 KB fingerprint was
`google/sdk_gslim_arm64/emu64a:16/BE2A.250530.027/13847098:userdebug/dev-keys`.
The output files have SHA-256
`f1c73485a430bcac76c8a555275fba95d13706ff1794ea94af0219107e69ac67`.
Native C hash, secretbox, and deterministic signature vectors also matched
the original on the 4 KB emulator and passed with the rebuilt library on the
16 KB emulator.

x86_64 has static ELF, export, and reproducibility checks only; no x86_64
emulator was installed. This evidence does not qualify a signed production
Release, wallet migration, KYC flow, or the remaining native libraries.
Independent source-to-binary and license/SBOM review remain open.
