# Android xcrypto / Bouncy Castle static linkage audit

Observed on 2026-09-24 in the Android Release candidate at commit
`6d402008f955824e217962b8aad4a74f90a0cc54`. The companion
[read-only verifier](ANDROID-BOUNCYCASTLE-LINKAGE-AUDIT-20260924.py) reads the
current candidate, so rerun it after any dependency or vendored-binary change.

## Repeat

Run from `/Users/takemiyamakoto/dev/sora-wallet`:

```sh
python3 ANDROID-BOUNCYCASTLE-LINKAGE-AUDIT-20260924.py
```

The script reads the candidate's `app/gradle.lockfile`,
`gradle/verification-metadata.xml`, `vendor/soramitsu-maven/SOURCE_PROVENANCE.json`,
and vendored `xcrypto-1.2.7.aar`. It locates the three exact versioned jars in
`~/.gradle/caches/modules-2/files-2.1`, checks their SHA-256 against verification
metadata, and reads the AAR's `classes.jar` in memory. It needs Python 3 and
`javap` from the installed Homebrew JDK 17. Use `--candidate`, `--cache`, and
`--javap` if those paths differ. It does not edit the candidate or Gradle cache.

The observed cache inputs were:

| Input | Exact path | SHA-256 |
| --- | --- | --- |
| Compile `bcprov-jdk18on:1.77` | `/Users/takemiyamakoto/.gradle/caches/modules-2/files-2.1/org.bouncycastle/bcprov-jdk18on/1.77/2cc971b6c20949c1ff98d1a4bc741ee848a09523/bcprov-jdk18on-1.77.jar` | `dabb98c24d72c9b9f585633d1df9c5cd58d9ad373d0cd681367e6a603a495d58` |
| Runtime `bcprov-jdk18on:1.78.1` | `/Users/takemiyamakoto/.gradle/caches/modules-2/files-2.1/org.bouncycastle/bcprov-jdk18on/1.78.1/39e9e45359e20998eb79c1828751f94a818d25f8/bcprov-jdk18on-1.78.1.jar` | `add5915e6acfc6ab5836e1fd8a5e21c6488536a8c1f21f386eeb3bf280b702d7` |
| Runtime `bcutil-jdk18on:1.71` | `/Users/takemiyamakoto/.gradle/caches/modules-2/files-2.1/org.bouncycastle/bcutil-jdk18on/1.71/57daa18bc93730eab46291d9b55a15480e013265/bcutil-jdk18on-1.71.jar` | `ac75ae3fabf2cb81210b3648fbe36aaed8d8c453bbeaac40e3b5031c7677197a` |

The vendored AAR SHA-256 is
`a701705120918cc3c66d7217590035c9d385466e1b00836191c917845e9ff56b`;
its embedded `classes.jar` SHA-256 is
`8f7c1e826ac797a1def6737f9d0bad8b903f75a960eee2a781ccffcf682f921c`.

## Result

`xcrypto` has 140 class files, with 31 Bouncy Castle class constants and 58
Bouncy Castle member references. All 31 classes are present in both provider
versions. All 58 members resolve in each version when inherited JDK members are
included. Five references resolve through `java.security.MessageDigest`; `javap
-s` confirms their `digest` and `clone` descriptors. There is no `xcrypto` class
constant that exists only in `bcutil:1.71`.

The version split is real: `:app:productionReleaseCompileClasspath` selects
`bcprov:1.77` from vendored `xcrypto`, while
`:app:productionReleaseRuntimeClasspath` selects `bcprov:1.78.1` from `:common`.
`bcutil:1.71` enters the runtime graph through
`org.jmrtd:jmrtd:0.7.34` → IDensic `1.31.3` → PayWings KYC `1.2.2` →
Sora Card `1.2.1-K2`; it is not an `xcrypto` direct dependency. Recheck those
paths with the following commands from the candidate directory. The preflight
returns exit 1 while independent production review is outstanding, even when
its inventory is `STABLE` and its `failures` list is empty.

```sh
node scripts/verify-production-modernization.mjs --dependency-preflight
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew --offline :app:dependencyInsight --configuration productionReleaseCompileClasspath --dependency org.bouncycastle:bcprov-jdk18on
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew --offline :app:dependencyInsight --configuration productionReleaseRuntimeClasspath --dependency org.bouncycastle:bcprov-jdk18on
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew --offline :app:dependencyInsight --configuration productionReleaseRuntimeClasspath --dependency org.bouncycastle:bcutil-jdk18on
```

The static result narrows the Bouncy Castle concern to behavior that
classfile references cannot prove: reflection, JNI/native sr25519 code, Android
class loading, and KYC/JMRTD behavior with the mixed BC versions. Further
crypto and KYC device tests and the required independent vendored-binary review
remain release gates. This note grants no dependency, signing, or release
qualification.

## Packaged Android runtime follow-up

At candidate `b09c544d062f618508b7c2f53b15e837dfe68350`, the new
`CryptoRuntimeCompatibilityTest` ran against an installed production-flavor
Debug APK on an API 34 arm64 emulator. The exact Gradle command
`:app:connectedProductionDebugAndroidTest` with class filter
`jp.co.soramitsu.sora.ux.CryptoRuntimeCompatibilityTest` reported **2 tests,
0 failures, 0 errors, 0 skips**. One test used a public retained SORA2 vector
to exercise native sr25519 derivation, signing, and verification; the other
used a public BIP39 vector to derive the expected Taira public key through
the packaged Bouncy Castle runtime. The existing pull-request emulator job
now runs the same command on API 36 x86_64 after its explorer test; the AAR
contains an x86_64 native library. At `a5d3aa32`, that CI emulator went offline
during the preceding explorer test, before either crypto test ran. Candidate
`b37afdfb` prebuilds the connected APKs before emulator startup; its CI run
remains pending.

This device result covers those two crypto paths in a Debug APK. It does not
qualify a signed/minified Release artifact, KYC/JMRTD, unrelated reflective
paths, physical devices, or the required independent vendored-binary review.
