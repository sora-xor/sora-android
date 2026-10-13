# Preserved source-build inputs

These files are immutable inputs that were not present in the pinned upstream Git trees but were
required to materialize the checked-in Maven modules.

- `xcrypto-1.2.7-Cargo.lock` is the exact generated Rust lock state used for the xcrypto 1.2.7
  Android build. It pins `zeroize_derive` to the compatible 1.4.2 release. Its SHA-256 is bound by
  `SOURCE_PROVENANCE.json` and `CONTENTS.sha256`.
- `xsubstrate-1.2.7-source-normalization.patch` is the complete semantic source delta applied to
  the pinned xsubstrate commit: consume the materialized xcrypto 1.2.7 module and use polkaj's
  authoritative `io.emeraldpay.polkaj` coordinate. Temporary repository-path edits used only to
  resolve the isolated staging repository are intentionally excluded because they are not source
  or dependency-semantic build inputs.

These inputs and the current artifact hashes establish a stable, inspectable inventory. They do
not constitute an independent source-to-binary reproduction, artifact review, SBOM, license
review, or production approval.
