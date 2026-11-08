# Preserved source-build inputs

These files are immutable inputs that were not present in the pinned upstream Git trees but were
required to materialize the checked-in Maven modules.

- `lazysodium-5.0.2-original.aar` preserves Maven Central's exact 5.0.2 AAR.
- `lazysodium-5.0.2-build-16kb.sh`, `lazysodium-5.0.2-repack-16kb.py`,
  `LazysodiumSmoke.java`, and `lazysodium-5.0.2-16kb-rebuild.md`
  pin the signed libsodium 1.0.18 source archive, NDK r28 compilation, the
  two rebuilt 64-bit JNI digests, a Java/JNA crypto smoke, and the exact AAR replacement.
- `graphics-path-1.0.1-original.aar` and `graphics-path-1.0.1-original.module` preserve
  the exact Google Maven publication used by the app dependency graph.
- `graphics-path-1.0.1-build-16kb.sh`, `graphics-path-1.0.1-repack-16kb.py`, and
  `graphics-path-1.0.1-16kb-rebuild.md` pin the AndroidX release source, NDK toolchain,
  64-bit JNI build digests, two-member AAR replacement, and Gradle module descriptors.
  `GraphicsPathSmoke.java` preserves the Java/JNI path-iterator smoke source.
- `xsubstrate-1.2.7-source-normalization.patch` is the complete semantic source delta applied to
  the pinned xsubstrate commit: consume the xcrypto 1.2.7 module (now the in-repo `:xcrypto`) and use polkaj's
  authoritative `io.emeraldpay.polkaj` coordinate (now the in-repo `:polkaj-scale`). Temporary repository-path edits used only to
  resolve the isolated staging repository are intentionally excluded because they are not source
  or dependency-semantic build inputs.

The RootBeer, TensorFlow Lite and CameraX rebuild inputs were removed in 3.9.0 together with the
SORA Card KYC SDK, their only consumer; they remain in git history.

These inputs and the current artifact hashes establish a stable, inspectable inventory. The
isolated xcrypto JNI experiment does not constitute an independent source-to-binary review, full
wallet runtime test, SBOM, license review, or production approval.

xcrypto is no longer vendored: it is the in-repo `:xcrypto` module, and its sr25519 JNI library
is built from C source with ndk-build (see `xcrypto/README.md`). Its former Rust build inputs, security
patches and 16 KB repack scripts are in git history.
