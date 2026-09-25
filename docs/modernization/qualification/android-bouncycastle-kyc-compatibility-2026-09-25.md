# Bouncy Castle and KYC dependency compatibility — 2026-09-25

This candidate fixes a reproduced binary incompatibility in the existing KYC
library closure. It does not qualify a signed Release app, a physical NFC/KYC
flow, retained wallet migration, or the independent dependency review.

## Reproduced defect and bounded fix

At candidate `8b3182e9`, the app Release compile classpath selected bcprov
1.77, while runtime selected bcprov 1.78.1, bcutil 1.71, JMRTD 0.7.34 and
Scuba smartcards 0.0.19. A local Java 17 harness using those exact runtime JARs
parsed a synthetic DER CMS SignedData container through both
`ContentInfo.getInstance` and `SignedDataUtil.readSignedData`. Both failed with:

```text
java.lang.NoSuchMethodError:
  org.bouncycastle.asn1.ASN1Primitive
  org.bouncycastle.asn1.ASN1TaggedObject.getObject()
```

The upstream method exists in [BC 1.74 source](https://github.com/bcgit/bc-java/blob/r1rv74/core/src/main/java/org/bouncycastle/asn1/ASN1TaggedObject.java)
and is absent in [BC 1.75 source](https://github.com/bcgit/bc-java/blob/r1rv75/core/src/main/java/org/bouncycastle/asn1/ASN1TaggedObject.java).
JMRTD 0.7.35–0.7.37 published sources still call it; 0.7.38 first uses
`getExplicitBaseObject`. The selected [JMRTD 0.7.42 POM](https://repo.maven.apache.org/maven2/org/jmrtd/jmrtd/0.7.42/jmrtd-0.7.42.pom)
declares BC 1.78 and Scuba smartcards 0.0.20. Its
[published sources](https://repo.maven.apache.org/maven2/org/jmrtd/jmrtd/0.7.42/jmrtd-0.7.42-sources.jar)
use the available ASN.1 API. The [bcutil 1.78.1 POM](https://repo.maven.apache.org/maven2/org/bouncycastle/bcutil-jdk18on/1.78.1/bcutil-jdk18on-1.78.1.pom)
requires the retained bcprov 1.78.1 provider.

| Runtime closure with bcprov 1.78.1 | CMS parser | JMRTD parser |
| --- | --- | --- |
| bcutil 1.71 / JMRTD 0.7.34 | `NoSuchMethodError` | `NoSuchMethodError` |
| bcutil 1.78.1 / JMRTD 0.7.34 | passes | `NoSuchMethodError` |
| bcutil 1.78.1 / JMRTD 0.7.38 | passes | passes |
| bcutil 1.78.1 / JMRTD 0.7.42 | passes | passes |

The candidate exports compatible dependency constraints at the `common_wallet`
card SDK boundary: bcprov/bcutil 1.78.1, JMRTD 0.7.42 and Scuba smartcards 0.0.20.
The app now uses bcprov 1.78.1 at compile and runtime. The provider runtime
version is unchanged. The unaffected account and multiaccount modules still
compile their existing code against bcprov 1.77; their runtime remains 1.78.1,
and their source contains no direct Bouncy Castle imports. No global resolution
force or verification exception was introduced.

## Artifact identities

SHA-256 values identify the exact JAR bytes. The new JARs and POMs were fetched
from both `repo.maven.apache.org` and `repo1.maven.org`; both endpoints produced
identical bytes. Publisher SHA-256 sidecars matched for bcutil, and publisher
SHA-1 sidecars matched for JMRTD/Scuba (those coordinates do not publish SHA-256
sidecars). The repository pins SHA-256 for all six new files.

| Artifact | SHA-256 |
| --- | --- |
| bcprov 1.78.1, retained | `add5915e6acfc6ab5836e1fd8a5e21c6488536a8c1f21f386eeb3bf280b702d7` |
| bcutil 1.71, reproduced old failure | `ac75ae3fabf2cb81210b3648fbe36aaed8d8c453bbeaac40e3b5031c7677197a` |
| JMRTD 0.7.34, reproduced old failure | `dde76ca9588e7e18bea30bd1ef4cf3e68938f2521cbe39b826ed110c9a9fe0b6` |
| bcutil 1.78.1 | `d9fa56f97b0f761ce3bc8d9d74c5d7137a987bf5bd3abfe1003f9bafa45a1d2f` |
| JMRTD 0.7.42 | `918e1977276ba9edd537af14e020c522bf4cd4e2fceb49b06e6f68936bfb8727` |
| Scuba smartcards 0.0.20 | `0ff14b1b559ee5be6a6d2b08b681f938d33a0b88a8f09e5d080613f32cf00cb8` |
| retained IDensic 1.31.3 transformed runtime classes | `a81e5bcba66b7c26bffdff77428cf15e7a4a16ee734b6626e835f3314e4c9686` |
| retained scuba-sc-android 0.0.23 transformed runtime classes | `ec87aff72b035586046974554332cdc160e3b53e8ace8ddf6bd9436b1e2a671d` |

## Compiled caller checks and limits

The audit resolved the actual Production Release external classpath with strict
Gradle verification, then inspected constant-pool member references and member
hierarchies in those JARs and existing compiled project classes. The resolver
included Java 17 `java.base` definitions for inherited JDK methods; it excluded
multi-release JAR overlays, as Android uses the base classes.

All 110 direct BC member references in the compiled `common`, `sorasubstrate`
and xcrypto wallet classes resolve against the retained provider and updated
utility JAR. Replacing bcutil and JMRTD removes the old closure's 81 unresolved
bcutil member references and one unresolved JMRTD member reference. Optional BC
JSSE/FIPS/PKIX adapters in unrelated libraries are outside this bounded check;
this audit does not claim every app code path was executed.

All 14 IDensic references into JMRTD retain their exact JVM descriptors. Their
callers are `com.sumsub.sns.internal.nfc.d` and `d$e`, covering:

- `PassportService(CardService,int,int,boolean,boolean)`, `open`,
  `sendSelectApplet(boolean)`, `getInputStream(short,int)`,
  `doPACE(AccessKeySpec,String,AlgorithmParameterSpec,BigInteger)` and
  `doBAC(AccessKeySpec)`;
- `CardAccessFile(InputStream)` and `getSecurityInfos()`;
- `PACEKeySpec(byte[],byte)`;
- `SecurityInfo.getProtocolOIDString()` and `getObjectIdentifier()`;
- `PACEInfo.getObjectIdentifier()`, `getParameterId()` and
  `toParameterSpec(BigInteger)`.

All 270 references into Scuba from updated JMRTD, unchanged IDensic and unchanged
scuba-sc-android resolve with smartcards 0.0.20 and the retained Android adapter.
IDensic contains no direct SODFile/SignedDataUtil caller. Its compiled NFC
operations alone therefore do not prove a full KYC flow reaches the reproduced
signed-data-parser failure. The exact packaged parser incompatibility is proven;
physical NFC transport, service responses, full KYC behavior and device provider
behavior still require their existing qualification.

## Regression and candidate checks

`BouncyCastleKycCompatibilityTest` runs on the app's resolved unit-test classpath.
Its five cases pass: CMS tagged-content parsing; JMRTD signed-data parsing;
CardAccess/PACE parsing and curve parameters; the published ICAO BAC key,
encryption and MAC vectors; and JMRTD EC key encoding, ECDSA signing/verification
and ECDH agreement. It uses synthetic CMS content and ephemeral test keys.

The BAC vectors come from [ICAO Doc 9303 Part 11, Appendix D](https://www.icao.int/sites/default/files/publications/DocSeries/9303_p11_cons_en.pdf).
JMRTD leaves DES parity bits unnormalized; the test normalizes copies when
comparing the published key representation, then verifies the actual published
ciphertext and retail MAC using the original derived keys. The complete
`deriveKey(byte[],String,int,byte[],int,byte)` source method is byte-identical
between JMRTD 0.7.34 and 0.7.42 (SHA-256
`cbb62c783f1132755def341a7359e6057ed862d87798f7b9a5b110efdf4c2a18`).
This parity representation is pre-existing behavior.

All 565 production Release configurations were rematerialized with only the four
named coordinates unlocked. The 31 lockfiles still cover 566 configurations,
including the settings catalog. Source audit, the 112 lock mutation checks and
the dependency/signing verifier's 29 rejection cases pass. Independent review,
credential closure, protected signing and production admission remain blocked.
The strict offline Production Debug APK/AAB build and Gradle model lock coverage
pass. The Release runtime, Debug runtime and Debug unit-test runtime all resolve
the same five KYC/BC coordinates and exact JAR hashes (including retained
scuba-sc-android 0.0.23). Both new packages pass the native gate on all 16 64-bit
libraries, and APK `zipalign -c -P 16 4` passes:

| New Debug artifact | SHA-256 |
| --- | --- |
| APK | `e3da0c539599f0ae8f0028879200e4eff2107102c1f5c60cdf59e5748d01d6ec` |
| AAB | `c1664aff9e8c4cc85bbd494a4ab9b13b38adddad130f60d83cc6e30031e141aa` |

The previous 16 KB UI flow evidence remains bound to code head `2aa1a378` and
its recorded APK; it is not claimed for these newly built bytes. Physical KYC,
signed/minified Release, and protected review remain open.

Bounded reproducer, original/updated parser logs, artifact inventory, complete
IDensic/JMRTD and Scuba member-reference results, upstream checksum observations,
and local build/test logs are retained by the operator under
`PRODUCTION-READINESS-EVIDENCE/android-bouncycastle-kyc-20260925` in the parent
workspace. They are local qualification evidence and grant no release authority.
