# Android dependency and signing review admission

Repository booleans and certificate strings are never sufficient to qualify Android dependencies
or production signing. The production environment must supply one canonical
`sora-android-dependency-signing-review-v1` manifest and two detached SHA-256/P-256 signatures:
one from the protected review-evidence producer and one from a distinct independent reviewer.

The environment independently pins both P-256 SPKI SHA-256 identities, the exact positive review
sequence, candidate source revision, and review-contract SHA-256. The contract binds the current
Gradle dependency-provenance and signing-identity files, verification metadata and its digest,
vendor provenance and whole-tree manifest, 31-lock/271-configuration identities, and both public
production certificate fingerprints. A manifest is admitted only when those protected values and
the current checkout projections all agree byte-for-byte.

The signed manifest also carries nonzero SHA-256 identities for the external source-to-binary,
license/notice, SBOM, build-attestation, repository-allowlist, verification-metadata, lock,
IDensic/TensorFlow workaround, credential-incident closure, and Play signing-continuity review
receipts. The receipts themselves remain in protected review systems; their exact identities are
retained without copying secrets or private material into source or candidate evidence.

Both signatures cover the exact canonical JSON bytes and use fixed 64-byte IEEE-P1363 encoding
with canonical low-S P-256 scalars; DER and high-S alternatives are rejected.
Inputs must be owner-only, stable, regular, non-symbolic, and singly linked. The review must be
current, all named dependency, credential-incident, workaround, and signing-continuity checks must
be true, and producer/reviewer key IDs and pins must differ.

Only the two `.blocked.json` templates are checked in. Qualified manifests, signatures, public-key
files, and admission receipts are protected runtime inputs and must remain absent from source. The
admission authorizes dependency/signing qualification only; it never signs an artifact or
authorizes release or production mutation. The immutable candidate package retains and re-verifies
the exact manifest, signatures, public keys, and non-authorizing admission receipt.

After the retained Play and upload certificate fingerprints have replaced their deliberate `null`
placeholders and the reviewed source revision is immutable, compute the exact non-authorizing
contract digest that the protected manifest and workflow variable must pin:

```sh
ANDROID_DEPENDENCY_SIGNING_REVIEW_SOURCE_REVISION="$(git rev-parse HEAD)" \
  node scripts/verify-android-dependency-signing-review.mjs --print-contract-sha256
```

This mode reads only the checked-in dependency/signing projections and prints their contract hash.
It does not read a manifest, signatures, keys, or sequence; it cannot produce an admission receipt.
