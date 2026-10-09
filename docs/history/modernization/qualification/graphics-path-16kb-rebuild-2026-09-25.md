# AndroidX graphics-path 1.0.1: isolated 16 KB JNI rebuild

Status: **materialized-unreviewed; production blocked**. This isolated experiment is based on
Android candidate `6a0d13d46524c9c28ec6bfbcb14d38b739c07ea9`. The app's strict
dependency graph selects `androidx.graphics:graphics-path:1.0.1`; the Gradle locks and the
published Java API remain unchanged. The current official 1.1.0 AAR still fails the 16 KB
`GNU_RELRO` end check for both 64-bit ABIs, so upgrading it does not clear the gate.

The [pinned recipe](../../../vendor/soramitsu-maven/build-inputs/graphics-path-1.0.1-16kb-rebuild.md)
uses the 1.0.1 release source from Google's `platform/frameworks/support` at commit
`8a05a22af450d589ef911d772a001a49dcb05b71`, tree
`f1ec6450df09939325e8dbe5cf57b589a673afc3`, and NDK r28 beta2. That source commit
is unsigned and needs independent authentication and source-to-binary review. Two builds
produced byte-identical binaries. The original Google Maven AAR has 23 entries; the
repacked AAR changes exactly the two 64-bit JNI entries. The Gradle module file changes
only the AAR size and hashes in its API and runtime publication descriptors.

| Artifact | SHA-256 | Result |
| --- | --- | --- |
| Original 1.0.1 AAR | `8ca4032b6d79b351f0b59ad4b580eddbb9423e1652f7c958830687f1eee2ec03` | arm64/x86_64 RELRO end `0x6000` |
| Rebuilt arm64 SO | `d7ec2a1d0e1c1c652dd7bc0f70825835b1c9a6d26f72caab33b88f4f425d5e9e` | `PT_LOAD` 0x4000, RELRO end `0xc000` |
| Rebuilt x86_64 SO | `c286266ff104672626bce3b87b3b3367e017db972cff12dd53290c967de42e17` | `PT_LOAD` 0x4000, RELRO end `0xc000` |
| Repacked AAR | `eb4759c27ae0625d9e81fe95d8ce63d0a3846764a3465363ab195109d6785a71` | 21 other entries byte-identical |
| Updated Gradle module | `478695824bf314c9d0603ea249e25431b172c485aab20a61856a2cdea6819441` | dependency variants unchanged |

For each ABI, the rebuilt library has the same `JNI_OnLoad@@LIBANDROIDX.GRAPHICS.PATH`
dynamic export, `libm.so`, `libdl.so`, `libc.so` dependencies, soname, and `BIND_NOW` flag
as the published original. The app's `inspect_elf` returns no findings for either rebuilt
library.

Strict offline `productionReleaseRuntimeClasspath` resolution selected the vendored 1.0.1
runtime AAR. Strict offline `:app:assembleProductionDebug` and `:app:bundleProductionDebug`
succeeded. The source dependency preflight reported `inventoryStatus=STABLE`, zero
structural failures, and six existing independent review blockers. The source release gate
reported zero structural failures and 36 existing blockers. All 31 lock files and 566
configuration entries are unchanged.

| Packaged artifact | SHA-256 | 16 KB audit |
| --- | --- | --- |
| Production-flavor Debug APK | `d762eca654f4f393f956c553385689e93b10137480d6d18c76e38386ae5c8d84` | rebuilt graphics-path bytes in both ABIs; 24 findings across four CameraX/TensorFlow Lite entries |
| Production-flavor Debug AAB | `b2630920a049cbb68b189fe4a4b4d5210e7518863f29787b1e407f7509d00c7c` | same rebuilt bytes; same 24 findings |

On `FearlessMigrationApi30Fresh20260730`, arm64 API 30 with `PAGE_SIZE=4096`, a
Java `PathIterator`/JNI smoke passed with both the published and rebuilt arm64 SO:
`GRAPHICS_PATH_JNI_SMOKE_PASS page=4096 verbs=Move,Line,Quadratic,Cubic,Close`.
On `FearlessApi36Ps16k`, arm64 API 36 with `PAGE_SIZE=16384`, the rebuilt SO loaded
and the same Java smoke passed with `page=16384`. The API 30 run exercises the
pre-API34 JNI path. This focused test does not establish wallet startup, graphical
rendering behavior, or source-to-binary equivalence beyond the exercised verbs.
Both temporary emulators were shut down after testing.

The package is still blocked by CameraX and TensorFlow Lite native entries, plus
the existing independent source, dependency, signing, and release reviews. No
production approval is granted by this experiment.
