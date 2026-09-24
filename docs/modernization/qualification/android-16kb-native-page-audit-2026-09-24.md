# Android 16 KB native page audit — 2026-09-24

Status: **blocked for 16 KB devices**. This is an artifact inspection, not a signed
production Release qualification.

## Evidence

- Checkout HEAD inspected: `13abd5765452aad11620794c9db7a674cad834d4`.
- Artifact: `app/build/outputs/apk/production/debug/SORA_Wallet_3.8.6.3_122_production_debug.apk`.
  SHA-256: `9d2f07bf686471f46c681a8d1f56473e132c6fe161c6433e11cdac134043a2d2`.
  This is the production **flavor** with the debug build type. No protected, signed,
  minified `productionRelease` APK was available for inspection.
- The APK contains eight `arm64-v8a` and eight `x86_64` shared libraries. Android
  Build Tools 36 `zipalign -c -P 16 -v 4` returned `Verification successful`;
  all 16 of those entries are stored with data offsets divisible by 16,384.
  ZIP alignment does not repair an ELF program header.
- NDK r28 `llvm-readelf -lW` found the following `PT_LOAD` segment alignment of
  `0x1000` (4 KB). Every row must reach at least `0x4000` (16 KB) before the
  package can be qualified for 16 KB devices.

| Library in APK | Failing 64-bit ABI(s) | Resolved source artifact |
| --- | --- | --- |
| `libimage_processing_util_jni.so` | `arm64-v8a`, `x86_64` | `androidx.camera:camera-core:1.3.1` |
| `libsr25519java_1.so` | `arm64-v8a`, `x86_64` | vendored `jp.co.soramitsu:xcrypto:1.2.7` |
| `libtensorflowlite_jni.so` | `arm64-v8a`, `x86_64` | `org.tensorflow:tensorflow-lite:2.12.0` |
| `libtoolChecker.so` | `arm64-v8a`, `x86_64` | `com.scottyab:rootbeer-lib:0.1.0` |
| `libjnidispatch.so` | `x86_64` | `net.java.dev.jna:jna:5.8.0` |
| `libsodium.so` | `x86_64` | `com.goterl:lazysodium-android:5.0.2` |

Thus **10 of 16** packaged 64-bit ABI/library entries have 4 KB `PT_LOAD`
alignment. The other six have `PT_LOAD` alignment of `0x4000` or `0x10000`.
Release lint reports 20 `Aligned16KB` instances but only three unique
ABI/library pairs: xcrypto `arm64-v8a`, JNA `x86_64`, and lazysodium `x86_64`.
Direct APK inspection additionally detects both ABIs of CameraX, TensorFlow
Lite, and RootBeer, plus xcrypto `x86_64`.

The `productionReleaseRuntimeClasspath` dependency graph resolves the same six
source artifacts. xcrypto is used directly by `:app` and `:common`; JNA and
lazysodium are direct `:common` dependencies. CameraX, TensorFlow Lite, and
RootBeer arrive through
`jp.co.soramitsu:android-sora-card:1.2.1-K2` →
`com.paywings.kyc:android-sdk:1.2.2` →
`com.paywings.onboarding.kyc.android-libs:idensic-mobile-sdk:1.31.3`.
The IDensic vendor POM declares those three transitive dependencies. The
repository's verification metadata pins the currently inspected artifacts;
`vendor/soramitsu-maven` contains only xcrypto 1.2.7, not a reviewed aligned
replacement.

## Reproduce

From the repository root:

```sh
APK=app/build/outputs/apk/production/debug/SORA_Wallet_3.8.6.3_122_production_debug.apk
shasum -a 256 "$APK"
"$HOME/Library/Android/sdk/build-tools/36.0.0/zipalign" -c -P 16 -v 4 "$APK"

READELF="$HOME/Library/Android/sdk/ndk/28.0.12674087/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-readelf"
TMP_NATIVE="$(mktemp)"
for ABI in arm64-v8a x86_64; do
  unzip -Z1 "$APK" | rg "^lib/$ABI/.*[.]so$" | while IFS= read -r ENTRY; do
    unzip -p "$APK" "$ENTRY" > "$TMP_NATIVE"
    printf '%s: ' "$ENTRY"
    "$READELF" -lW "$TMP_NATIVE" | awk '$1 == "LOAD" {print $NF}' | sort -u | paste -sd, -
  done
done
rm "$TMP_NATIVE"

JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :app:dependencyInsight --configuration productionReleaseRuntimeClasspath \
  --dependency rootbeer-lib --offline --dependency-verification strict --console=plain
```

Repeat the last command with `camera-core` and `tensorflow-lite` to trace the
other IDensic artifacts. All three dependency insight commands succeeded.

## Release impact and gates

The app targets API 36 (`app/build.gradle.kts`). The [Android 16 KB page
guide](https://developer.android.com/guide/practices/page-sizes) says that Play
apps targeting API 35 or higher must support 16 KB pages on 64-bit devices;
starting **2027-02-01**, incompatible app updates cannot be released. The guide
also requires 16 KB ELF alignment for prebuilt shared libraries and says to
recompile and reimport incompatible prebuilts. This is already a device
compatibility problem, irrespective of the future Play enforcement date.

1. Rebuild the pinned xcrypto source with a 16 KB-capable native toolchain and
   independently recheck source-to-binary provenance and wallet crypto behavior.
2. Obtain and independently review compatible JNA and lazysodium AARs. Obtain
   a compatible PayWings/IDensic dependency set (or vendor-approved component
   replacements) for CameraX, TensorFlow Lite, and RootBeer. Update the
   dependency locks, checksums, vendor provenance, and protected review receipts
   only for artifacts actually selected and tested. No reviewed same-repository
   replacement currently establishes this gate.
3. Inspect **every** `arm64-v8a` and `x86_64` `.so` in the exact signed/minified
   `productionRelease` APK and Play bundle output: 16 KB ZIP alignment, ELF
   `PT_LOAD` alignment, and the guide's `GNU_RELRO` end-alignment check. Run
   wallet creation/import, key operations, camera/KYC, and app startup on a
   16 KB device or emulator verified with `adb shell getconf PAGE_SIZE` =
   `16384`, retaining results against the signed candidate digest.

The `GNU_RELRO` gate needs particular attention. An exploratory check on this
debug APK found nonzero `(VirtAddr + MemSiz) % 0x4000` for many 64-bit libraries,
including `arm64-v8a/libandroidx.graphics.path.so`, whose RELRO end is `0x6000`
(`0x2000` modulo `0x4000`) even though its `PT_LOAD` segments are 16 KB aligned.
The [same Android guide](https://developer.android.com/guide/practices/page-sizes)
warns that RELRO mismatch can cause a runtime segmentation fault. No 16 KB
device run or protected Release artifact has validated this observation; it is
an additional unresolved runtime risk, distinct from the conclusive `PT_LOAD`
failures above.
