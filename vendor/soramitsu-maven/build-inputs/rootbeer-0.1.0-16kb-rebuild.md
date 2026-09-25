# RootBeer 0.1.0: source-pinned 16 KB JNI rebuild

The IDensic/PayWings graph selects `com.scottyab:rootbeer-lib:0.1.0`. This local Maven module retains that exact coordinate and the published AAR's Java classes, resources, manifest, and both 32-bit JNI libraries. Only `jni/arm64-v8a/libtoolChecker.so` and `jni/x86_64/libtoolChecker.so` are rebuilt from the RootBeer 0.1.0 native source. The published POM is unchanged.

## Inputs and source trust

- Publisher source: [scottyab/rootbeer tag 0.1.0](https://github.com/scottyab/rootbeer/tree/0.1.0), commit `d9057ce490c3481bc9be852e343678c93860e6a8`, tree `e50696d927e68c68e88520a013d50b481c3a1f8c`.
- Native source `rootbeerlib/src/main/cpp/toolChecker.cpp` SHA-256 `b6239571e19ce95e5e6c90ff0502a94468e4023ecfb100350fa0c04c0489ae62`; `toolChecker.h` SHA-256 `e82c0796eff735c5afe0210e41d1411368f922fe902d83b8e789d72ec9dc2de8`.
- The Git tag and commit are **unsigned**. The exact Git and file hashes pin the bytes but do not constitute independent source authentication or review.
- [Published Maven Central AAR](https://repo.maven.apache.org/maven2/com/scottyab/rootbeer-lib/0.1.0/rootbeer-lib-0.1.0.aar) SHA-256 `6c4d2e20148111a550aa3923c24e9b1360f300f1454117235a4d435e45928ee7`. `rootbeer-0.1.0-original.aar` retains these exact bytes. [Published POM](https://repo.maven.apache.org/maven2/com/scottyab/rootbeer-lib/0.1.0/rootbeer-lib-0.1.0.pom) SHA-256 `7bdcb8d3aa5a345281663c04653df190adb7382b7266a44cffe63d6fa3a6dd35`.
- Android NDK `28.0.12674087`, Clang `19.0.0`, Darwin x86_64 host toolchain, Android API 21 target. Link options explicitly set 16 KB maximum/common page sizes, `RELRO`, `NOW`, and static C++ runtime. `-fstack-protector-all` is retained from upstream's 0.1.0 CMake input. The static runtime keeps the original AAR's `DT_NEEDED` dependency set and avoids adding a missing `libc++_shared.so` dependency.
- The source is [Apache-2.0](https://github.com/scottyab/rootbeer/blob/0.1.0/LICENSE); the published POM declares that license. Full app notice/SBOM review is pending.

## Reproduce

From the Android repository root on macOS with that NDK installed:

```sh
ROOTBEER_SOURCE=/private/tmp/rootbeer-0.1.0-source
ROOTBEER_OUTPUT=/private/tmp/rootbeer-0.1.0-rebuilt
ANDROID_SDK_ROOT=/Users/takemiyamakoto/Library/Android/sdk
export ANDROID_SDK_ROOT
git clone --filter=blob:none --no-checkout https://github.com/scottyab/rootbeer.git "$ROOTBEER_SOURCE"
git -C "$ROOTBEER_SOURCE" checkout --detach 0.1.0
vendor/soramitsu-maven/build-inputs/rootbeer-0.1.0-build-16kb.sh "$ROOTBEER_SOURCE" "$ROOTBEER_OUTPUT"
python3 vendor/soramitsu-maven/build-inputs/rootbeer-0.1.0-repack-16kb.py \
  vendor/soramitsu-maven/build-inputs/rootbeer-0.1.0-original.aar \
  "$ROOTBEER_OUTPUT/arm64-v8a/libtoolChecker.so" \
  "$ROOTBEER_OUTPUT/x86_64/libtoolChecker.so" \
  /private/tmp/rootbeer-0.1.0-16kb-reproduced.aar
cmp /private/tmp/rootbeer-0.1.0-16kb-reproduced.aar \
  vendor/soramitsu-maven/com/scottyab/rootbeer-lib/0.1.0/rootbeer-lib-0.1.0.aar
```

Pinned SHA-256 outputs:

| Output | SHA-256 |
| --- | --- |
| arm64-v8a JNI | `656be4fef61da1f1fe9740beac8c11297b3337f9028dceb87bdbbf7a65414cfa` |
| x86_64 JNI | `15b820b18ac414d90dff74e225743f05b3ba793bd6ad9084b7c1a7bd5e52eee5` |
| Repacked AAR | `d071cebd41c71d920ebfc445adf237ad3c560aaa3a074534af0393560ad85953` |

A second build to a separate output directory produced byte-identical 64-bit libraries. The repack script verifies all AAR member names/order and that only the two 64-bit JNI members changed; the other 15 members are byte-identical to the published AAR. The project's `inspect_elf` gate reports no findings for either replacement library: all `PT_LOAD` segments are 16 KB aligned and congruent, and `GNU_RELRO` ends at a 16 KB boundary. The original AAR fails both checks in both 64-bit ABIs.

A JNI harness using the **published 0.1.0 `classes.jar`** passed on an arm64 16 KB emulator (`getconf PAGE_SIZE=16384`): library loaded and returned `0,1,1` for missing, present, and mixed file paths. On an arm64 4 KB emulator (`PAGE_SIZE=4096`), both original and rebuilt libraries returned the same `0,1,1` results. This exercises the native root-check API, not the complete IDensic/KYC flow or a protected signed Release APK. x86_64 has static ELF and byte-reproducibility evidence only. The module remains `materialized-unreviewed` and `productionAllowed=false`.
