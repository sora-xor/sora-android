# Preserved source-build inputs

These files are immutable inputs that were not present in the pinned upstream Git trees but were
required to materialize the checked-in Maven modules.

- `xcrypto-1.2.7-Cargo.lock` is the exact generated Rust lock state used for the xcrypto 1.2.7
  Android build. It pins `zeroize_derive` to the compatible 1.4.2 release. Its SHA-256 is bound by
  `SOURCE_PROVENANCE.json` and `CONTENTS.sha256`.
- `xcrypto-1.2.7-original.aar` preserves the exact prior materialized AAR. It supplies the
  unchanged Java, resources, metadata, and 32-bit libraries for the 16 KB repack.
- `xcrypto-1.2.7-16kb-rebuild.md` records the pinned xcrypto source, Rust and NDK toolchains,
  linker flags, output digests, JNI smoke evidence, and exact reproduction commands.
- `xcrypto-1.2.7-repack-16kb.py` copies only the rebuilt arm64-v8a and x86_64 JNI libraries into
  that original AAR. It verifies the input and output digests and every ZIP member's contents.
- `rootbeer-0.1.0-original.aar` preserves Maven Central's exact 0.1.0 RootBeer AAR, including
  Java and both 32-bit JNI entries.
- `rootbeer-0.1.0-build-16kb.sh`, `rootbeer-0.1.0-repack-16kb.py`, and
  `rootbeer-0.1.0-16kb-rebuild.md` pin the unsigned upstream source tag, NDK r28 compilation,
  the two rebuilt 64-bit JNI digests, and the deterministic two-member AAR replacement.
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
  the pinned xsubstrate commit: consume the materialized xcrypto 1.2.7 module and use polkaj's
  authoritative `io.emeraldpay.polkaj` coordinate. Temporary repository-path edits used only to
  resolve the isolated staging repository are intentionally excluded because they are not source
  or dependency-semantic build inputs.

These inputs and the current artifact hashes establish a stable, inspectable inventory. The
isolated xcrypto JNI experiment does not constitute an independent source-to-binary review, full
wallet runtime test, SBOM, license review, or production approval.
