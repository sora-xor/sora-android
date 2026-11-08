# sr25519lib

sr25519 (schnorrkel) for SORA keys, built from source. Replaces the prebuilt
`sr25519lib.xcframework`, which was a Rust build of sr25519-crust. It keeps
the same C functions and byte formats: a secret is `[scalar | nonce]` with
the scalar stored canonically, and a keypair is `[secret | public]`.

- `donna/`: from https://github.com/TerenceGe/sr25519-donna, commit
  `e1e8c7df5dd822b402a4ebfc8746303193b9c665`. That is a C port of schnorrkel
  on ed25519-donna, funded by a Web3 Foundation grant. License: Apache-2.0
  (`donna/LICENSE`). It includes:
  - merlin (libmerlin, hdevalence)
  - ristretto-donna
  - ed25519-donna (Andrew Moon, public domain)
  - sha2 and memzero (trezor-crypto)
- Changes to `donna/`:
  - `sr25519-randombytes.h` uses `arc4random_buf`.
  - The Elligator hash-to-group code is removed. Only the VRF used it, and it
    had a type error.
  - The VRF, the default RNG and donna's own `sr25519.c` are left out.
- `sr25519.c`: the C API, derived from donna's `sr25519.c` and following
  schnorrkel 0.9:
  - It reads and writes the canonical scalar format, where donna uses the
    ed25519 (×8) form.
  - Soft derivation computes the public key from the secret.
  - The signing witness takes 64 bytes from the transcript RNG.
  - Secrets are wiped after use.
  - Key material that is not valid ends the process, as the Rust library's
    panic did. `sr25519_verify` returns false instead.

## Compatibility

`../../Tests/CryptoCompatibility` holds the evidence:

- Outputs are byte-identical to the old prebuilt library for 3,000 random
  seeds and chain codes, in 64-bit and 32-bit arithmetic. That covers
  keypairs, hard and soft derivation, public soft derivation and the
  ed25519 conversions.
- Each library verifies the other's signatures.
- RFC 9496 Ristretto vectors pass, valid and invalid.
- Substrate's `//Alice` and `//Bob` keys match.
