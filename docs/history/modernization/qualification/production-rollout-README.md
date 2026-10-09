# Production rollout advancement evidence

Ordinary non-rollout builds do not invoke `scripts/verify-production-rollout.mjs`. Invoking the
script with `PRODUCTION_ROLLOUT_TARGET_PERCENT` unset is a failure; release automation invokes it
only when an operator explicitly supplies a target of `1`, `5`, `25`, or `100`. Every target
requires `PRODUCTION_CANDIDATE_AAB_PATH` to be an absolute
path to the exact completed production AAB. The validator rejects an absent, empty, symbolic,
non-regular, changing, or larger-than-512-MiB artifact and hashes its bytes itself. The receipt's
`identity.candidateArtifactSha256` must equal that observed hash.

The exact-candidate qualification run writes the successful
`verify-production-modernization.mjs --release` JSON to the absolute
`PRODUCTION_QUALIFICATION_RECEIPT_PATH`. The rollout validator strictly parses and hashes that
receipt, requires empty failure/blocker arrays and a qualified
`sora-android-production-admission-v3`, and independently matches its AAB hash/size, source
revision, runtime-130 identity, and rollout trust-root hash. The v3 admission deliberately excludes
moving rollout epochs and PI receipts: it records only its own immutable qualification epoch and
the exact candidate-time PI receipt, both retained beside the AAB. One successful admission is
therefore reused byte-for-byte through the 1% -> 5% -> 25% -> 100% sequence. Its binding also covers the reviewed signing
identity, Iroha SDK pin, migration
qualification, both funded canary receipts, and funded-canary trust root. Target `1` is therefore
not an identity-only bypass around production qualification.

The admission also binds the production qualification repository, fixed workflow path, exact
workflow-file SHA-256, GitHub run ID, and first run attempt. A rollout invocation downloads the
named artifact from that exact run and obtains bounded strict GitHub API receipts for both the run
and its sole `sora-android-qualified-candidate` artifact. The local provenance validator requires a
successful completed `workflow_dispatch` of the bound workflow at the candidate source revision,
the same repository/head repository, attempt `1`, a non-expired artifact, and its canonical API
artifact ID, size, and `sha256:` digest. The privacy-safe controller request carries the selected
safe provenance projection and both API-receipt hashes, so a same-name artifact or self-asserted
admission from another run cannot authorize rollout.

Each rollout-gate invocation requires the absolute
`PI_PRODUCTION_RAW_LIVE_RECEIPT_PATH` produced by a fresh, read-only PI probe and the canonical positive
`PRODUCTION_ROLLOUT_EVALUATED_AT_EPOCH_SECONDS` recorded immediately before the protected rollout
controller emits its signed evidence. The validator
does not use ambient wall-clock time as release authority. It rechecks the bounded receipt, its
five-minute freshness relative to that explicit epoch, positive checkpoint ordering, a maximum
32-block finalized/indexed lag, capability implications, exact field sets, and privacy shape. It then derives
`checkpoint.capabilitySnapshotSha256` from the canonical live service/network/feature-capability
projection. All five release capabilities must be enabled; implication-only or emergency-disabled
states cannot qualify a production expansion. Dynamic timestamps and checkpoints are deliberately
excluded from the capability projection, while their freshness and consistency are still mandatory.
`identity.runtimeMetadataSha256` must
also equal the bytes of the reviewed production `sora2_metadata` asset; neither identity is a
self-asserted string.

The moving `checkpoint` block binds the actual strict PI receipt hash, the reviewed SORA2 genesis,
the positive finalized/indexed heights and canonical block hash returned by the reviewed SORA2
RPC, and the health-bound capability checkpoint/hash. The pinned telemetry source also attests
positive Minamoto and Taira finalized heights, nonzero genesis hashes, and finalized block hashes
under their exact chain IDs within five minutes of the evaluation. Every later gate revalidates the
complete prior signed receipt chain, requires unchanged network genesis, rejects any checkpoint
regression, and rejects a different block hash at an unchanged finalized height.

The capability projection is UTF-8 text with one `key=value` line, in this exact order:
`schemaVersion`, `contractId`, `endpoint`, `serviceId`, `ecosystem`, `chainId`, `network`, `readOnly`,
`mobileConfigHealthBound`, `configRevision`, `historyBlockHeightContractDeployed`, `nexusAvailable`,
`nexusSendsAvailable`, `polkamarktVisible`, `polkamarktMutationsAvailable`, and
`tairaDefaultVisible`. Its SHA-256 is the receipt value. The candidate binding then uses the exact
ordered fields implemented by `identityBinding`; reviewers must recompute both hashes rather than
copying them from an untrusted receipt.

Every invocation also requires `PRODUCTION_ROLLOUT_EVIDENCE_PATH` as an absolute, canonical path
to external release-controller evidence. The reader opens it without following a final symlink,
rejects a path containing symlink resolution, binds the opened descriptor back to the path inode
before and after the bounded read, and accepts only strict UTF-8 JSON with unique object keys and
canonical safe-integer number tokens. A source-tree filename or a transient path swap is not
rollout evidence.

`config/production-rollout-trust.json` is the only controller trust contract. A qualified file
pins two distinct Ed25519 SPKI public keys and fingerprints: a privacy-aggregate telemetry
collector and a production rollout authorizer. It also pins the aggregate telemetry source ID and
completeness-policy SHA-256. The checked-in file is deliberately `blocked` and contains no
fabricated key, source identity, or policy digest. An explicit rollout target cannot pass until the
authorized controller replaces those blockers with reviewed public trust material.
The exact qualified file hash must also equal the protected production-environment variable
`PRODUCTION_ROLLOUT_TRUST_SHA256`; a source change cannot replace controller keys and then
self-authorize with the replacement. The GitHub qualification job uses the protected `production`
environment and exposes that independently administered pin to candidate admission and rollout
validation; neither path treats possession of the source tree as trust authority.

Target `1` creates the initial rollout candidate. It has no prior cohort, but it still requires a
qualified external identity receipt with `fromCohortPercent` set to
`0`, `targetCohortPercent` set to `1`, and an empty `cohorts` array. The adjacent
`production-rollout-candidate.blocked.json` is its deliberately non-qualifying template. Targets
`5`, `25`, and `100` use an equivalent qualified external receipt and require the exact completed prior
cohorts. `production-rollout-advancement.blocked.json` remains a non-qualifying advancement
template, not evidence.

Target `1` requires `priorGateReceiptSha256: null` and all three prior-receipt path variables to be
empty. Target `5` requires `PRODUCTION_ROLLOUT_RECEIPT_1_PATH`; target `25` requires the `1` and `5`
paths; target `100` requires the `1`, `5`, and `25` paths. No extra path is accepted. Automation
reads each signed link and retrieves its predecessor by the exact SHA-256 URL, never by a mutable
“latest cohort” lookup. The validator hashes every receipt's actual inode-bound bytes, walks all
links back to the null-terminated 1% receipt, verifies every collector/authorizer signature and
cohort calculation again, requires the same stable candidate/admission identity, and
byte-compares every shared cohort prefix. Each newly completed cohort must start strictly after
the preceding gate evaluation and authorization. A later receipt therefore cannot rewrite,
backdate, or skip an earlier stage.

The v3 transition is deliberately fail-closed. A v2 admission, controller request, rollout receipt,
or prior-chain link is not accepted as v3 evidence and a v2/v3 chain cannot be mixed. The staged
rollout must restart at a new 1% v3 receipt, with a fresh 48-hour dwell before any v3 5% gate; v2
cohort history is retained for audit only and does not authorize a v3 expansion.
`scripts/lib/production-rollout-v3-contract.mjs` is the shared envelope authority used by the
admission/current/prior/cursor parsers and both local producers. The hermetic
`scripts/test-production-rollout-v3-contract.mjs` source contract executes those same predicates for
all five contexts: exact v3 pairs pass, while complete v2 envelopes and both mixed schema/contract-ID
pairs fail. Production qualification runs that regression directly; source-text checks alone are not
wire-transition evidence.

The qualified `sora-android-production-rollout-v3` receipt contains only fixed contract strings,
hashes, bounded epoch seconds, and aggregate counters. Its identity binds one Android candidate
artifact, the exact production-admission receipt/binding, the rollout trust contract, SORA2 runtime
revision/metadata, and one immutable recomputed SHA-256 used by every cohort. The fresh PI receipt,
capability projection, and moving finalized checkpoints are separately signed in `checkpoint` for
each gate, so checkpoint refreshes do not mutate candidate identity.
Completed cohorts must be
exactly `1`, then `5`, then `25`, each with at least 172800 seconds of non-overlapping dwell and no
future-dated cohort window; the receipt evaluation epoch must equal the controller-request epoch.
Every completed cohort also contains a separately authorizer-signed
`sora-android-production-rollout-distribution-v2` attestation for provider `google-play`, track
`production`, application ID `jp.co.soramitsu.sora`, the exact candidate and stable identity, the
cohort percentage, its effective start, the bounded provider release/edit identifier, the exact
independently captured Play-provider receipt hash, and the provider observation epoch. The collector
and enclosing authorization signatures
bind the decoded distribution-signature digest as well, so aggregate observations cannot be
silently reassigned to another Play rollout.
Any confirmed missing wallet/account, address mismatch, signature mismatch, or
cross-network routing event blocks advancement. Eligible terminal failures are calculated after
subtracting user cancellations and insufficient-funds outcomes and must satisfy
`failures * 100 <= eligible outcomes`. Eligible successes plus eligible failures plus those two
excluded classes must equal the terminal total exactly; the signed categories are exhaustive and
cannot hide uncategorized failures. Every completed cohort must also set
`telemetryCompletenessQualified` to `true` and report nonzero `upgradedWalletsObserved`,
`accountsObserved`, and `terminalTransactionsObserved`; accounts observed may not be fewer than
upgraded wallets, and at least one terminal outcome must remain after exclusions.

The `telemetryAttestation` names the pinned source and completeness policy, records a snapshot no
more than five minutes old, and carries the pinned collector key ID and detached canonical Base64
Ed25519 signature. That signature covers the complete receipt identity and privacy policy plus
every cohort window and every metric in a fixed ordered projection. Consequently,
`telemetryCompletenessQualified` is an authenticated collector assertion under a reviewed policy,
not a Boolean that a receipt author can set by itself. `authorization` binds the telemetry
projection digest, decoded collector-signature digest, exact target, evaluation time, identity,
and production-admission receipt, and must verify with the distinct pinned rollout-authorizer key.
Authorization occurs after the telemetry observation and no earlier than the evaluation epoch.
Because that epoch is recorded immediately before the bounded controller request, authorization
may be at most 30 seconds later to tolerate request processing; anything beyond that skew is
rejected. Telemetry itself may precede evaluation by at most the five-minute freshness window.

All detached signatures are canonical Base64 encodings of exactly 64 Ed25519 signature bytes.
They cover UTF-8 `key=value` lines joined by `\n`, with no final newline. The stable identity
binding uses this exact order after the fixed `platform=android` line:

```text
candidateArtifactSha256
sora2NetworkRevision
runtimeSpecVersion
runtimeTransactionVersion
runtimeMetadataSha256
productionQualificationReceiptSha256
productionAdmissionBindingSha256
tairaCanaryReceiptSha256
minamotoCanaryReceiptSha256
productionRolloutTrustSha256
```

The two funded-canary hashes are copied from the fully validated v4 production-admission identity,
covered directly by the rollout identity digest, repeated unchanged in every prior-gate receipt,
and compared against the immutable candidate admission. Binding only the admission receipt hash or
an aggregate “canary qualified” Boolean is insufficient.

The distribution signature projection remains the unchanged domain
`sora-android-production-rollout-distribution-v2`, platform, provider, track, application ID,
provider release ID, provider receipt hash, provider observation epoch, candidate hash, identity
binding, cohort percentage, effective epoch, and authorizer key ID, in
that order. The telemetry projection is the domain
`sora-android-production-rollout-telemetry-v1`, receipt schema/status/platform/from/target/evaluation
and prior-gate hash; all stable identity fields; every moving checkpoint field; all privacy fields;
cohort count; then, for each
cohort in order, its percentage/window/identity, complete distribution projection and decoded
signature digest, every metric in the verifier's fixed order, and completeness Boolean; finally
the source ID, policy digest, observation epoch, and collector key ID. The authorization projection
is the domain `sora-android-production-rollout-authorization-v1`, platform, target, evaluation,
identity binding, production-admission receipt hash, prior-gate receipt hash, telemetry digest,
decoded collector-signature digest, authorization epoch, and authorizer key ID. The verifier
reconstructs every projection; receipt-provided digests are never signature authority.

Never put phrases, mnemonics, seeds, private/public keys, addresses, account or transaction
identifiers, signed payloads, per-wallet rows, free-form notes, or raw errors in a receipt. The
blocked templates document the fail-closed shape only; never convert them to qualified evidence in
the source tree. Qualified evidence comes from the protected external controller and both pinned
signatures. Do not infer missing counters and do not fabricate qualification evidence.

The production GitHub qualification workflow has two fail-closed modes. Candidate mode keeps the
fresh local raw-live v1 observation distinct from authority. It submits that receipt hash and body,
the exact AAB identity, source revision, reviewed runtime identity, and admitted Taira epoch to the
TLS-SPKI-pinned protected controller. The controller must return an exact
`sora-pi-production-capability-probe-v3` receipt plus a detached-signature JSON envelope. The v3
receipt has canonical iOS-parity `health`, atomic `capabilities`, and Minamoto/Taira
`networkCheckpoints`, plus an Android `candidate` block binding the artifact SHA-256/size, source,
raw-live receipt SHA-256, and controller ID. Its Ed25519 signature must verify with the independently
protected rollout-authorizer key and exact protected trust-file pin. Candidate v1/v2 receipts and
mixed schema/contract pairs are wire-incompatible and rejected.

After the full migration, test, signing, PI, funded-canary, and release-admission gates pass once,
candidate mode publishes a 90-day immutable artifact containing the exact AAB, v3 admission,
candidate-time PI v3 receipt, its protected signature envelope, and source revision. The raw-live
file is never aliased to the immutable candidate receipt. Rollout mode
requires that candidate run ID, downloads the exact artifact without rebuilding it, validates its
GitHub run/artifact provenance, and rejects a source-revision mismatch. After a fresh PI probe and
explicit evaluation epoch it creates the privacy-safe
`sora-android-production-rollout-controller-request-v3` containing the qualified admission and
candidate PI bodies, signature envelope, and their hashes. Its `productionAdmission` envelope carries the admission
receipt hash, admission binding, and the exact Taira and Minamoto funded-canary receipt hashes as
four first-class fields; each canary hash must equal the corresponding value inside the bound
admission identity and the two network hashes must differ. The request is sent over HTTPS to the protected controller origin
using the protected controller credential and v3 controller API. The controller returns the
dual-signed current receipt.
For later stages, automation follows that receipt's exact SHA-256 links and fetches the entire prior
chain by digest. The local gate still independently verifies all hashes, signatures, chronology,
checkpoints, and metrics; API authentication alone is never qualification. Missing controller
configuration, evidence, any chain receipt, qualified trust, provenance receipt, or immutable
candidate artifact blocks promotion. Generic Jenkins test
tasks must not claim rollout authorization before their publish artifact exists.

Protected controller v3 deployment and compatibility approval is an external hard blocker: the
workflow now calls only the v3 evidence and exact-receipt endpoints and must fail until those routes
produce the exact reviewed v3 contracts. This transition does not invent the separate post-AAB
funded-canary candidate-evidence handoff; the protected release lifecycle must provide and review
that handoff before candidate admission can qualify.
