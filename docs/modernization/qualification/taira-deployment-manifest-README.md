# Taira deployment admission evidence

The first wallet release has one immutable public Taira contract:

- chain ID `fc56984b-2be7-431d-840e-21514d1883f0`;
- Torii root `https://taira.sora.org`;
- curated MCP endpoint `https://taira.sora.org/v1/mcp`; and
- I105 discriminant `369`.

Application routing does not come from build variables or a signed manifest. Android source admits
only the constants above and rejects copied `NexusNetwork` values that change them. The legacy
`809574f5-fee7-5e69-bfcf-52451e42d50f` identifier is never a routable configuration.

The protected production environment still supplies a canonical
`sora-taira-deployment-epoch-manifest-v1`, signed independently by the operator and reviewer. It is
release evidence, not runtime authority. `scripts/verify-taira-deployment-manifest.mjs` admits it
only when:

- its sole current record exactly matches the public contract above;
- the legacy identifier appears only as a retired, route-less historical record used to quarantine
  pre-release database evidence;
- both detached ECDSA P-256/SHA-256 signatures verify over the canonical manifest bytes against
  distinct protected SPKI SHA-256 pins;
- the schema-77 pending-row policy preserves exact chain IDs and forbids reinterpretation; and
- the independent review is current at the explicit evaluation epoch.

New pending rows are namespaced by the SHA-256 of the immutable first-release contract. They do not
depend on the deployment manifest hash. Rows carrying a null or different chain ID or namespace
remain recovery evidence and cannot be submitted.

The immutable candidate package retains the manifest, signatures, public keys, and admission
receipt so rollout verification can reproduce the review. This evidence authorizes candidate build
binding only; it does not authorize a funded canary, release, or production mutation, and it cannot
change the app's Taira route or chain identity.
