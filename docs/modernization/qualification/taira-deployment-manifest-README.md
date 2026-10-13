# Taira deployment admission evidence

The first wallet release has one immutable public Taira contract:

- chain ID `fc56984b-2be7-431d-840e-21514d1883f0`;
- Torii root `https://taira.sora.org`;
- fixed submission MCP endpoint `https://taira.sora.org/v1/mcp`; and
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

New pending rows are namespaced by the exact 32-byte signing genesis hash. They do not
depend on the deployment manifest hash. Rows carrying a null or different chain ID, an older contract
namespace, or another genesis remain durable recovery evidence and cannot be submitted or reinterpreted.

The immutable candidate package retains the manifest, signatures, public keys, and admission
receipt so rollout verification can reproduce the review. This evidence authorizes candidate build
binding only; it does not authorize a funded canary, release, or production mutation, and it cannot
change the app's Taira route or chain identity.

## Runtime availability (2026-10-13)

Taira is the only live Nexus deployment. Minamoto remains an offline production account profile: wallet creation and recovery retain its derivation and address, and the wallet exposes address copy/QR with “Not live yet”. It must not fetch balances/history, open an explorer, or send on that profile before a production deployment is independently admitted. Existing pending records and derivation identities remain unchanged.

Wallet reads use fixed direct Torii GET queries: `/health`, `/v1/assets/definitions/{definition}`, `/v1/accounts/{account}/assets`, `/v1/accounts/{account}/history`, and `/v1/pipeline/transactions/status`. Collection queries use URL-encoded text filters and opaque cursors. Assets request `include_total=true`; history filters the pinned XOR definition and successful transfers, with an additional `tx_hash` predicate for exact transaction recovery. Empty history pages can have a continuation and must not be treated as completion. The wallet loads history on demand, with no lifetime-history cap on recovery. HTTP content types, partial fanout failures, account/asset identities, duplicate items and repeated cursors remain validated. Only the canonical Taira origin admits the deployed local lane-0/dataspace-0 response evidence.

The app performs no runtime MCP discovery or tool listing. Submission retains the fixed `iroha.transactions.submit_and_wait` request with stateless `2026-07-28` protocol metadata and headers; account data and status reads do not use MCP.

Taira sends use the local fixed-V1 Norito transfer writer with Ed25519 keys derived from the wallet phrase inside the app. The current genesis is `hash:5C3E66C94E079079FF4FC46F59810B9C4EC7B2646DD45CE937831C0FDCE20C9B#44D9`; the chain UUID remains the account/journal label, not transaction-domain wire bytes. Authenticated fee quotes carry only a signature and unsigned transfer data. A confirmation binds the quote, recipient, amount, and unsigned payload; local signing is one-shot and derived seed buffers are wiped.

Testnet confirmation intentionally trusts canonical HTTPS Torii: a fresh challenge-bound applied checkpoint and exact block hash, plus exact transaction status/history. It is not an independently verified consensus light client. Production native/finality qualification and the false production rollout gate do not disable Taira. Nexus visibility and the user's Taira preference still apply. Production-launch and funded-canary release evidence below remains future production evidence.

Anonymous live read fixtures under `common/src/test/resources/nexus/taira-2026-10-13` retain the earlier MCP envelopes as response fixtures for asset identity, balances and movement paging. `NexusDirectToriiReadTest` and `TairaToriiQueriesTest` verify the direct read paths and request counts. `NexusLiveTairaReadTest` is opt-in with `SORA_LIVE_TAIRA_READS=1` and performs no writes.
