# CameraX camera-core 1.3.1 16 KB JNI rebuild

The app's locked graph selects `androidx.camera:camera-core:1.3.1`. This overlay
retains its original Java classes, resources, manifest, 32-bit JNI, POM, source
JAR, version metadata, and dependency variants. The AAR changes only its
arm64-v8a and x86_64 `libimage_processing_util_jni.so` entries. It remains
`materialized-unreviewed`; this focused rebuild is not independent source-to-binary
review or approval to release.

The [official CameraX 1.3.1 release notes](https://developer.android.com/jetpack/androidx/releases/camera#1.3.1)
end their source commit range at AndroidX support commit
`ee5fe2aa34dba21365bb402477c32c593ccbecda`, tree
`0ca4c35926f8b22e4e660dee2f6fc807b69c9738`. Its camera-core native
subtree is `4bf7c67fce6b47c7c19c4b047416f23bf481c68d`. Google's
`androidx-camera-release` manifest at commit
`2f7e8332eff3d34fdfb1b471f44d654aa6518e41`, tree
`984e5a745f5a677e05e778d6f9c528be9f8d5951`, pins support commit
`0e832b9f4335f232b8520df7e430b5f8e8544dc0` and libyuv commit
`096484820d74c72a6838b3e80743fc7a5d94784b`, tree
`303e8eabb435a80ebec36a19c23ca85744d58fb5`. The pinned support
commit has the same camera-core native subtree as the release-notes endpoint.
The release manifest's exact `default.xml` is preserved here as
`camera-core-1.3.1-release-manifest.xml` (SHA-256
`8b03ec48a8dc962a921ef333a56f2359e7b4ab41005025ad6e86c20166fe5ec7`).
These Git commits are unsigned and the publisher's precise original build
inputs have not been independently attested.

Use Android NDK directory `28.0.12674087` with `Pkg.Revision =
28.0.12674087-beta2`, Clang 19.0.0, host `darwin-x86_64`. The pinned build
script compiles the 51 libyuv C++ source files named by its CMake source glob
into a static archive, then compiles the camera-core JNI source with its
upstream C++17, O3, LTO, visibility and section flags. It adds 16 KB maximum
and common page-size linker options. `-static-libstdc++` preserves the
published SO's `DT_NEEDED` set; `--undefined-version` accepts upstream's
version-map entries for optional `JNI_OnLoad` and `JNI_OnUnload`, which are
not defined by this library. The six published JNI exports are unchanged.

```sh
git init support-source
git -C support-source remote add upstream https://android.googlesource.com/platform/frameworks/support
git -C support-source fetch --filter=blob:none --depth=1 upstream ee5fe2aa34dba21365bb402477c32c593ccbecda
git -C support-source fetch --filter=blob:none --depth=1 upstream 0e832b9f4335f232b8520df7e430b5f8e8544dc0
git -C support-source sparse-checkout init --cone
git -C support-source sparse-checkout set camera/camera-core
git -C support-source checkout --detach ee5fe2aa34dba21365bb402477c32c593ccbecda
git init libyuv-source
git -C libyuv-source remote add upstream https://android.googlesource.com/platform/external/libyuv
git -C libyuv-source fetch --filter=blob:none --depth=1 upstream 096484820d74c72a6838b3e80743fc7a5d94784b
git -C libyuv-source checkout --detach FETCH_HEAD
git init release-manifest
git -C release-manifest remote add upstream https://android.googlesource.com/platform/manifest
git -C release-manifest fetch --filter=blob:none --depth=1 upstream 2f7e8332eff3d34fdfb1b471f44d654aa6518e41
git -C release-manifest checkout --detach FETCH_HEAD
export ANDROID_SDK_ROOT=/path/to/Android/sdk
python3 vendor/soramitsu-maven/build-inputs/camera-core-1.3.1-build-16kb.py \
  support-source libyuv-source release-manifest rebuilt
python3 vendor/soramitsu-maven/build-inputs/camera-core-1.3.1-repack-16kb.py \
  vendor/soramitsu-maven/build-inputs/camera-core-1.3.1-original.aar \
  vendor/soramitsu-maven/build-inputs/camera-core-1.3.1-original.module \
  rebuilt/arm64-v8a/libimage_processing_util_jni.so \
  rebuilt/x86_64/libimage_processing_util_jni.so \
  rebuilt/camera-core-1.3.1.aar rebuilt/camera-core-1.3.1.module
```

The original AAR and Gradle module file were downloaded directly from Google
Maven and match the app's prior strict verification hashes. The repacker pins
their bytes, changes exactly two of 24 AAR members, and updates only the AAR
size and four AAR hashes in the API and runtime Gradle variants.

| Artifact | SHA-256 |
| --- | --- |
| Published AAR | `6b7ea2da7cc504d6624c3c12a0c2d488dd6635563421dacba0790399507443e8` |
| Published Gradle module | `fe175138941912c5c1ad8ce070a72c56650beceef7bbdfbde49d179ee3dec894` |
| Rebuilt arm64-v8a SO | `2e7b45ad96e790e1b11095f22021232d29d9b128d776eb5a2a7485d22da19e4e` |
| Rebuilt x86_64 SO | `c8ac7fe97507a374f789b42f8ca41dff671c4723eb8ec78ec76872746143a4d0` |
| Repacked AAR | `e42b866b808d12cf69f9477aa902f8ba0934449ca738e97d4ffd341ad4b3ca37` |
| Updated Gradle module | `58b26548d8ddd483f14e1740eae38655333852d5985c7dde84597807af1f1b21` |

Two builds from the same pinned source trees produced byte-identical SOs.
Both have 0x4000 `PT_LOAD` alignment; the arm64 `GNU_RELRO` end is 0x10000
and the x86_64 end is 0x14000. The original six JNI exports, six `DT_NEEDED`
libraries, soname and `BIND_NOW` flag match for both ABIs. The repository's
`inspect_elf` returns no findings for either rebuilt SO.

`CameraCoreNativeSmoke.java` is the Java/JNI smoke source. It calls the actual
`ImageProcessingUtil` JNI exports for YUV-to-bitmap conversion and pixel shift
with deterministic direct buffers. On the API 30 arm64 4 KB emulator, both
original and rebuilt SOs returned `rgba=3db07f5229ce1cf71b9c0e114520e20351e76bb0c49364f41939d9cdafa6718d`
and `shiftedY=683257c013a1608da89f20c61ccaa6966c6de4cd150490110c041e5651f8bb0c`.
The rebuilt SO returned the same hashes on the API 36 arm64 16 KB emulator.
The original failed 16 KB loading with `program alignment (4096) cannot be
smaller than system page size (16384)`.
