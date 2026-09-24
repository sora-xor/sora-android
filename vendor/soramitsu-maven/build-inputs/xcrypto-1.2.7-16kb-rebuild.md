# xcrypto 1.2.7: 16 KB JNI rebuild input and recipe

This recipe changes **only** `jni/arm64-v8a/libsr25519java_1.so` and
`jni/x86_64/libsr25519java_1.so` inside the pinned xcrypto 1.2.7 AAR. The Rust source and
`Cargo.lock` are unchanged. `xcrypto-1.2.7-original.aar` retains the previous AAR as a complete
repack input; its SHA-256 is
`a701705120918cc3c66d7217590035c9d385466e1b00836191c917845e9ff56b`.

## Pinned build inputs

- Source: `https://github.com/soramitsu/x-crypto.git`, commit
  `2346144a127c1121ae3166800b7ab06ed9c5bf20`, tree
  `c0dab19ed314bbe9bf939dc0c33fe0c4d13fbb11`.
- Lockfile: `xcrypto-1.2.7-Cargo.lock`, SHA-256
  `72e4aa8f2365dbdff249820abe7cf07593d63a371b220c34bad1733a5694d395`.
- Rust and Cargo: `1.82.0` (`rustc f6e511eec734`, `cargo 8f40fc59fb0c`). Install both
  `aarch64-linux-android` and `x86_64-linux-android` Rust targets.
- Android NDK: `28.0.12674087`, Clang `19.0.0`, Darwin x86_64 host toolchain, API 26 target
  linker wrappers. The Android SDK path is host-specific; the NDK revision and linker arguments
  are build inputs.
- Cargo dependencies: exact versions/checksums in the lockfile. The observed builds used Cargo's
  already populated cache with `--locked --offline`; no dependency resolution update occurred.
- Repack input: original AAR above. The original `-sources.jar`, `.pom`, and Gradle module
  dependency declarations stay unchanged.

## Reproduce

From the Android repository root on a macOS host with these toolchains installed, set the SDK
path for that host and run:

```sh
ANDROID_SDK_ROOT=/Users/takemiyamakoto/Library/Android/sdk
XCRYPTO_SRC=/private/tmp/sora-xcrypto-16kb-reproduction
git clone --filter=blob:none --no-checkout https://github.com/soramitsu/x-crypto.git "$XCRYPTO_SRC"
git -C "$XCRYPTO_SRC" checkout --detach 2346144a127c1121ae3166800b7ab06ed9c5bf20
test "$(git -C "$XCRYPTO_SRC" rev-parse HEAD^{tree})" = c0dab19ed314bbe9bf939dc0c33fe0c4d13fbb11
cp vendor/soramitsu-maven/build-inputs/xcrypto-1.2.7-Cargo.lock "$XCRYPTO_SRC/sr25519-java/Cargo.lock"
cd "$XCRYPTO_SRC/sr25519-java"
CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ANDROID_SDK_ROOT/ndk/28.0.12674087/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android26-clang" RUSTFLAGS='-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384' cargo +1.82.0 build --release --target aarch64-linux-android --locked --offline
CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$ANDROID_SDK_ROOT/ndk/28.0.12674087/toolchains/llvm/prebuilt/darwin-x86_64/bin/x86_64-linux-android26-clang" RUSTFLAGS='-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384' cargo +1.82.0 build --release --target x86_64-linux-android --locked --offline
```

Return to the Android repository root, then repack and compare with the checked-in artifact:

```sh
python3 vendor/soramitsu-maven/build-inputs/xcrypto-1.2.7-repack-16kb.py \
  vendor/soramitsu-maven/build-inputs/xcrypto-1.2.7-original.aar \
  "$XCRYPTO_SRC/sr25519-java/target/aarch64-linux-android/release/libsr25519java_1.so" \
  "$XCRYPTO_SRC/sr25519-java/target/x86_64-linux-android/release/libsr25519java_1.so" \
  /private/tmp/xcrypto-1.2.7-16kb-reproduced.aar
cmp /private/tmp/xcrypto-1.2.7-16kb-reproduced.aar \
  vendor/soramitsu-maven/jp/co/soramitsu/xcrypto/1.2.7/xcrypto-1.2.7.aar
```

Expected SHA-256: arm64-v8a
`74744f6eb2d7efefaf0d985d316b7db418997cea6fb5f43dc4bba85b5fb2cde2`, x86_64
`1daa9af93389dc4c2ffb48d54eccdc71f207b415e913c8ec9d26a2ed047843b4`, repacked AAR
`bb2cbb157c36430a7843e8db6900fec1ce36cacd966ff8fe00c6eab524f853ef`. A second clean
Cargo target-directory build yielded byte-identical native outputs. Both rebuilt ELF files pass
the repository's 16 KB `inspect_elf` check, including `PT_LOAD` and `GNU_RELRO` alignment.

The [isolated experiment report](../../../docs/modernization/qualification/xcrypto-16kb-rebuild-2026-09-24.md)
records the 16 KB arm64 emulator JNI smoke and comparison with the original library on a 4 KB
emulator. This materialization remains `materialized-unreviewed` and is **not** a release
qualification. The rest of the app's native libraries and signed APK/AAB still require their
own 16 KB checks and protected runtime qualification.
