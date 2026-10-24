# Polkamarkt full-extrinsic qualification

This qualification is intentionally blocked in source. The two mobile fixtures must retain
`reviewedWebAndRuntimeReceiptQualified=false`, `reviewedReceipt=null`, and the exact blocker until
all evidence below exists. A Debug build is not permission to promote mutations. Android release
verification records the missing receipt as a blocker; iOS Release verification rejects it.

## Evidence producers

Produce three independent strict-JSON receipts with format
`sora-mobile-polkamarkt-platform-extrinsic-receipt-v1`:

1. `reference`: check out the already pinned Polkamarkt web revision
   `893783ba6a19c33043eb5dabe42d949c14d0f257`, use its pinned `polkadotApi@11.2.1`, and load the
   checked-in runtime metadata bytes whose SHA-256 is
   `2b49c3cbf682d8b88985a04a60a958de3ef5de77d282c3622bdae53f7e4fbabf`.
2. `android`: exercise the production `RuntimeSnapshot` and `ExtrinsicBuilder` paths with the same
   public test signing context. Do not substitute a hand encoder for the builder being qualified.
3. `ios`: exercise the production `RuntimeCoderFactory`, `ExtrinsicBuilder`, and dynamic decoder
   with the same public test signing context. The metadata must be supplied as a stable, exact-hash
   input; do not fetch mutable live bytes during vector generation.

Each producer must resolve `Polkamarkt` and the five call discriminants from the reviewed metadata,
not source constants. Each of the ordered `buy`, `sell`, `claim_market`, `claim_markets`, and
`claim_creator_fees` vectors contains the exact fixture arguments plus:

- pallet and call indices, SCALE arguments, and full call bytes;
- raw signature payload and the actual signing input after the 256-byte threshold rule;
- the public test signer, raw sr25519 signature, signed extrinsic, and extrinsic hash;
- a canonical semantic decode projection and its SHA-256;
- an exact `sora-mobile-polkamarkt-native-cryptographic-proof-v1` object signed by the
  reviewed producer-verifier key. The signed statement binds the candidate receipt,
  producer source revision/tree, generator, verifier revision/binary, runtime metadata,
  signing context, and all five vectors, and attests successful sr25519 verification and
  full metadata decode/projection parity.

The producer receipt and native proof have exact keys enforced by
`scripts/qualify-polkamarkt-extrinsic-receipts.mjs`. The receipt must state that no phrase, seed,
private key, wallet address, production account identifier, or other private signing material is
present. Never print the test seed or any production payload. Sr25519 signatures may legitimately
differ between implementations; call bytes, signing prehash, and semantic projection may not.

Before any merge attempt, independently review and replace the deliberately null admission values
in the merger and both mirrored fixtures with the exact non-production qualification account and,
for reference, Android, and iOS, the source revision/tree, generator SHA-256, native verifier
revision/binary SHA-256, and verifier public-key SHA-256. All three copies are an admission contract;
command-line evidence cannot choose or relax them. Leaving any value null must keep qualification
blocked.

## Independent review and merge

After the three candidate files are independently inspected, create a strict review receipt with
format `sora-mobile-polkamarkt-extrinsic-review-v1`. It binds the exact SHA-256 of the blocked input
fixture and all three raw candidate receipts, records a positive integer review epoch, a nonempty
reviewer role, `decision=qualified`, and the same all-false privacy object. Sign the exact review
receipt bytes with the independently controlled Ed25519 review key. Supply the protected SHA-256 of
that key's SPKI DER through `POLKAMARKT_EXTRINSIC_REVIEW_KEY_SHA256`; it is never read from the
fixture being promoted.

After the merger output has been independently reviewed, protect the SHA-256 of the exact qualified
fixture outside the repository as `POLKAMARKT_EXTRINSIC_QUALIFIED_FIXTURE_SHA256`. Android and iOS
Release gates require both independent pins and compare the latter with the fixture bytes. Never
derive or export that value from the checkout in the release workflow: doing so would turn a
tamper-evident approval into a self-assertion.

With absolute, canonical, regular, non-symlink input paths, run the merger under an explicitly
reserved guarded Node owner:

```text
node scripts/qualify-polkamarkt-extrinsic-receipts.mjs \
  --fixture /absolute/polkamarkt_web_contract.json \
  --reference /absolute/reference-receipt.json \
  --android /absolute/android-receipt.json \
  --ios /absolute/ios-receipt.json \
  --review /absolute/review-receipt.json \
  --review-key /absolute/review-key.json \
  --review-signature /absolute/review-signature.json \
  --out /absolute/new-qualified-polkamarkt-contract.json
```

The output path must not exist. The merger never overwrites a fixture, forces a successfully
created output to mode `0600`, and attempts to remove a failed partial output only when its
device/inode still matches the file created by that invocation. It rechecks metadata/runtime
identity, the exact vector order, arguments and SCALE bytes, full call construction, signing
threshold behavior, BLAKE2b-256 extrinsic hashes, decoded-projection hashes, cross-platform byte
parity, privacy, every signed native-proof binding, the protected review key, and the detached
review signature. The generic Node merger does not contain a Substrate metadata decoder or
sr25519 verifier: each reviewed native verifier performs those operations and signs the exact
candidate-bound proof statement; the merger verifies that Ed25519 signature against the
checked-in verifier-key hash. It embeds the exact raw candidate/review/key/signature receipts so
offline review can reproduce the decision. The Release gate also invokes `--validate-qualified`,
which reconstructs the blocked input and all embedded evidence, verifies the native-proof and
independent-review signatures again, rebuilds the expected composite, and requires an exact
transition match. The independently protected exact whole-fixture SHA-256 remains an additional
approval boundary; the shell/Node gate does not claim to perform sr25519 or metadata decoding
itself.

The 512 KiB candidate-receipt cap is an aggregate evidence-file bound and can reject a receipt
before a larger per-field defense-in-depth bound is reached. Those per-field caps are not promises
that every maximum is independently reachable; real Polkamarkt payloads must remain well below the
aggregate cap.

Do not replace either mobile fixture or change the qualification boolean in the same step that
generates the output. Review the new file, independently reproduce every candidate hash and
producer-native sr25519/metadata result bound by its review receipt, then replace Android and iOS
fixtures byte-for-byte in a dedicated reviewed change and publish the exact whole-fixture hash only
after that review is complete.

Every Android production candidate must check out a protected, exact iOS source revision and every
iOS Release candidate must receive a protected, exact Android source checkout. Both gates compare
the sibling fixture byte-for-byte with their local stable snapshot and require the same independently
reviewed whole-fixture SHA-256. Missing sibling provenance is a hard failure, not permission to rely
on an unpaired local pin.

Configure Android's protected `POLKAMARKT_IOS_REPOSITORY` and
`POLKAMARKT_IOS_SOURCE_REVISION`; its workflow supplies `POLKAMARKT_IOS_FIXTURE_PATH` from the
sparse pinned checkout using the read-only, non-persisted `POLKAMARKT_IOS_READ_TOKEN` secret.
Configure iOS Release with `POLKAMARKT_ANDROID_SOURCE_ROOT`,
`POLKAMARKT_ANDROID_SOURCE_REVISION`, and `POLKAMARKT_ANDROID_FIXTURE_PATH` from its pinned sibling
checkout. Also provide an absolute, regular, executable reviewed Node runtime as
`POLKAMARKT_NODE_BINARY` and its independently pinned SHA-256 as
`POLKAMARKT_NODE_BINARY_SHA256`; iOS uses that exact binary and the qualifier from the protected
Android checkout for installed-evidence replay. Never point either path at a copied local fixture
that is not inside the stated checkout.
These parity revisions identify the reviewed fixture-promotion commits; they are not the earlier
producer-code revisions recorded inside the candidate receipts. Because a commit cannot embed its
own not-yet-known hash, never require a producer revision recorded inside the fixture to equal the
later fixture-promotion commit. Instead, both Release gates must have the producer commit objects
available and require a clean Git diff from each reviewed producer revision to the candidate's
current revision across all production source, dependency, and build-contract pathspecs. Any such
implementation drift invalidates the old proof and requires new native receipts.

## Guarded validation still required

No command in this section is execution evidence until a host-slot owner reaches observed terminal.
After the reviewed composite is installed, the required sequence is:

1. run `node scripts/test-polkamarkt-extrinsic-qualification.mjs` under the guarded owner. Its
   positive path is replay validation of the installed, genuinely qualified fixture; synthetic
   candidate data is used only for rejection tests. Mutations cover duplicate keys, unknown or secret
   fixture fields, wrong metadata, inconsistent indices, altered call/payload/prehash/projection
   bytes, malformed signer envelopes, invalid signature/extrinsic hashes, noncanonical mortal eras,
   mismatched signing contexts, false native-attestation flags, substituted review keys, and bad
   review signatures; separately run producer-native negative tests for hardcoded/swapped metadata
   resolution, invalid sr25519 signatures, and false metadata round trips because the generic merger
   deliberately does not implement those cryptographic/runtime decoders;
2. Android `PolkamarktWebContractTest` and `PolkamarktRuntimeCallFactoryTest`, followed by the
   affected production-flavor substrate and Polkamarkt unit suites;
3. the existing iOS `WalletModernizationTests` owner, still exactly 196 test methods, with the
   reviewed metadata/candidate receipts available to the qualification path;
4. Android strict release modernization verification and iOS Release dependency verification with
   the protected review-key SHA-256 and independently reviewed exact qualified-fixture SHA-256;
5. a non-broadcast canary decode/signature check, then the separately approved low-value live
   Polkamarkt canary before mutations are enabled.

The production qualification workflow first applies the static production source gate, including
installed-qualified replay validation, then runs that hermetic harness before any Gradle task. The
harness derives a private blocked merger input and therefore remains valid after a reviewed
qualified receipt replaces the blocked fixture; it never rewrites the installed fixture. The
currently checked-in source supplies the strict schema, fail-closed source tests, non-promoting
merger, protected native-proof and independent-review boundaries, and Release wiring. It does not
supply or fabricate the reviewed source manifests, native proofs, or five production byte-vector
receipts; those remain explicit production blockers.
