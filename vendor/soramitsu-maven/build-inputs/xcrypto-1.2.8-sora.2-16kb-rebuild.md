# Security fork with 16 KB JNI alignment

The October 24 security fork fixes Ed25519 verification and backports the Dalek and Rand
native fixes. Its original JNI binaries use 4 KB alignment. The October 25 consolidation
rebuilds only the arm64-v8a and x86_64 JNI entries with 16 KB alignment. Managed classes,
source JAR, dependencies, and both 32-bit JNI entries retain the original security-fork bytes.

The original security AAR is retained as `xcrypto-1.2.8-sora.2-original.aar`, SHA-256
`dee65fa2e0aef012a9ac591638da02bf89efc7f77a5969e2e0d2bbb7fdeebdf1`.

## Reproduce

Prepare source revision `2346144a127c1121ae3166800b7ab06ed9c5bf20` using both security
patches, both original crate archives, and the exact `xcrypto-1.2.8-sora.2-Cargo.lock`,
as described in `scripts/qualification/crypto-security/README.md`. Do not substitute
the unpatched 1.2.7 native libraries: that would lose the security fixes.

Use Rust/Cargo 1.97.1, Android NDK 28.0.12674087, and API 26 linker wrappers. From
the prepared `sr25519-java` directory, with `ANDROID_SDK_ROOT` set to the local SDK:

```sh
CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ANDROID_SDK_ROOT/ndk/28.0.12674087/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android26-clang" RUSTFLAGS='-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384' cargo +1.97.1 build --release --target aarch64-linux-android --locked --offline
CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$ANDROID_SDK_ROOT/ndk/28.0.12674087/toolchains/llvm/prebuilt/darwin-x86_64/bin/x86_64-linux-android26-clang" RUSTFLAGS='-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384' cargo +1.97.1 build --release --target x86_64-linux-android --locked --offline
```

From the Android repository, with `XCRYPTO_SRC` set to the prepared source checkout:

```sh
python3 vendor/soramitsu-maven/build-inputs/xcrypto-1.2.8-sora.2-repack-16kb.py \
  vendor/soramitsu-maven/build-inputs/xcrypto-1.2.8-sora.2-original.aar \
  "$XCRYPTO_SRC/sr25519-java/target/aarch64-linux-android/release/libsr25519java_1.so" \
  "$XCRYPTO_SRC/sr25519-java/target/x86_64-linux-android/release/libsr25519java_1.so" \
  /private/tmp/xcrypto-security-16kb.aar
```

The script checks every input hash, preserves ZIP metadata and entry order, verifies that
only the two intended JNI members changed, and checks the exact output digest. The
current Gradle module metadata updates the AAR size and hashes only.

Expected output SHA-256: `021e37c8b6f1b9c49a80a2b81af3094de4519b06f75a2fc82f57161630550f7f`.
Expected arm64 JNI SHA-256: `ef74ddc0640a676ad3b9b2b96d066b9001f72281a471cbdefd412f79020e9a45`.
Expected x86_64 JNI SHA-256: `bcc7d06342cd10496492197daa4a47752c161ad92f4cf0b1ffc56e13e2205649`.

Both libraries pass the repository's ELF `PT_LOAD` and `GNU_RELRO` 16 KB checks.
The retained scalar-boundary and Rand callback regressions pass for both scalar backends.
These checks do not replace independent source-to-binary/license review or release approval.
