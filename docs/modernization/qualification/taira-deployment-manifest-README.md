# Taira deployment epoch admission

Production source does not activate either known Taira UUID as current without authority evidence.
Before the first Gradle task, the protected production environment supplies one canonical
`sora-taira-deployment-epoch-manifest-v1` plus detached Ed25519 signatures from distinct operator
and independent-reviewer authorities. `scripts/verify-taira-deployment-manifest.mjs` admits it only
when all of the following remain true:

- the manifest maps exactly `809574f5-fee7-5e69-bfcf-52451e42d50f` and
  `fc56984b-2be7-431d-840e-21514d1883f0`, with unique nonzero genesis hashes and monotonically
  ordered epochs;
- the signed ordering—not repository source—selects either known UUID as current and the other as
  retired. Database schema 77 retains each pending journal's exact UUID, and every new Taira
  journal namespaces its existing `localId` with the admitted manifest SHA-256. Current-chain
  queries require both values. Thus a retained null, non-current UUID, unbound local ID, or
  different-manifest local ID remains quarantined as recovery evidence—even if a later signed
  mapping selects the same UUID—rather than being reinterpreted;
- exactly one epoch is current and the other is retired; a retired epoch carries no route;
- the current route is an explicit canonical HTTPS public-node origin whose `/v1/mcp` endpoint is
  not the `taira.sora.org` convenience endpoint;
- both signatures verify over the exact canonical manifest bytes against distinct protected SPKI
  SHA-256 pins; and
- the independent review is current at the explicit evaluation epoch.

The admitted projection is exported as public BuildConfig data. If any field is absent or drifts,
`TairaDeployment.binding` is null and `NexusToriiRoutes` throws
`TAIRA_DEPLOYMENT_MANIFEST_NOT_QUALIFIED` before constructing any Taira URL. Both known identities
remain in the binary only to preserve derivation and pending-journal recovery evidence; neither is
routing authority by itself.

The immutable candidate package retains the manifest, both signatures, both public keys, and the
build-binding admission receipt. Its production admission identity includes the exact manifest
SHA-256. Every downloaded rollout candidate re-verifies the detached signatures using the
independently protected pins. The manifest authorizes build binding only: it does not authorize a
funded canary, release, or production mutation.
