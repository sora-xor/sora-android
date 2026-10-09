# RootBeer 0.1.0 16 KB native replacement: isolated qualification evidence

Status: **materialized-unreviewed; production blocked**. This experiment is on the isolated `codex/rootbeer-16kb-rebuild-20260924` worktree, based on Android candidate `7f1f413b`. It is not a protected signed Release or a completed IDensic/KYC runtime test.

## Selected graph and inputs

The exact `:app:productionReleaseRuntimeClasspath` still selects `com.scottyab:rootbeer-lib:0.1.0` through `android-sora-card:1.2.1-K2` → PayWings KYC `1.2.2` → IDensic `1.31.3`. A strict offline `:app:dependencyInsight` confirmed the lock selects 0.1.0. The source-qualified local Maven repository now supplies the same coordinate, with the published POM unchanged and the original published AAR preserved. Its replacement AAR changes only the `arm64-v8a` and `x86_64` `libtoolChecker.so` entries; the remaining 15 ZIP members, including `classes.jar` and both 32-bit libraries, are byte-identical to the [published 0.1.0 AAR](https://repo.maven.apache.org/maven2/com/scottyab/rootbeer-lib/0.1.0/rootbeer-lib-0.1.0.aar).

The [rebuild recipe](../../../vendor/soramitsu-maven/build-inputs/rootbeer-0.1.0-16kb-rebuild.md) pins the unsigned upstream 0.1.0 tag at `d9057ce490c3481bc9be852e343678c93860e6a8`, tree `e50696d927e68c68e88520a013d50b481c3a1f8c`, source-file hashes, NDK r28, compile flags, and output digests. A second build yielded byte-identical `.so` files. The exported native functions and `DT_NEEDED` library sets match the original for both ABIs. The unsigned source tag requires independent source authentication/review before production approval.

| Input or output | SHA-256 |
| --- | --- |
| Published original AAR | `6c4d2e20148111a550aa3923c24e9b1360f300f1454117235a4d435e45928ee7` |
| Published unchanged POM | `7bdcb8d3aa5a345281663c04653df190adb7382b7266a44cffe63d6fa3a6dd35` |
| Rebuilt arm64-v8a `.so` | `656be4fef61da1f1fe9740beac8c11297b3337f9028dceb87bdbbf7a65414cfa` |
| Rebuilt x86_64 `.so` | `15b820b18ac414d90dff74e225743f05b3ba793bd6ad9084b7c1a7bd5e52eee5` |
| Repacked AAR | `d071cebd41c71d920ebfc445adf237ad3c560aaa3a074534af0393560ad85953` |

## Static and runtime checks

The repository's `inspect_elf` reports no findings for either rebuilt library. Each `PT_LOAD` has `0x4000` alignment and offset/virtual-address congruence; each `GNU_RELRO` ends at a 16 KB boundary. Both original 64-bit libraries fail load and RELRO checks. The original and rebuilt libraries have the same three exported functions (`checkForRoot`, `setLogDebugMessages`, `exists`) and the same five `DT_NEEDED` libraries (`libandroid`, `liblog`, `libm`, `libdl`, `libc`). Static C++ runtime linkage prevents a new `libc++_shared.so` dependency.

A JNI harness compiled with the original 0.1.0 `classes.jar` called its actual `RootBeerNative` class. On an arm64 emulator with `getconf PAGE_SIZE=16384`, the replacement loaded and returned `0` for a missing path, `1` for `/system/bin/sh`, and `1` for a mixed path array. On a separate arm64 emulator with `PAGE_SIZE=4096`, the published original and rebuilt libraries both returned `0,1,1` for those inputs. This validates the JNI/native root-check behavior exercised here. It does not cover all Java-side root-check heuristics, an x86_64 device run, KYC flows, or a signed Release build.

## Exact app artifacts

Strict offline `:app:assembleProductionDebug` and `:app:bundleProductionDebug` succeeded, as did strict production Release dependency resolution and the source dependency preflight. The preflight reported `inventoryStatus=STABLE` and zero structural failures, retaining six independent review blockers. The source release gate reported zero structural failures and its existing release blockers. The 31 lock files and 566 configuration entries were unchanged.

| Artifact | SHA-256 | Packaged RootBeer bytes | Whole-artifact native gate |
| --- | --- | --- | --- |
| Production-flavor Debug APK | `ae92311c9faaf27168eebedde1a9701038a1a298bc5cc4c969dee0137856ee6b` | both ABI entries match the vendored AAR | 30 findings across 8 of 16 entries |
| Production-flavor Debug AAB | `bd3a1357243ad1e81b46b4a53dd99a1df872c35dc1881231797d6e3b87288c3a` | both ABI entries match the vendored AAR | 30 findings across 8 of 16 entries |

Build Tools 36 `zipalign -c -P 16 -v 4` passed the APK. The AAB native gate read its 16 KB bundle alignment metadata. All remaining findings belong to graphics-path, CameraX, lazysodium, and TensorFlow Lite. RootBeer has no remaining static 16 KB finding in either artifact. The baseline combined xcrypto/DataStore candidate had 42 findings across 10 entries, so this isolated replacement removes 12 findings and two failing entries. The signed Release and PayWings/IDensic compatibility review remain open; `productionAllowed=false` is unchanged.
