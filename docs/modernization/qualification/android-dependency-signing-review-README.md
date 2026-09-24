# Android dependency and signing review admission

Repository booleans and certificate strings are never sufficient to qualify Android dependencies
or production signing. The production environment must supply one canonical
`sora-android-dependency-signing-review-v1` manifest and two detached SHA-256/P-256 signatures:
one from the protected review-evidence producer and one from a distinct independent reviewer.

The environment independently pins both P-256 SPKI SHA-256 identities, the exact positive review
sequence, candidate source revision, and review-contract SHA-256. The contract binds the current
Gradle dependency-provenance and signing-identity files, verification metadata and its digest,
vendor provenance and whole-tree manifest, 31-lock/566-configuration identities (565 production
Release configurations plus settings), and both public production certificate fingerprints. A
manifest is admitted only when those protected values and
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

The authenticated Play Console inventory for `jp.co.soramitsu.sora` confirms that Play App Signing
is active and records the same SHA-256 certificate fingerprint for the app-signing and upload
identities: `b35dfe16cb3226da4432607288c6287362c5e623532c428f933d552297e9e3e0`.
The Play-generated Digital Asset Links statement carries the same public fingerprint, so the public
identity can be cross-checked without handling the keystore or any password. This observation is
not the independent continuity admission and does not qualify a bundle by itself.

The retained credentials remain in Jenkins under the pinned
`jenkins-library@65079bbe356bca4a3d5a1964e360498735afa1f0` pipeline. Its credential IDs are
`android_keystore_sora`, `android_keystore_storepass_sora`, `android_keyalias_sora`, and
`android_keypass_sora`; these are bindings, not secret values. No keystore is checked in or present
locally, and the GitHub qualification secret has not been provisioned. When the protected operator
provisions it, the workflow accepts the keystore only as `CI_KEYSTORE_BASE64`, decodes it to the
fixed owner-only `$RUNNER_TEMP/sora-android-production-upload.keystore`, exports only that runtime
path as `CI_KEYSTORE_PATH`, and removes that exact path in an unconditional cleanup step. Password
and alias inputs remain step-scoped secrets and are never copied to evidence.

Now that the public fingerprints are populated, an immutable reviewed source revision can be used
to compute the exact non-authorizing contract digest that the protected manifest and workflow
variable must pin:

```sh
ANDROID_DEPENDENCY_SIGNING_REVIEW_SOURCE_REVISION="$(git rev-parse HEAD)" \
  node scripts/verify-android-dependency-signing-review.mjs --print-contract-sha256
```

This mode reads only the checked-in dependency/signing projections and prints their contract hash.
It does not read a manifest, signatures, keys, or sequence; it cannot produce an admission receipt.
Release remains blocked until the retained keystore is provisioned, a signed candidate matches the
upload fingerprint, and distinct protected producer/reviewer authorities admit the signing and
Play-continuity evidence.

The current materialization reconciliation records 565 production Release configurations plus the
settings catalog configuration, 566 in total. The signed v1 object shape is unchanged; its exact
configuration count and current provenance/input hashes reject a receipt for the prior 271-entry
materialization. Updating these observed identities grants no review or signing authority. Metadata
and lock statuses remain
`materialized-unreviewed` until the required independent review and authenticated admission succeed.

The current app Release lock selects `org.bouncycastle:bcprov-jdk18on` 1.77 for compile and
1.78.1 for runtime, alongside runtime `bcutil-jdk18on` 1.71. Offline Gradle dependency insight
confirms the split. This is an unresolved compatibility review item for wallet cryptography and
KYC; lock and checksum verification alone do not establish on-device behavior. The protected
review must assess the graph and retained-wallet/KYC device evidence before admission.

The vendored provenance inventory still marks the PayWings KYC, OAuth, and IDensic AARs as
closed-source packages without completed independent binary review. The `xcrypto:1.2.7` AAR
contains four ABI variants of `libsr25519java_1.so`; its source revision and Cargo lock are
pinned, but source-to-binary reproduction review is incomplete. Their recorded hashes establish
which bytes were materialized, not that these binaries correspond to reviewed source. Keep the
vendor admission blocked until the independent receipts exist.
