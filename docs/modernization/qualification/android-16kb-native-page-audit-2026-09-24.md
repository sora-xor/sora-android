# Android 16 KB native page audit — 2026-09-24

Status on 25 September: the current production-flavor **Debug** APK and AAB pass
the strict native gate for all 16 packaged 64-bit libraries, and the APK passes
16 KB ZIP alignment. The protected signed/minified `productionRelease` artifact,
independent source-to-binary review, and retained-device wallet/KYC flows remain
open. The baseline and intermediate failures below are a chronological record,
not findings against the current Debug artifacts.

At `2aa1a378`, strict offline builds produced APK SHA-256
`feabcb62e1d884d49362716faff9a3b63da0e084c189258c9c949361ffde5665`
and AAB SHA-256
`1fd381ecfeb039240b61c000ae96bc9ad2546614f48dcb0329129836b6b5c83a`.
Both pass `scripts/verify-android-native-16kb.py` with zero findings across 16
entries. Exact-version replacement evidence for graphics-path, CameraX, and
TensorFlow Lite is in their companion qualification reports. The Debug APK
cold-launched to onboarding on an API 36 arm64 emulator reporting 16,384-byte
pages without a fatal process error. A later [bounded app-flow smoke](android-16kb-app-flow-smoke-2026-09-25.md)
used the same APK to navigate creation and recovery entry with test-only data;
it found no fatal process, native-load, or JNI error.

## Baseline evidence

- Checkout HEAD inspected: `13abd5765452aad11620794c9db7a674cad834d4`.
- Artifact: `app/build/outputs/apk/production/debug/SORA_Wallet_3.8.6.3_122_production_debug.apk`.
  SHA-256: `9d2f07bf686471f46c681a8d1f56473e132c6fe161c6433e11cdac134043a2d2`.
  This is the production **flavor** with the debug build type. No protected, signed,
  minified `productionRelease` APK was available for inspection.
- A strict local `:app:bundleProductionDebug` produced
  `app/build/outputs/bundle/productionDebug/app-production-debug.aab`, SHA-256
  `c73f59f917a393b6d7768d741f06ad620dd4af7f357675c99fc6c51d230dc221`.
  Its `BundleConfig.pb` sets `uncompressNativeLibraries.enabled=1` and
  `alignment=2` (`PAGE_ALIGNMENT_16K`). The AAB still contains the same 16
  incompatible 64-bit ELF entries; the new artifact gate reports 65 findings.
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

The baseline `productionReleaseRuntimeClasspath` dependency graph resolves the same six
source artifacts. xcrypto is used directly by `:app` and `:common`; JNA and
lazysodium are direct `:common` dependencies. CameraX, TensorFlow Lite, and
RootBeer arrive through
`jp.co.soramitsu:android-sora-card:1.2.1-K2` →
`com.paywings.kyc:android-sdk:1.2.2` →
`com.paywings.onboarding.kyc.android-libs:idensic-mobile-sdk:1.31.3`.
The IDensic vendor POM declares those three transitive dependencies. The
repository's verification metadata pins the currently inspected artifacts.
At baseline, `vendor/soramitsu-maven` contained the original xcrypto 1.2.7 AAR with 4 KB JNI
libraries.

## Candidate JNA replacement

The current working candidate selects `net.java.dev.jna:jna:5.17.0` through the
version catalog and both affected production Release locks. The official Maven
Central AAR SHA-256 is
`4dbeffffa665d97ad5aa7eee297531d3c841a86716ab7f774fd6956422b3cf38`;
the POM SHA-256 is
`501a0ff05d84a4ad10f6de25be94f49398b70a31be8f3a0ed9f4c6b44fbefee4`.
Both bytes are pinned in strict Gradle verification metadata. JNA's
[5.17.0 changelog](https://github.com/java-native-access/jna/blob/5.17.0/CHANGES.md)
records the second Android 16 KB page fix.

Strict `productionReleaseRuntimeClasspath` resolution and
`:app:assembleProductionDebug` succeeded. The rebuilt debug APK SHA-256 is
`53aea3e9714556658a7cd00ac2f2bb5f02938d6df2a5bc8af41fe8e28c9dcd09`.
Its `libjnidispatch.so` passes both static ELF checks for arm64 and x86_64,
reducing the gate result from 65 findings in 16 libraries to 60 findings in
14 libraries. On a verified 16 KB arm64 emulator, the exact packaged arm64
`.so` (`abc26e994517bcaa3309acdb0a27373864086c7569c89d3087b8626fada9ef06`)
loaded through a one-class `app_process` `System.load` harness. This verifies
raw native loading only; JNA API behavior and full app startup remain to be
qualified. The `:common` and `:app` production Debug unit suites passed 178
and 49 tests, respectively, with zero failures. The dependency preflight now
reports a `STABLE` inventory and retains all review blockers.

## xcrypto 16 KB integration candidate

The candidate retains the original
xcrypto 1.2.7 AAR and [rebuild recipe](../../../vendor/soramitsu-maven/build-inputs/xcrypto-1.2.7-16kb-rebuild.md),
then materializes an AAR whose only changed members are
`jni/arm64-v8a/libsr25519java_1.so` and `jni/x86_64/libsr25519java_1.so`. Both rebuilt 64-bit
ELFs pass the repository's `PT_LOAD` and `GNU_RELRO` 16 KB checks; the exact old AAR and both
32-bit libraries are retained unchanged. Gradle module checksums, strict verification metadata,
the whole vendor manifest, source provenance, and materialization bindings were rebased to the
new bytes. The dependency preflight reports a `STABLE` inventory but retains all independent
review blockers. The [JNI experiment report](xcrypto-16kb-rebuild-2026-09-24.md) records an
arm64 16 KB emulator smoke; the x86_64 JNI output has static inspection and byte-for-byte
second-build reproducibility but no emulator smoke.

That integration addresses only xcrypto. CameraX, TensorFlow Lite, RootBeer, and lazysodium remain
incompatible in the current APK; graphics-path and DataStore native libraries also fail the
strict `GNU_RELRO` check. The integrated production-flavor Debug APK
(`e2e5c813224ab79f02268e94ee8f0213aee00032f90dc8512a5f3a22bade9d1b`) and AAB
(`a6aa9c7ad2e2417c91ccbffe9fbed4419babec082458d41ca8a2a29e7b172d13`) package the
exact rebuilt xcrypto bytes in both ABIs. Each artifact still has **44 findings across 12 of 16
64-bit entries**. Both xcrypto entries and both JNA entries pass. The full signed Release package
and wallet flows remain unqualified. Strict offline Gradle builds of both Debug packages succeeded;
uncached `:common` and `:app` production Debug unit suites passed 178 and 49 tests respectively,
with no failures. The Release resource task stopped at the protected signing gate because the
CI keystore inputs are not present locally. `productionAllowed=false` is unchanged.

## Combined xcrypto and DataStore candidate

At `5aa607aa`, the production Debug APK also selects official
`androidx.datastore:datastore-core-android:1.2.1`. The APK SHA-256 is
`682456245f4acaf7c63e0e7950259616c435dc2252f248c2c85e6dc1eccebd1c`;
the Debug AAB SHA-256 is
`c2174ef6ecf2108a51eb6b7a698e64650cea9f7c17d71c0b30c4c61458336894`.
Both were built with offline strict dependency verification. The APK passes
Build Tools 36 16 KB ZIP alignment. The native gate still rejects both artifacts
with **42 findings across 10 of 16 inspected 64-bit entries**. Both ABIs of JNA,
xcrypto, and `libdatastore_shared_counter.so` pass ELF load and RELRO checks;
graphics-path, CameraX, lazysodium, TensorFlow Lite, and RootBeer remain.

The official Google Maven DataStore AAR SHA-256 is
`435edad7bcb1fbb1a2a46de7be4d6daf299479b3328ebf757ebdfe02810cbdd8`.
Its arm64 and x86_64 shared-counter entries match the packaged entries byte for
byte. On a 4 KB arm64 emulator, the retained 3.8.6.3 writer, DataStore 1.2.1
reader/update/reopen, and retained old-reader rollback all passed under the
same package identity and signer. The 178 `:common` and 49 `:app` Debug unit
tests passed. `:app:testProductionReleaseUnitTest` passed with protected signing
and production Google OAuth validation tasks excluded. The native and storage
checks do not qualify a signed Release artifact or 16 KB device flow.

## Isolated RootBeer 0.1.0 candidate

The [RootBeer 16 KB rebuild report](rootbeer-16kb-rebuild-2026-09-25.md) records a
source-pinned replacement for the exact `com.scottyab:rootbeer-lib:0.1.0` graph.
Only the two 64-bit `libtoolChecker.so` AAR entries change; the original Java,
resources, 32-bit JNI entries, and published POM remain. Both new ELFs pass the
`PT_LOAD` and `GNU_RELRO` checks. An arm64 16 KB JNI smoke passes, and the
original/rebuilt arm64 outputs agree on a 4 KB emulator. The isolated production
Debug APK and AAB package those bytes and have 30 findings across eight other
entries. Full KYC compatibility, independent source review, and signed Release
qualification remain open.

## Combined RootBeer and Lazysodium candidate

The current candidate includes the source-pinned RootBeer 0.1.0 and
[Lazysodium 5.0.2](lazysodium-16kb-rebuild-2026-09-25.md) AAR overlays at their
existing dependency coordinates. Each changes only its two 64-bit JNI entries.
The production-flavor Debug APK SHA-256 is
`5a62cbae3a86ac6fe5132101068ce02e64bee8d7046c6616a723a3427290ff43`;
the AAB SHA-256 is
`fa4ed6af31af03dd92a2a078dab4f32461f99181805e8458d8b9ce57802ecc4f`.
Strict offline builds and the 16 KB APK ZIP alignment check pass. Both artifacts
package the exact rebuilt RootBeer and libsodium bytes in arm64-v8a and x86_64.

The whole-artifact native gate still rejects both with **26 findings across six
of 16 64-bit entries**: both ABIs of `libandroidx.graphics.path.so`, CameraX
`libimage_processing_util_jni.so`, and TensorFlow Lite
`libtensorflowlite_jni.so`. The RootBeer and libsodium entries have no static
16 KB finding. The release source audit has zero structural failures and the
dependency preflight reports `STABLE`, while all independent review and signed
Release admission blockers remain.

## Reproduce current artifact inspection

From the repository root:

```sh
APK=app/build/outputs/apk/production/debug/SORA_Wallet_3.8.6.3_122_production_debug.apk
shasum -a 256 "$APK"
"$HOME/Library/Android/sdk/build-tools/36.0.0/zipalign" -c -P 16 -v 4 "$APK"
python3 scripts/verify-android-native-16kb.py "$APK" # current artifact passes

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

1. Independently review the pinned xcrypto 16 KB rebuild, its source-to-binary
   provenance, and wallet crypto behavior in the signed Release artifact.
2. Independently review the selected JNA AAR and pinned RootBeer, Lazysodium,
   graphics-path, CameraX, and TensorFlow Lite rebuilds, including their
   source-to-binary provenance and wallet crypto/KYC behavior. The current
   candidate pins exact dependency coordinates, locks, checksums, and vendor
   provenance; protected review receipts are still required.
3. Inspect **every** `arm64-v8a` and `x86_64` `.so` in the exact signed/minified
   `productionRelease` APK and Play bundle output: 16 KB ZIP alignment, ELF
   `PT_LOAD` alignment, and the guide's `GNU_RELRO` end-alignment check. Run
   wallet creation/import, key operations, camera/KYC, and app startup on a
   16 KB device or emulator verified with `adb shell getconf PAGE_SIZE` =
   `16384`, retaining results against the signed candidate digest.

The `GNU_RELRO` gate needs particular attention. An exploratory check on the
baseline debug APK found nonzero `(VirtAddr + MemSiz) % 0x4000` for many 64-bit libraries,
including `arm64-v8a/libandroidx.graphics.path.so`, whose RELRO end is `0x6000`
(`0x2000` modulo `0x4000`) even though its `PT_LOAD` segments are 16 KB aligned.
The [same Android guide](https://developer.android.com/guide/practices/page-sizes)
warns that RELRO mismatch can cause a runtime segmentation fault. The current
Debug package clears the static RELRO check and cold-launches on a 16 KB
emulator; the protected Release artifact and retained device flows remain open.

## Signed artifact gate

`scripts/verify-android-native-16kb.py` checks every packaged `arm64-v8a` and
`x86_64` library in the APK and AAB. It requires ELF64 architecture and bounds,
16 KB `PT_LOAD` alignment and congruence, and the Android guide's `GNU_RELRO`
end alignment. For the APK, the release policy additionally requires
uncompressed native entries on 16 KB ZIP data offsets. For the AAB, it parses
the [official `BundleConfig.pb` fields](https://github.com/google/bundletool/blob/master/src/main/proto/config.proto)
and requires enabled uncompressed native libraries with 16 KB or 64 KB page
alignment, so Play-generated APKs are instructed to align them. This is the
reviewed uncompressed packaging policy for this app; Android also documents a
compressed-library alternative for other configurations.

The protected build checks the exact signed APK and AAB before the independent
rebuild; rollout rechecks all four downloaded primary and reproduced artifacts.
The protected signed APK device smoke repeats the APK check after signature
verification and before installation. Eleven synthetic tests cover aligned
success, independent ZIP/ELF/RELRO failures, and bundle config handling; PR CI
runs those tests. The baseline production-flavor debug APK failed with 65
individual alignment findings and at least one finding in every one of its 16
native library entries. The JNA-updated debug APK failed with 60 findings in
14 library entries; the combined xcrypto and DataStore candidate failed with
42 findings in 10 entries. The current Debug APK and AAB pass with zero
findings. Passing this gate on the exact signed Release APK is required in
addition to a verified 16 KB device run.

## Published replacement reconnaissance

Official Maven AARs were inspected with NDK r28 `llvm-readelf` for both
64-bit ABIs. This checks the published library bytes, not compatibility with
the resolved app or the closed IDensic SDK.

| Candidate AAR | `PT_LOAD` for both ABIs | `GNU_RELRO` end remainder (arm64 / x86_64) | Finding |
| --- | --- | --- | --- |
| [JNA 5.17.0](https://repo.maven.apache.org/maven2/net/java/dev/jna/jna/5.17.0/jna-5.17.0.aar) | `0x4000` | `0 / 0` | Passes both static ELF checks; its [changelog](https://github.com/java-native-access/jna/blob/master/CHANGES.md) records a second Android 16 KB fix. |
| [lazysodium 5.2.0](https://repo.maven.apache.org/maven2/com/goterl/lazysodium-android/5.2.0/lazysodium-android-5.2.0.aar) | `0x4000` | `0x1000 / 0x1000` | Fails RELRO despite its [16 KB release note](https://github.com/terl/lazysodium-android/releases/tag/v5.2.0). It requests JNA 5.17.0; this app pins JNA separately. |
| [RootBeer 0.1.2](https://repo.maven.apache.org/maven2/com/scottyab/rootbeer-lib/0.1.2/rootbeer-lib-0.1.2.aar) | `0x4000` | `0x1000 / 0x1000` | Fails RELRO; 0.1.1 has the same result despite [release notes](https://github.com/scottyab/rootbeer/releases) claiming 16 KB support. |
| [CameraX camera-core 1.4.0](https://dl.google.com/dl/android/maven2/androidx/camera/camera-core/1.4.0/camera-core-1.4.0.aar) | `0x4000` | `0x3000 / 0` | Fails arm64 RELRO. [1.6.2](https://dl.google.com/dl/android/maven2/androidx/camera/camera-core/1.6.2/camera-core-1.6.2.aar) fixes that library but adds another native library with `0x1000 / 0x1000` RELRO remainders. |
| [LiteRT 1.4.0](https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert/1.4.0/litert-1.4.0.aar) | `0x4000` | `0x2000 / 0x1000` | Fails RELRO. Google's [migration guide](https://developers.google.com/edge/litert/migration) describes its Interpreter API path, but IDensic linkage and model behavior need vendor validation. |

The pinned [xcrypto source](https://github.com/soramitsu/x-crypto/tree/2346144a127c1121ae3166800b7ab06ed9c5bf20)
uses NDK 25.2. A controlled 16 KB rebuild must establish both ELF checks and
preserve the wallet's sr25519 behavior before replacing its vendored AAR.
CameraX, TensorFlow Lite, and RootBeer are selected through the closed
PayWings/IDensic graph; upgrading their transitive coordinates without vendor
compatibility review would not qualify the KYC path.
