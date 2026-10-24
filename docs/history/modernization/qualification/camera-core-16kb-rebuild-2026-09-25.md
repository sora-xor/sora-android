# CameraX camera-core 1.3.1: isolated 16 KB JNI rebuild

Status: **materialized-unreviewed; production blocked**. This isolated worktree starts
from Android candidate `1e86354d047e4bb6c33406ed7ca8accc5a1a98c2` and leaves the
exact locked `androidx.camera:camera-core:1.3.1` coordinate in place. Its Java
classes, resources, manifest, 32-bit libraries, POM, source JAR, version metadata,
and Gradle dependency variants are unchanged. The AAR changes only the arm64-v8a
and x86_64 `libimage_processing_util_jni.so` ZIP members; the other 22 of 24
members are byte identical to [Google Maven's published AAR](https://dl.google.com/dl/android/maven2/androidx/camera/camera-core/1.3.1/camera-core-1.3.1.aar).

The [pinned recipe](../../../vendor/soramitsu-maven/build-inputs/camera-core-1.3.1-16kb-rebuild.md)
uses the [official CameraX 1.3.1 release notes](https://developer.android.com/jetpack/androidx/releases/camera#1.3.1)
source endpoint `ee5fe2aa34dba21365bb402477c32c593ccbecda` and the
contemporaneous official camera-release manifest
`2f7e8332eff3d34fdfb1b471f44d654aa6518e41`. That manifest pins libyuv
`096484820d74c72a6838b3e80743fc7a5d94784b` and a support revision
whose camera-core native subtree matches the release-notes endpoint. The
NDK is `28.0.12674087-beta2`; two builds from these pinned sources produced
byte-identical output. The commits are unsigned, and the precise source-to-binary
correspondence of Google's original AAR has not been independently attested.

| Artifact | SHA-256 | Result |
| --- | --- | --- |
| Published 1.3.1 AAR | `6b7ea2da7cc504d6624c3c12a0c2d488dd6635563421dacba0790399507443e8` | original 64-bit PT_LOAD is 0x1000 |
| Rebuilt arm64-v8a SO | `2e7b45ad96e790e1b11095f22021232d29d9b128d776eb5a2a7485d22da19e4e` | PT_LOAD 0x4000, RELRO end 0x10000 |
| Rebuilt x86_64 SO | `c8ac7fe97507a374f789b42f8ca41dff671c4723eb8ec78ec76872746143a4d0` | PT_LOAD 0x4000, RELRO end 0x14000 |
| Repacked AAR | `e42b866b808d12cf69f9477aa902f8ba0934449ca738e97d4ffd341ad4b3ca37` | two changed native members |
| Updated Gradle module | `58b26548d8ddd483f14e1740eae38655333852d5985c7dde84597807af1f1b21` | only two AAR file descriptors changed |

Both rebuilt SOs pass `inspect_elf` with no findings. The published and rebuilt
SOs have the same six versioned JNI exports, six `DT_NEEDED` libraries, soname,
and `BIND_NOW` flag in both ABIs. Strict offline production Release dependency
resolution selected the source-qualified 1.3.1 runtime AAR. Strict offline
`:app:assembleProductionDebug` and `:app:bundleProductionDebug` succeeded.
The source dependency preflight reported `inventoryStatus=STABLE`, zero
structural failures, and six existing independent review blockers. The
source release gate reported zero structural failures and 36 existing
blockers. The 31 lock files and 566 configuration entries are unchanged.

| Packaged artifact | SHA-256 | 16 KB native audit |
| --- | --- | --- |
| Production-flavor Debug APK | `aa64cc0824c100392d2b332dd98f9a6927aa1762f062bc7cb7f2b22d044da223` | both CameraX ABI entries match rebuilt SOs; 8 findings in two TensorFlow Lite entries |
| Production-flavor Debug AAB | `a6d53b3d1257a3e2cdcc9b36bb9a9cd714ed5309813a18a08ed5186b0631fbb2` | same CameraX bytes and 8 remaining findings |

On `FearlessMigrationApi30Fresh20260730` (arm64, API 30,
`PAGE_SIZE=4096`), a minimal Java class with the published
`androidx.camera.core.ImageProcessingUtil` JNI signatures called native
YUV-to-bitmap conversion and pixel shift. Both original and rebuilt SOs
returned identical SHA-256 values: RGBA
`3db07f5229ce1cf71b9c0e114520e20351e76bb0c49364f41939d9cdafa6718d`
and shifted Y plane
`683257c013a1608da89f20c61ccaa6966c6de4cd150490110c041e5651f8bb0c`.
On `FearlessApi36Ps16k` (arm64, API 36, `PAGE_SIZE=16384`), the rebuilt
SO loaded and produced those same hashes. The original SO failed to load
there with `program alignment (4096) cannot be smaller than system page size
(16384)`. Both temporary emulators were shut down after testing.

The smoke covers two JNI operations with deterministic 4×4 YUV inputs. It does
not establish full camera capture, camera hardware compatibility, or production
wallet startup. The source and dependency reviews remain incomplete, and the
native package gate still fails on TensorFlow Lite. No release authority is
granted by this experiment.
