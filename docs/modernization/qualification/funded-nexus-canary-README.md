# Android funded Nexus canary receipt contract

`taira-canary.json` and `minamoto-canary.json` are blocked production-admission schema fixtures,
not manually editable checklists or live results. External receipts use their exact
`sora-android-funded-nexus-canary-v4` shape after an authorized release operator runs a funded
canary with the exact production candidate. Missing observations stay `null` or `missing`; never
turn absent evidence into a successful Boolean.

## Parser and size rules

The release verifier must fail closed unless each receipt:

- is a regular, non-symlink file no larger than 64 KiB;
- is strict UTF-8 JSON with no duplicate object keys, trailing data, non-finite numbers, or unsafe
  integers, and with JSON number tokens restricted to canonical safe integers (quantities remain
  decimal strings);
- has exactly the keys and nesting shown by the checked-in blocked receipt for that network, with
  no extension keys;
- contains at most 16 blocking reasons, each 1–240 Unicode scalar values; and
- bounds every free-form string to 256 Unicode scalar values and rejects C0/C1 control characters,
  including line breaks, except fixed URLs and identifiers, which must exactly match this contract.

All SHA-256 values are lowercase 64-character hexadecimal strings. Every reviewed finality-verifier
and finality-server source revision is a lowercase, nonzero 40-character commit ID; an all-zero
SHA-1 placeholder is invalid. Epoch values are positive safe integers in a verifier-supplied
release-evaluation window. A qualified AAB byte count is in `1..1073741824`; the version code and
submission attempt count are positive safe integers.

## Release-controller inputs

Qualification is intentionally impossible from the checked-in templates alone. A reviewed
post-bundle controller must provide the following paths as absolute, canonical paths to regular,
non-symlink files. The verifier opens and hashes the actual bytes; a path or digest copied into a
receipt is not evidence.

- `PRODUCTION_CANDIDATE_AAB_PATH`, `PRODUCTION_CANDIDATE_SOURCE_REVISION`,
  `PRODUCTION_RELEASE_EVALUATED_AT_EPOCH_SECONDS`, and (when the build does not use the checked-in
  default) the exact `CI_BUILD_ID` used as the AAB version code;
- `PRODUCTION_CANDIDATE_APKS_PATH` and `PRODUCTION_BUNDLETOOL_JAR_PATH`, whose actual bytes must
  match the installed-candidate attestation and pinned inspection-tool identity;
- `PI_PRODUCTION_PROBE_RECEIPT`, captured no more than five minutes before evaluation;
- `TAIRA_FUNDED_CANARY_RECEIPT_PATH` or `MINAMOTO_FUNDED_CANARY_RECEIPT_PATH`;
- `<NETWORK>_FUNDED_CANARY_APPROVAL_RECEIPT`;
- `<NETWORK>_FUNDED_CANARY_LOW_VALUE_POLICY`;
- `<NETWORK>_FUNDED_CANARY_PI_RECEIPT`, captured during the five minutes before execution starts;
- `<NETWORK>_FUNDED_CANARY_ARTIFACT_IDENTITY_RECEIPT`;
- `PRODUCTION_FINALITY_TRUST_MANIFEST_RECEIPT`;
- `<NETWORK>_FUNDED_CANARY_FINALITY_TRUST_CONTEXT`;
- `IROHA_REVIEWED_FINALITY_NATIVE_CANARY_RECEIPT_PATH`;
- `IROHA_REVIEWED_FINALITY_VERIFIER_ARTIFACT_PATH` and
  `IROHA_REVIEWED_FINALITY_PLATFORM_ARTIFACT_PATH`, whose stable regular-file bytes must hash to the
  reviewed verifier and platform-artifact identities in the signed evidence;
- `<NETWORK>_FUNDED_CANARY_CONSUMPTION_RECEIPT`; and
- `<NETWORK>_FUNDED_CANARY_EVIDENCE_BUNDLE`.

`<NETWORK>` is exactly `TAIRA` or `MINAMOTO`. The approval, policy, evidence bundle, and qualified
canary receipt remain outside the source tree and come from access-controlled release evidence.
The GitHub qualification workflow now contains the source-side controller handoff, but the reviewed
controller deployment, pinned TLS SPKI, bearer token, funded accounts, physical devices, and
reviewer signing keys remain protected external inputs. Their absence keeps production admission
blocked; the repository never synthesizes them.

Immediately before the first funded mutation, `verify-production-modernization.mjs --pre-canary`
must pass. That mode defers only the Taira canary, Minamoto canary, and exact cross-network-pair
receipts which the controller is about to produce; every other production prerequisite remains
mandatory. The workflow then makes one non-retried, idempotency-keyed upload of the exact candidate
AAB to the pinned controller and accepts only the exact 22-file owner-only POSIX ustar bundle.
The local extractor rejects links, duplicate or missing members, checksum or padding defects,
non-owner-only modes, invalid or duplicate-key JSON, size-limit violations, and trailing data. Its
receipt is non-authorizing: the qualified internal receipts must still authenticate themselves and
pass the strict release verifier. The original controller tar and extraction receipt are retained
inside the immutable candidate package so later rollout cohorts revalidate the same handoff bytes.

## Candidate binding

Qualification must hash the bytes of the real AAB supplied to the release verifier. The AAB must
itself be a regular, non-symlink file and must not be resolved from a path stored in the receipt.
The computed hash and byte count must equal `candidate.aabSha256` and `candidate.aabBytes`.
`candidate.applicationId` is exactly `jp.co.soramitsu.sora`, the build variant is exactly
`productionRelease`, and version name/code and source revision must match the same built candidate.
The signed installed-candidate receipt separately proves the AAB upload certificate and the
Play-installed app-signing certificate; those two identities must never be conflated.

`candidate.candidateBindingSha256` binds, in the listed order, the contract ID, platform,
application ID, build variant, version name, version code, source revision, AAB SHA-256 and byte
count, upload/install/APK-set identity, the six SORA2 runtime identity values, the complete local
and remote feature state, separate signer and finality binding SHA-256 values, and the full current
network identity. The release tooling must use one reviewed
canonical UTF-8 serialization and independently recompute the digest; a receipt-provided digest is
never self-authenticating. Taira and Minamoto therefore receive distinct candidate bindings even
when they exercise the same AAB.

The canonical serialization is UTF-8, one line per entry below, joined with `\n` and with no final
newline:

```text
contractId=<contractId>
platform=<platform>
applicationId=<applicationId>
buildVariant=<buildVariant>
versionName=<versionName>
versionCode=<versionCode>
sourceRevision=<sourceRevision>
aabSha256=<aabSha256>
aabBytes=<aabBytes>
aabUploadCertificateSha256=<aabUploadCertificateSha256>
installedAppSigningCertificateSha256=<installedAppSigningCertificateSha256>
generatedApksSha256=<generatedApksSha256>
artifactIdentityReceiptSha256=<artifactIdentityReceiptSha256>
installedCandidateBindingSha256=<installedCandidateBindingSha256>
sora2SourceRevision=<sora2Runtime.sourceRevision>
runtimeSpecVersion=<sora2Runtime.specVersion>
runtimeTransactionVersion=<sora2Runtime.transactionVersion>
runtimeGenesisHash=<sora2Runtime.genesisHash>
runtimeMetadataSha256=<sora2Runtime.metadataSha256>
runtimeTypesSha256=<sora2Runtime.typesSha256>
featureFlagSnapshotObservedAtEpochSeconds=<featureFlags.snapshotObservedAtEpochSeconds>
featureFlagSnapshotSha256=<featureFlags.snapshotSha256>
featureFlagSourceReceiptSha256=<featureFlags.sourceReceiptSha256>
nexusAvailable=<featureFlags.nexusAvailable>
nexusSendsAvailable=<featureFlags.nexusSendsAvailable>
polkamarktVisible=<featureFlags.polkamarktVisible>
polkamarktMutationsAvailable=<featureFlags.polkamarktMutationsAvailable>
tairaDefaultVisible=<featureFlags.tairaDefaultVisible>
tairaPreferenceIsExplicit=<featureFlags.tairaPreferenceIsExplicit>
tairaEffectiveVisible=<featureFlags.tairaEffectiveVisible>
localNexusSendsQualified=<featureFlags.localNexusSendsQualified>
signerBindingSha256=<signer.bindingSha256>
finalityBindingSha256=<finality.bindingSha256>
networkId=<network.networkId>
chainId=<network.chainId>
i105Discriminant=<network.i105Discriminant>
toriiBaseUrl=<network.toriiBaseUrl>
explorerBaseUrl=<network.explorerBaseUrl>
isTestnet=<network.isTestnet>
```

The SORA2 identity embedded in the candidate must remain exactly:

- source revision `411dcdb70c5c00b21482a44d02334840d5f338c6`;
- spec and transaction version `130`;
- genesis `0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5`;
- metadata SHA-256 `2b49c3cbf682d8b88985a04a60a958de3ef5de77d282c3622bdae53f7e4fbabf`; and
- type-definition SHA-256 `e87760d7a566d1b1b3d21a1e76ad70990fd54e14e6af3ba27dd4440461063601`.

## Network, flags, signer, artifact identity, and approval

The last shipped Taira recovery identity is chain
`fc56984b-2be7-431d-840e-21514d1883f0` with discriminant `369`. It remains available only for
derivation and retained-journal recovery. It is not current routing authority. Production routing
requires the dual-signed contract documented in
`taira-deployment-manifest-README.md`; without that exact build binding every Taira Torii route
fails before transport. The checked-in blocked canary and rollout contracts are likewise not
authoritative evidence of the identity currently served by a public endpoint. Minamoto is exactly chain
`00000000-0000-0000-0000-000000000753`, discriminant `753`, Torii
`https://minamoto.sora.org`, explorer `https://minamoto-explorer.sora.org`, and a mainnet. Both use
the symbol `XOR` and exact alias `xor#universal`.

The UUID `809574f5-fee7-5e69-bfcf-52451e42d50f` is also retained as recovery evidence. Repository
source never activates either UUID from its own assertion: protected operator and reviewer inputs
must explicitly confirm the retired/current ordering. Schema-77 current-chain queries consume both
the admitted UUID and the exact manifest-digest namespace written into each new Taira `localId`;
every null, other UUID, unbound ID, or other-manifest ID remains immutable recovery evidence. This
also prevents a newly selected mapping from reinterpreting an older row that happens to carry the
same UUID. Until that
manifest and a challenge-bound Torii observation match, Taira funded canaries and production
mutation admission remain blocked. Never
reinterpret retained journal rows or route Torii traffic from a documentation claim; retained keys
and discriminant-369 addresses remain unchanged.

The feature snapshot binds all five decoded PI `mobileConfig` values, its actual strict receipt
hash and observation time, the effective Taira choice, its explicit-preference state, and the local
Nexus-send build gate. The canary snapshot must be captured no more than five minutes before the
execution starts and may not postdate it. A separate PI probe, also no more than five minutes old,
rechecks all production flags at release admission. Qualification requires Nexus and Nexus sends
available, the local Nexus-send gate true, and Taira effectively visible from an explicit user
choice for a Taira run. Remote configuration cannot substitute for the local release gate.

The PI capability digest uses the same newline/no-final-newline rule and exactly this projection:

```text
schemaVersion=<schemaVersion>
endpoint=<endpoint>
serviceId=<serviceId>
ecosystem=<ecosystem>
chainId=<chainId>
network=<network>
readOnly=<readOnly>
mobileConfigHealthBound=<mobileConfigHealthBound>
historyBlockHeightContractDeployed=<historyBlockHeightContractDeployed>
sora2GenesisHash=<sora2GenesisHash>
finalizedCheckpoint=<finalizedCheckpoint>
finalizedCheckpointBlockHash=<finalizedCheckpointBlockHash>
capabilityFinalizedCheckpoint=<capabilityFinalizedCheckpoint>
capabilityFinalizedCheckpointBlockHash=<capabilityFinalizedCheckpointBlockHash>
nexusAvailable=<capabilities.nexusAvailable>
nexusSendsAvailable=<capabilities.nexusSendsAvailable>
polkamarktVisible=<capabilities.polkamarktVisible>
polkamarktMutationsAvailable=<capabilities.polkamarktMutationsAvailable>
tairaDefaultVisible=<capabilities.tairaDefaultVisible>
```

The signer block must match the reviewed `productionSignerBinding` and the independently qualified
mutation-signing native canary: one concrete signer adapter type and source hash, exact dependency
coordinate and artifact hash and size,
exact ABI 21, export-inventory digest, provenance-receipt digest, native-canary digest, binding
digest, and the Android OS security provider actually used. A placeholder/unavailable signer,
missing artifact, local debug binary, ABI compatibility fallback, or hash-only self-claim is never
qualified. The observed
provider is exactly `AndroidKeyStore`. `signer.bindingSha256` uses
the same canonical serialization rule and exact line order:

```text
status=<status>
adapterType=<adapterType>
adapterSourcePath=<adapterSourcePath>
adapterSourceSha256=<adapterSourceSha256>
artifactCoordinate=<artifactCoordinate>
artifactSha256=<artifactSha256>
artifactBytes=<artifactBytes>
requiredNativeAbi=<requiredNativeAbi>
observedNativeAbi=<observedNativeAbi>
exportInventorySha256=<exportInventorySha256>
provenanceReceiptPath=<provenanceReceiptPath>
provenanceReceiptSha256=<provenanceReceiptSha256>
nativeCanaryReceiptSha256=<nativeCanaryReceiptSha256>
androidSecurityProvider=<androidSecurityProvider>
```

Finality is a separate reviewed authority. `finality.bindingSha256` binds the exact readiness
receipt, non-`Unavailable` read-only adapter type/source, verifier source revision and artifact,
native-finality canary, signed trust manifest, network-specific trust context, server contract and
route identities, and the exact attestation/bundle routes. The manifest and each network context
are signed by the pinned independent-reviewer Ed25519 key. The context binds the current chain UUID,
expected node/build/protocol/consensus identity, validator roster and quorum, canonical signed
genesis, genesis key, trusted first height, and stateful successor-verification context. Context
review must not postdate native-canary review, native-canary review must not postdate manifest
review, and manifest review must happen before execution begins.

The exact native-finality receipt is not an opaque digest. It binds the material manifest
projection (verifier, server contract/routes, and both network-context receipts) rather than the
completed manifest-file hash; the completed signed manifest then binds the native receipt hash.
This explicit acyclic order prevents mutually recursive receipt hashes. The native receipt must
report required and observed ABI `21`, a clean `reviewed-platform-release` artifact, the reviewed verifier and manifest identities,
equal nonzero required/observed export inventories, and nine distinct KAT digests for complete
canonical Norito encode/decode round trips of the attestation and bundle, plus challenge, genesis,
tip, node signature, aggregate signature, successor proof, and finalized
projection behavior. The reviewed platform artifact, verifier artifact, manifest binding, export
inventory, and all nine KAT digests must also be nonzero and mutually independent. The receipt binds
an individual `*KatPassed: true` result beside each of the nine KAT digests; an aggregate
qualification Boolean or a present digest cannot substitute for any per-KAT pass result. It also binds
the exact `/v1/bridge/finality/attestation/{height}` and `/v1/bridge/finality/bundle/{height}` routes
and response types `BridgeFinalityAttestationV1` and `BridgeFinalityBundle`. Artifact identity, ABI,
export inventory, canonical Norito round trip,
attestation/bundle projections, and trust-context binding must all be independently qualified. A
dirty local DEBUG artifact is always rejected and can never replace the unavailable production
adapter.

The external installed-candidate receipt is dual-signed by the pinned release-operator and
independent-reviewer Ed25519 keys. It binds the actual AAB hash/size, application/version/source,
AAB upload certificate, retained Play app-signing certificate, reviewed Play mapping, exact pinned
bundletool and apksigner versions, actual `.apks` hash/size, installed package/version/certificate,
network, canary run ID, and privacy exclusions. It must state that the measured installed package
came from that exact APK set. This is the production link between source claims, bundle bytes, and
the app that performed the canary.

`config/funded-canary-trust.json` is the sole checked-in trust-root contract. A qualified version
pins four distinct Ed25519 SPKI public keys and fingerprints for the release operator, independent
approver, independent reviewer, and append-only consumption ledger, plus the ledger store ID and
inspection-tool identities. The checked-in file is blocked and contains no fabricated key. Until
authorized release-system public keys replace that blocker, funded canaries cannot qualify.

Operator approval is deliberately pseudonymous. Store only an authorized release-system approval
digest and non-identifying roles, never a person's name. Approval must be dual-controlled, scoped to
the exact receipt network, issued before the canary starts, unexpired through completion, and bound
to a reviewed low-value policy digest. It is not valid merely because `approved` is true.

The release job supplies the authorized approval receipt and low-value policy as explicit inputs;
the verifier hashes their actual regular, non-symlink bytes and compares those hashes here. The
same rule applies to the capability snapshot, signer provenance/native-canary receipts, and the
privacy-redacted execution evidence bundle. Paths in this JSON are descriptive contracts only and
must never select the evidence the verifier trusts. A non-null but unverified digest is still
blocked.

The external approval receipt has exactly these root fields: `schemaVersion`, `contractId`,
`platform`, `networkId`, `candidateAabSha256`, `candidateBindingSha256`,
`lowValuePolicySha256`, `approvalNonce`, `approved`, `approvedAtEpochSeconds`,
`expiresAtEpochSeconds`, `approverRole`, `independentApproverRole`, `dualControlReviewed`, both
pinned authority key IDs and detached Base64 Ed25519 signatures, and `privacy`. Its contract ID is
`sora-android-funded-nexus-canary-approval-v1`; `approved` and `dualControlReviewed` are true, the
roles are exactly `release-operator` and `independent-approver`, both signatures verify over the
same canonical projection, and all identity, nonce, digest, and time values equal the canary receipt.

The external low-value policy has exactly `schemaVersion`, `contractId`, `platform`, `networkId`,
`assetAlias`, `maximumAmountCanonical`, `maximumFeeCanonical`, `maximumExecutions`,
`validFromEpochSeconds`, `validUntilEpochSeconds`, `reviewedAtEpochSeconds`, `reviewed`, and
`privacy`. Its contract ID is
`sora-android-funded-nexus-low-value-policy-v1`, its alias is `xor#universal`, and both maxima are
positive canonical Nexus decimal strings with at most 254 fractional digits. Independently of a
policy claim, neither maximum may exceed exactly `1` XOR. `maximumExecutions` is exactly one, the
validity interval covers the complete execution, and policy review must occur within that interval
and no later than the dual-control approval. A policy approved first and reviewed retroactively is
rejected.

## Execution and freshness

Every execution stage must be `qualified`, carry its own non-null observation time and SHA-256 of
privacy-redacted evidence, and fall within the canary start/completion interval. Each stage digest
is recomputed from the domain `sora-android-funded-nexus-canary-stage-v1`, canary run ID, candidate
binding, network, stage name, time, status, and exact approved assertions; it is never an opaque
digest or a digest of a transaction identifier. The aggregate
evidence-bundle digest must bind all stage digests and their exact assertions. The independently
supplied bundle must use a reviewed, exact-key schema, stay below 256 KiB, contain no prohibited
privacy field, and hash to `execution.evidenceBundleSha256`. A qualified run must:

- record positive `sendAmountCanonical` and `feeAmountCanonical` Nexus decimal strings and
  independently prove each is no greater than both its approved policy maximum and the verifier's
  one-XOR hard ceiling;

- validate the receive address and selected-network I105 discriminant and observe funded XOR;
- validate recipient, arbitrary-precision amount, XOR balance, and XOR fee balance;
- obtain a positive fee for the canonical payload and revalidate it before signing;
- atomically reserve the signed one-use approval in the pinned append-only ledger before signing;
- use the exact candidate signer through OS-secured signing, revalidating that reservation, the
  selected wallet and network/account, deletion state, live Nexus/send gates, and quote identity,
  while never exporting the private key;
- journal the exact hash durably, revalidate the same reservation immediately before the one
  handoff, and match the local and Torii receipt hashes;
- observe committed terminal status resolved from global state and bind its positive
  `committedBlockHeight`, then require an attested finality checkpoint on the exact wallet network
  and current chain UUID with `finalizedBlockHeight >= committedBlockHeight` and a canonical,
  lowercase, prefix-free, nonzero 64-hex finalized block hash;
- bind a fresh nonzero challenge deterministically to the canary run, candidate binding, network,
  chain, finalized height and block hash, attestation and bundle evidence, and the complete finality
  binding. The attestation and bundle evidence digests
  must be distinct and must resolve through the reviewed routes, verifier, native KAT receipt,
  signed manifest, and exact network trust context; genesis/tip proofs, node and aggregate
  signatures, canonical Norito projection, and one state view must all qualify. The trust context's
  positive `trustedFirstHeight` must be at or below the observed finalized height, and the signed
  finality stage must explicitly set
  `liveBoundedSequentialStatefulSuccessorChainVerified: true` after live, bounded, sequential,
  stateful verification from that trusted context through the finalized successor chain;
- reconcile sender, receiver, and fee balance deltas;
- reconcile the exact transaction through the network-scoped explorer and bounded complete-fanout
  history; and
- cold-restart and recover through the capability-limited read interface by exact-hash status only,
  with no signer, signed-byte, or submission capability.

The submission attempt count is exactly one, automatic retry and ambiguous submission are false,
the terminal status is exactly `committed`, and the submission failure classification is exactly
`none`. A timeout or ambiguous result is a canary failure; it must not be retried under the same
approval.

Completion must be after start and no more than two hours later. The receipt must be recorded no
more than 24 hours after completion. At initial production admission, completion and the feature
snapshot must be no more than seven days old relative to the explicit release-evaluation epoch.
System wall clock alone is not release authority: CI supplies and records the evaluation epoch.

The external evidence bundle has exactly `schemaVersion`, `contractId`, `platform`, `networkId`,
`canaryRunId`, `candidateBindingSha256`, `artifactIdentityReceiptSha256`,
`installedCandidateBindingSha256`, `approvalReferenceSha256`, `lowValuePolicySha256`,
`approvalNonce`, `consumptionReceiptSha256`,
`startedAtEpochSeconds`, `completedAtEpochSeconds`, `sendAmountCanonical`, `feeAmountCanonical`,
`submissionAttemptCount`, `automaticRetryAttempted`, `ambiguousSubmissionObserved`,
`terminalStatus`, `stages`, `independentReviewerRole`, the pinned reviewer key ID,
`independentReviewedAtEpochSeconds`, `independentReviewCompleted`, its detached Ed25519 signature,
and `privacy`. Its contract ID is
`sora-android-funded-nexus-canary-evidence-v1`. `stages` has exactly the eleven named stage objects
from the canary receipt and every field—including the committed height and complete finality
identity/challenge projection—must match byte-for-byte after strict JSON decoding. The
independent review occurs after completion and before the final receipt is recorded; its
non-identifying reviewer role is exactly `independent-reviewer`.

The external `sora-android-funded-nexus-approval-consumption-v2` receipt is signed by the pinned
ledger authority and binds the ledger store, approval nonce, canary run, candidate binding,
approval-receipt hash, atomic reservation time, finalization time, and distinct monotonically
increasing append-only reservation/finalization store versions. Qualification requires zero prior
reservations, exactly one recorded submission handoff, and zero ambiguous attempts. The atomic
reservation occurs after fee validation and no later than signing; both signing and handoff prove
they observed that same reservation. The ledger finalizes the record only after execution
completion and before independent review. This makes approval reuse fail before signing instead
of relying on an after-the-fact success receipt.

The receipt has exactly `schemaVersion`, `contractId`, `status`, `platform`, `ledgerStoreId`,
`networkId`, `approvalNonce`, `canaryRunId`, `candidateBindingSha256`,
`approvalReferenceSha256`, `reservedAtEpochSeconds`, `finalizedAtEpochSeconds`,
`reservationStoreVersion`, `finalizationStoreVersion`, `priorReservationCount`,
`submissionHandoffCount`, `ambiguousAttemptCount`, `atomicReservationAcquired`, the pinned ledger
authority key ID and detached Base64 Ed25519 signature, and `privacy`. Schema version is exactly
`2`, status is exactly `completed`, `atomicReservationAcquired` is true, and the finalization store
version is strictly greater than the reservation store version.

## Privacy and promotion

The privacy object is exact and must keep every exclusion false while
`redactedAggregateEvidenceOnly` is true. Do not record account IDs, addresses, transaction hashes,
phrases, seeds, keys, signed bytes, raw responses, device IDs, or operator names. Evidence digests
must be produced only after those fields are removed; a digest of a transaction hash is still a
transaction identifier and is forbidden.

`status: qualified` is accepted only when `blockingReasons` is an actual empty JSON array (not an
empty string or array-like value), every qualification field is
true, every identity is independently matched, and every detailed stage satisfies the rules above.
The main modernization verifier mirrors this exact-key contract and validates both checked-in
blocked receipts as fail-closed source fixtures. The production release controller must supply all
actual inputs listed above so the verifier can recompute identities and hashes rather than trust
receipt claims.

The `fundedTairaCanaryQualified` and `fundedMinamotoCanaryQualified` fields in the SDK pin remain
false pre-canary facts. They are never preclaimed or edited to make the canary circular:
every other SDK readiness criterion must already be true and signer/finality/artifact prerequisites
may enable the exact canary candidate, while the two external
signed v4 receipts and their admission record establish the funded results and independently gate
final production admission. The admission identity and every rollout identity retain both exact
receipt SHA-256 values alongside the candidate AAB/source/provenance identity.

Taira and Minamoto must also be independent executions. Their receipt hashes, candidate bindings,
finality bindings, canary run IDs, one-use approval nonces, and attestation challenges must all be
pairwise distinct. Reusing a network-scoped run or approval on the other network blocks admission
even if each receipt would pass in isolation.

`scripts/test-funded-canary-v4-contract.mjs` is a hermetic contract harness used by the static
verifier and GitHub qualification workflow. It constructs qualified synthetic Taira and
Minamoto proofs only in memory, then proves exactly 33 fail-closed mutations including preclaimed
readiness, finalized-before-committed or trusted-first-after-finalized height, missing live bounded
sequential/stateful successor-chain proof, challenge/bundle or current-chain drift, aliased
evidence/KAT/artifact identities, each of the nine independently false KAT pass flags, invalid native
ABI, dirty DEBUG artifacts, non-array manifest/context/native blocker collections, all-zero verifier
or server source revisions, cross-network reuse, and retroactive trust, manifest, or low-value-policy review. It
does not qualify either checked-in template and does not provide production keys or evidence.
