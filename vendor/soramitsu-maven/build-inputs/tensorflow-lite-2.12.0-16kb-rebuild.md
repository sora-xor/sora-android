# TensorFlow Lite 2.12.0 16 KB JNI rebuild

The app selects `org.tensorflow:tensorflow-lite:2.12.0`. This overlay retains the
published Maven POM, Java classes, manifest, resources, headers, license, and both
32-bit JNI members. Its AAR changes only `jni/arm64-v8a/libtensorflowlite_jni.so`
and `jni/x86_64/libtensorflowlite_jni.so`. The coordinate and dependency lock stay
at 2.12.0. The overlay remains `materialized-unreviewed` and cannot authorize a
production release without independent source and binary review.

Source is the TensorFlow GitHub `v2.12.0` lightweight tag at commit
`0db597d0d758aba578783b5bf46c889700a45085`, tree
`e32b1f067af476b65f944fdcc2390178200408d1`. The tag is not a signed tag.
The sole tracked source delta is `tensorflow-lite-2.12.0-cmake-16kb.patch`. It:

- Excludes the default logger when building Android, matching the Bazel Android
  source selection and avoiding duplicate logger definitions in a whole-archive link.
- Adds the 2.12.0 JNI sources, telemetry C API, and XNNPACK delegate plugin to the
  upstream TensorFlow Lite CMake static library.
- Links with 16 KB max/common page sizes, no undefined symbols, and the exact
  published export list in `tensorflow-lite-2.12.0-exports.lds`.

The export list was derived from the 2.12.0 published arm64 and x86_64 AAR
libraries. They each expose the same 215 callable/data symbols at `VERS_1.0`.
The rebuilt libraries expose exactly those 215 symbols. The older linker also
exposes an absolute `VERS_1.0` version-node symbol; the NDK r28 linker represents
that node in `.gnu.version_d` without a separate dynamic symbol.

Toolchain: Android NDK `28.0.12674087-beta2` (Clang 19.0.0), CMake 3.31.10,
Ninja 1.13, Python 3.9 with NumPy 1.23.5. Android API minimum is 21; STL is
`c++_static`. The exact external source revisions come from the 2.12.0 TensorFlow
CMake and Bazel workspace declarations. The CMake dependencies are pinned in
`tensorflow/lite/tools/cmake/modules`; PTHREADPOOL is
`b8374f80e42010941bda6c85b0e3f1a1bd77a1e0`, FP16 is
`4dfe081cf6bcd15db339cf2680b9281b8451eeb3`, and FXdiv is
`63058eff77e11aa15bf531df5dd34395ec3017c8`. Their upstream archive
SHA-256 values are pinned in `tensorflow/workspace2.bzl` and
`third_party/FP16/workspace.bzl`. The build script accepts the three extracted
source directories, applies the patch to a clean source checkout for the build,
restores the checkout afterward, and checks both stripped output hashes.

```sh
git clone --depth 1 --branch v2.12.0 https://github.com/tensorflow/tensorflow.git tensorflow-2.12.0
export ANDROID_SDK_ROOT=/path/to/Android/sdk
export PATH=/path/to/cmake-3.31.10-and-ninja-1.13/bin:$PATH
bash vendor/soramitsu-maven/build-inputs/tensorflow-lite-2.12.0-build-16kb.sh \
  tensorflow-2.12.0 /path/to/pinned-bazel-external-sources rebuilt
python3 vendor/soramitsu-maven/build-inputs/tensorflow-lite-2.12.0-repack-16kb.py \
  vendor/soramitsu-maven/build-inputs/tensorflow-lite-2.12.0-original.aar \
  rebuilt/arm64-v8a/libtensorflowlite_jni.so \
  rebuilt/x86_64/libtensorflowlite_jni.so rebuilt/tensorflow-lite-2.12.0.aar
```

`TFLITE_CMAKE_SOURCE_CACHE` may point to a directory holding the ten upstream
CMake source directories named in the build script to avoid downloading each
dependency again for both ABIs. Without it, CMake fetches the pinned revisions.

| Artifact | SHA-256 |
| --- | --- |
| Published AAR | `002371fefe277e93f1421206062823d04daceb6c9e4e824cb543eab2d3a00c91` |
| Published POM | `5ad01644c105dff937a70c169fddfd670e04cb783927972f6ee100f2d38bd31c` |
| Rebuilt arm64-v8a JNI | `c9099070c3034d21cbcda735cb8e39f83c34fa5e3ab079eb8d6d189e5c5fdae7` |
| Rebuilt x86_64 JNI | `a95289cb76794d714a91ce3fa240b92f88b8a356293c7bcdc9ca503cfcc98bef` |
| Repacked AAR | `4da11c611a69427c4b6742f66fdda7d276d5267b2f7a08528e1b268c4e65ccbd` |

`tensorflow-lite-2.12.0-repack-16kb.py` checks the original and rebuilt hashes,
preserves ZIP entry names and order, verifies that exactly two of the original
28 members changed, and checks the final AAR hash. Both new 64-bit libraries
pass the repository 16 KB ELF/RELRO gate and preserve the original SONAME,
`libm.so`, `libdl.so`, `liblog.so`, `libc.so` dependency set and `BIND_NOW`.

The `TensorFlowLiteCApiSmoke.c` harness loads a library by absolute path and runs
the upstream `tensorflow/lite/testdata/add.bin` model. On arm64 API 36 with
16 KB pages, the published 2.12.0 library fails `dlopen` with a 4096 byte
alignment error; the rebuilt library prints `version=2.12.0 output=3.0,9.0`.
On arm64 API 36 with 4 KB pages, both libraries print the same result. This
checks model loading, tensor resize/allocation, input/output copying, and
inference through the C API. It does not constitute a full Java/JNI wallet test
or a runtime test of the x86_64 ABI.
