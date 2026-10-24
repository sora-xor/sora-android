# Lazysodium 5.0.2 native 16 KB replacement: candidate evidence

Status: **materialized-unreviewed; production blocked**. This is a native
component qualification within a production-flavor Debug build, not a protected
signed and minified Release or complete wallet test.

The app still resolves `com.goterl:lazysodium-android:5.0.2` through its locked
`:common` dependency. The source-qualified local Maven repository now supplies
the same coordinate. Its POM is the original published POM and its AAR differs
from the published AAR in only the two 64-bit `libsodium.so` members. The
original `classes.jar`, manifest, resources, and 32-bit libraries are unchanged.
The [pinned recipe](../../../vendor/soramitsu-maven/build-inputs/lazysodium-5.0.2-16kb-rebuild.md)
records the signed libsodium 1.0.18 source archive, source tag, NDK and
Autotools settings, rebuild outputs, and independent rebuild checks.

| Input or output | SHA-256 |
| --- | --- |
| Published Lazysodium 5.0.2 AAR | `e38503013e03a3623bd9da01a0fbbf644a87947a35dbeb4df7b35605b71534ad` |
| Published unchanged POM | `6ae9391bb9f4200e9a104d60bbc00a4c85a726c87d1ba46485fa7fd3679e6a88` |
| Signed libsodium 1.0.18 archive | `6f504490b342a4f8a4c4a02fc9b866cbef8622d5df4e5452b46be121e46636c1` |
| Rebuilt arm64-v8a `.so` | `917eb22551c9792b0b5316a8dae614d7a68c2232e28075ae73afeeac94a8e3cb` |
| Rebuilt x86_64 `.so` | `8e7278efed584d83f99b1a374443fdbc2612c60b0ff17b8aa5a5152d1a909b26` |
| Repacked AAR | `02eb05b96f8236b646f7f642c8e424cdb7f6ad6451d6b3c9eaec0e8c8138f989` |

Both rebuilt libraries pass the repository's ELF `PT_LOAD` and `GNU_RELRO`
16 KB checks. The libsodium API export set matches the old 64-bit libraries;
only old toolchain bookkeeping symbols are absent. A fresh run of the checked-in
build script reproduced both `.so` digests from a new archive extraction, and
the checked-in repack script reproduced the vendored AAR byte for byte.

The original Lazysodium 5.0.2 Java classes, with the app's JNA 5.17.0,
exercised `cryptoSecretBoxEasy`, authenticated open, and tampered-nonce rejection
through the rebuilt arm64 library on a verified 16 KB Android 16 emulator. The
original and rebuilt libraries produced byte-identical output on a separate
4 KB arm64 emulator. A direct native hash, secretbox, and deterministic signing
smoke also matched old and new outputs on 4 KB and passed with the replacement
on 16 KB. The original arm64 native library failed to load on the 16 KB emulator.
x86_64 has static, export, and reproducibility checks but no emulator run.

Strict offline `:app:assembleProductionDebug` and `:app:bundleProductionDebug`
passed with the combined RootBeer/Lazysodium overlay. The exact Debug APK and
AAB package both rebuilt `libsodium.so` digests. APK 16 KB ZIP alignment passes.
The source audit reports zero structural failures, dependency preflight reports
`STABLE` with six review blockers, 112 lock mutation tests and 11 native-gate
fixture tests pass. `:common` and `:app` production Debug unit tasks pass or
restore from Gradle cache. The whole-artifact native gate still rejects the APK
and AAB with **26 findings across six other ABI/library entries**.

| Artifact | SHA-256 |
| --- | --- |
| Production-flavor Debug APK | `5a62cbae3a86ac6fe5132101068ce02e64bee8d7046c6616a723a3427290ff43` |
| Production-flavor Debug AAB | `fa4ed6af31af03dd92a2a078dab4f32461f99181805e8458d8b9ce57802ecc4f` |

Independent source-to-binary and license/SBOM review, x86_64 runtime testing,
signed Release app startup and wallet/KYC flows, and the remaining graphics,
CameraX, and TensorFlow Lite native fixes remain open. `productionAllowed=false`
is unchanged.
