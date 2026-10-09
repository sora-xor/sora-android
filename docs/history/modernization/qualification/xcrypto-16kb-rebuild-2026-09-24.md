# Isolated xcrypto 16 KB rebuild experiment (2026-09-24)

The isolated experiment changed no tracked source or Android release-candidate files. The
separate integration worktree subsequently materialized its AAR under the existing xcrypto 1.2.7
coordinate with the [pinned rebuild recipe](../../../vendor/soramitsu-maven/build-inputs/xcrypto-1.2.7-16kb-rebuild.md).
It does not qualify the full app for production.

## Exact source and inputs

- Source: `https://github.com/soramitsu/x-crypto.git` at `2346144a127c1121ae3166800b7ab06ed9c5bf20`, tree `c0dab19ed314bbe9bf939dc0c33fe0c4d13fbb11`.
- Source checkout: `/private/tmp/sora-xcrypto-16kb-rebuild-20260924`; tracked files remained clean. Its untracked script and report recorded the experiment; Cargo target output and copied lockfile were ignored. The persistent recipe and repack script now live under `vendor/soramitsu-maven/build-inputs`.
- Copied `vendor/soramitsu-maven/build-inputs/xcrypto-1.2.7-Cargo.lock` into `sr25519-java/Cargo.lock`; SHA-256 `72e4aa8f2365dbdff249820abe7cf07593d63a371b220c34bad1733a5694d395`. This exactly matches the vendored source-build input.
- Rust/Cargo 1.82.0 (`rustc f6e511eec734`, `cargo 8f40fc59fb0c`), both Android Rust targets preinstalled; NDK `28.0.12674087`, Clang 19.0.0, Android API 26 linker wrapper.

## Rebuild commands

```sh
git clone --filter=blob:none --no-checkout https://github.com/soramitsu/x-crypto.git /private/tmp/sora-xcrypto-16kb-rebuild-20260924
git -C /private/tmp/sora-xcrypto-16kb-rebuild-20260924 checkout --detach 2346144a127c1121ae3166800b7ab06ed9c5bf20
cp /Users/takemiyamakoto/dev/sora-wallet/sora-android-release-candidate-20260924/vendor/soramitsu-maven/build-inputs/xcrypto-1.2.7-Cargo.lock /private/tmp/sora-xcrypto-16kb-rebuild-20260924/sr25519-java/Cargo.lock
cd /private/tmp/sora-xcrypto-16kb-rebuild-20260924/sr25519-java
CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=/Users/takemiyamakoto/Library/Android/sdk/ndk/28.0.12674087/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android26-clang RUSTFLAGS='-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384' cargo +1.82.0 build --release --target aarch64-linux-android --locked --offline
CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER=/Users/takemiyamakoto/Library/Android/sdk/ndk/28.0.12674087/toolchains/llvm/prebuilt/darwin-x86_64/bin/x86_64-linux-android26-clang RUSTFLAGS='-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384' cargo +1.82.0 build --release --target x86_64-linux-android --locked --offline
```

The old Rust source emits 26 `semicolon_in_expressions_from_macros` future-compatibility warnings but compiles successfully. The direct Rust build makes no semantic source changes. A second clean build with `CARGO_TARGET_DIR=/private/tmp/sora-xcrypto-16kb-repro2-target-20260924` produced byte-for-byte identical `.so` files for both targets.

## Native outputs and checks

| ABI | SHA-256 | Bytes | ELF check |
| --- | --- | ---: | --- |
| arm64-v8a | `74744f6eb2d7efefaf0d985d316b7db418997cea6fb5f43dc4bba85b5fb2cde2` | 545632 | `inspect_elf` returns `[]` |
| x86_64 | `1daa9af93389dc4c2ffb48d54eccdc71f207b415e913c8ec9d26a2ed047843b4` | 562920 | `inspect_elf` returns `[]` |

Both libraries have four `PT_LOAD` segments with `p_align=0x4000`, valid 16 KB offset/address congruence, and `GNU_RELRO` ends on a 16 KB boundary. Both retain the same nine unique exported JNI entrypoint names as their respective vendored originals. The vendored originals have 4 KB `PT_LOAD` alignment.

`repack-experimental.py` in this directory makes `/private/tmp/sora-xcrypto-16kb-rebuild-20260924/xcrypto-1.2.7-16kb-experimental.aar` (SHA-256 `bb2cbb157c36430a7843e8db6900fec1ce36cacd966ff8fe00c6eab524f853ef`). Comparison with the pinned original AAR (SHA-256 `a701705120918cc3c66d7217590035c9d385466e1b00836191c917845e9ff56b`) confirms that **only** `jni/arm64-v8a/libsr25519java_1.so` and `jni/x86_64/libsr25519java_1.so` entry contents differ. The two 32-bit libraries and all Java resources remain byte-identical.

## Runtime smoke

The isolated harness source and dex jar are in `/private/tmp/sora-xcrypto-16kb-jni-smoke-20260924`. Its `Sr25519JNI.java` is copied from the pinned source with only the static loader path changed to `/data/local/tmp/libsr25519java_1.so`. It calls keypair creation from seed `00..1f`, sign/verify, mutated-message rejection, Ed25519 secret conversion roundtrip, hard keypair derivation and sign/verify, and soft public derivation.

On `FearlessApi36Ps16k`, build fingerprint `google/sdk_gphone16k_arm64/emu64a16k:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys`, `adb shell getconf PAGE_SIZE` reported `16384`. Host/device SHA for the rebuilt arm64 library matched. `CLASSPATH=/data/local/tmp/xcrypto16k-smoke.jar app_process /system/bin Xcrypto16kSmoke` printed `XCRYPTO_16KB_JNI_SMOKE_PASS`. The pinned original library instead failed on that emulator with `program alignment (4096) cannot be smaller than system page size (16384)` and exit 137.

On `FearlessMigrationApi36Fresh20260730` (arm64, `PAGE_SIZE=4096`), the same smoke harness using the pinned original arm64 library passed. Four deterministic output SHA-256 values matched the rebuilt library on the 16 KB emulator: pair `d3316accba01e647adb84940b451097cec1f5a4daeb11451898cd04cb5fd391e`, hard pair `c286b53b33f0541a9adf44dea7186dcc1db81f0f7bf66289ad3f3466930e5c0a`, soft public `0e78c1f5bde2c0517688fd5be608c91858ce9b71c582275c23f76eeec94367fb`, Ed25519 secret `2162e8f7362176a7079135a5e7501b13e59678bf34c9ed81eeec1b968a08ee5f`.

These are JNI-level experiments with raw native libraries, not signed production APK installation or full wallet migration/signing tests. x86_64 was checked statically and rebuilt reproducibly but no x86_64 emulator image was installed for runtime exercise. The repacked AAR is not yet an independently reviewed source-to-binary release artifact. The Android candidate pins it as `materialized-unreviewed`; release admission remains blocked.

## Separate JNA 5.17.0 observation

The current candidate `productionDebug` APK SHA-256 `53aea3e9714556658a7cd00ac2f2bb5f02938d6df2a5bc8af41fe8e28c9dcd09` packaged arm64 JNA `.so` SHA-256 `abc26e994517bcaa3309acdb0a27373864086c7569c89d3087b8626fada9ef06`, byte-identical to its cached Maven 5.17.0 AAR entry. Both packaged 64-bit JNA libraries pass the repo's `inspect_elf`. Pushing the packaged arm64 `.so` onto the same 16 KB emulator and calling `System.load` in a minimal `app_process` harness printed `JNA_16KB_DLOPEN_PASS`. This confirms raw native load, not JNA API integration or APK startup. The temporary emulators were shut down after testing.
