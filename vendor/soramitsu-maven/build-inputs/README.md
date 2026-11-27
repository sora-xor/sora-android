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

The RootBeer, TensorFlow Lite and CameraX rebuild inputs were removed in 3.9.0 together with the
SORA Card KYC SDK, their only consumer; they remain in git history.

These inputs and the current artifact hashes establish a stable, inspectable inventory. The
isolated xcrypto JNI experiment does not constitute an independent source-to-binary review, full
wallet runtime test, SBOM, license review, or production approval.

xcrypto is no longer vendored: it is the in-repo `:xcrypto` module, and its sr25519 JNI library
is built from C source with ndk-build (see `xcrypto/README.md`). Its former Rust build inputs, security
patches and 16 KB repack scripts are in git history.

xsubstrate and ui-core are no longer vendored either: they are the in-repo `:xsubstrate` and
`:ui-core` modules, built from those releases' unchanged sources (see their README files). The
xsubstrate source-normalization patch only changed dependency coordinates; it is in git history.
