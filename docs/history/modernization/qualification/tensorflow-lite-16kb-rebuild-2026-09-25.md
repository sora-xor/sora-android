# TensorFlow Lite 2.12.0: isolated 16 KB JNI rebuild

Status: **materialized-unreviewed; production release blocked**. The Android
candidate continues to select `org.tensorflow:tensorflow-lite:2.12.0`; no
dependency version, lock file, published Java class, resource, header, 32-bit JNI
library, or POM changed. The pinned [build and repack record](../../../vendor/soramitsu-maven/build-inputs/tensorflow-lite-2.12.0-16kb-rebuild.md)
uses the exact upstream `v2.12.0` source commit, a documented CMake-only build
patch, NDK r28 beta2, and the published library's exact dynamic export list.
The source tag is lightweight and unsigned; independent source-to-binary review
remains required.

Two source builds, including one from a fresh clean Git worktree, produced
byte-identical stripped arm64 and x86_64 libraries. The published AAR has 28
members; the repacked AAR changes only its two 64-bit JNI members.

| Artifact | SHA-256 | Result |
| --- | --- | --- |
| Original TensorFlow Lite 2.12.0 AAR | `002371fefe277e93f1421206062823d04daceb6c9e4e824cb543eab2d3a00c91` | arm64 loader alignment 4096 |
| Rebuilt arm64 JNI | `c9099070c3034d21cbcda735cb8e39f83c34fa5e3ab079eb8d6d189e5c5fdae7` | 16 KB ELF and RELRO gate passes |
| Rebuilt x86_64 JNI | `a95289cb76794d714a91ce3fa240b92f88b8a356293c7bcdc9ca503cfcc98bef` | 16 KB ELF and RELRO gate passes |
| Repacked AAR | `4da11c611a69427c4b6742f66fdda7d276d5267b2f7a08528e1b268c4e65ccbd` | only two 64-bit JNI members changed |

Both rebuilt libraries preserve the original 215 callable/data dynamic exports
at `VERS_1.0`, SONAME, `libm.so`/`libdl.so`/`liblog.so`/`libc.so` dependencies,
and `BIND_NOW`. The original linker additionally emits the `VERS_1.0` absolute
version-node symbol; the new linker retains the same version definition without
that standalone symbol. `--no-undefined` passes.

The C API smoke loads each library by absolute path and runs TensorFlow's
`add.bin` model through tensor resizing, allocation, input/output copying, and
inference. On arm64 API 36 with `PAGE_SIZE=4096`, both original and rebuilt
libraries print `version=2.12.0 output=3.0,9.0`. On arm64 API 36 with
`PAGE_SIZE=16384`, the original fails `dlopen` because its 4096 byte program
alignment is below the system page size; the rebuilt library prints the same
expected result. Both emulators were shut down after testing. x86_64 received
static ELF/export/package checks but no emulator runtime exercise.

Strict offline Gradle `:app:assembleProductionDebug` and
`:app:bundleProductionDebug` passed. The production-flavor Debug APK is
`feabcb62e1d884d49362716faff9a3b63da0e084c189258c9c949361ffde5665`;
the AAB is
`1fd381ecfeb039240b61c000ae96bc9ad2546614f48dcb0329129836b6b5c83a`.
Both package the exact rebuilt TensorFlow Lite and CameraX JNI bytes for both
64-bit ABIs. The native gate reports **16 libraries, zero findings** for each;
Build Tools 36 `zipalign -c -P 16 -v 4` reports `Verification successful`
for the APK.

The dependency preflight remains `STABLE` with six independent release blockers;
the source release gate has zero structural failures and 36 release blockers.
These builds are Debug artifacts. Java/JNI app flows, a protected signed and
minified production Release, retained-device tests, independent source/dependency
review, and release signing admission remain outstanding.
