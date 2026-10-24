# AndroidX graphics-path 1.0.1 16 KB JNI rebuild

This materialized overlay keeps the selected `androidx.graphics:graphics-path:1.0.1`
dependency and every original Java class, resource, manifest, 32-bit JNI member, POM,
source JAR, and version metadata file. Only the arm64-v8a and x86_64 JNI entries in the
AAR were rebuilt. It remains `materialized-unreviewed`; the unsigned source commit and
source-to-binary comparison need independent review before a production release.

Source: `https://android.googlesource.com/platform/frameworks/support` at commit
`8a05a22af450d589ef911d772a001a49dcb05b71`, tree
`f1ec6450df09939325e8dbe5cf57b589a673afc3`, native subtree
`27981507de4b994a6e92a9da8d89e5436600f00c`.
This is the AndroidX graphics-path 1.0.1 release's source history, fetched directly
from Google's Gitiles server. The commit has no cryptographic Git signature.
The upstream CMake file supplies `-z max-page-size=16384` but no
`-z common-page-size=16384`; the published 1.0.1 AAR's two 64-bit libraries have
16 KB `PT_LOAD` alignment but a `GNU_RELRO` end of `0x6000`. The published 1.1.0
upgrade also ends RELRO at `0x6000` in both 64-bit ABIs.

Toolchain: Android NDK directory `28.0.12674087`, `source.properties`
`Pkg.Revision = 28.0.12674087-beta2`, Clang 19.0.0, host `darwin-x86_64`.
The `graphics-path-1.0.1-build-16kb.sh` script pins the native source tree,
compilation flags, version map, soname, and both output hashes. It adds both
`-z max-page-size=16384` and `-z common-page-size=16384` and otherwise uses
the upstream build's C++17 and native feature flags.

```sh
git init support-source
git -C support-source remote add upstream https://android.googlesource.com/platform/frameworks/support
git -C support-source fetch --filter=blob:none --depth=1 upstream 8a05a22af450d589ef911d772a001a49dcb05b71
git -C support-source sparse-checkout init --cone
git -C support-source sparse-checkout set graphics/graphics-path
git -C support-source checkout --detach FETCH_HEAD
export ANDROID_SDK_ROOT=/path/to/Android/sdk
bash vendor/soramitsu-maven/build-inputs/graphics-path-1.0.1-build-16kb.sh support-source rebuilt
python3 vendor/soramitsu-maven/build-inputs/graphics-path-1.0.1-repack-16kb.py \
  vendor/soramitsu-maven/build-inputs/graphics-path-1.0.1-original.aar \
  vendor/soramitsu-maven/build-inputs/graphics-path-1.0.1-original.module \
  rebuilt/arm64-v8a/libandroidx.graphics.path.so \
  rebuilt/x86_64/libandroidx.graphics.path.so \
  rebuilt/graphics-path-1.0.1.aar rebuilt/graphics-path-1.0.1.module
```

The original AAR and Gradle `.module` were downloaded from Google Maven and pinned
as build inputs. The repacker verifies the original and replacement hashes, changes
only the two JNI ZIP members, verifies all other 21 members are byte identical,
and updates only the AAR size and four hashes in the API and runtime variant file
descriptors. POM, source JAR, and version metadata are the original publisher bytes.

| Artifact | SHA-256 |
| --- | --- |
| Published AAR | `8ca4032b6d79b351f0b59ad4b580eddbb9423e1652f7c958830687f1eee2ec03` |
| Published `.module` | `3f6fc7e96f8a1fd21045da7f2e332aef528aa1f56b6455fb8f25043aafa0e1b8` |
| Rebuilt arm64-v8a `.so` | `d7ec2a1d0e1c1c652dd7bc0f70825835b1c9a6d26f72caab33b88f4f425d5e9e` |
| Rebuilt x86_64 `.so` | `c286266ff104672626bce3b87b3b3367e017db972cff12dd53290c967de42e17` |
| Repacked AAR | `eb4759c27ae0625d9e81fe95d8ce63d0a3846764a3465363ab195109d6785a71` |
| Updated `.module` | `478695824bf314c9d0603ea249e25431b172c485aab20a61856a2cdea6819441` |

Both rebuilt SO files have 0x4000 `PT_LOAD` alignment, 0xc000 `GNU_RELRO` end,
the original `JNI_OnLoad@@LIBANDROIDX.GRAPHICS.PATH` export, the original
`libm.so`, `libdl.so`, `libc.so` dependencies, soname, and `BIND_NOW` flag.
Two independent rebuild invocations produced byte-identical SO files.

`GraphicsPathSmoke.java` is the source for the Java/JNI runtime check. It loads
the exact SO by absolute path, constructs an Android `Path`, iterates its move,
line, quadratic, cubic, and close verbs through the published 1.0.1 Java classes,
and checks the results. On API 30 with 4 KB pages, both the published original
and rebuilt arm64 libraries printed
`GRAPHICS_PATH_JNI_SMOKE_PASS page=4096 verbs=Move,Line,Quadratic,Cubic,Close`.
On API 36 with 16 KB pages, the rebuilt library printed the same line with
`page=16384`. API 36 uses the framework iterator for the verb operations,
so the API 30 run is the direct exercise of the pre-34 JNI implementation.
