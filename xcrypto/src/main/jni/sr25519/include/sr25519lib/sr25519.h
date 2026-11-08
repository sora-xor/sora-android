#ifndef __SR25519_INCLUDE_GUARD_H__
#define __SR25519_INCLUDE_GUARD_H__

/*
 * sr25519 (schnorrkel, Substrate signing context "substrate") in C.
 *
 * Same functions and byte formats as the sr25519-crust C API the app shipped
 * with: a secret is [32-byte scalar | 32-byte nonce] with the scalar stored
 * canonically (schnorrkel SecretKey::to_bytes), and a keypair is
 * [secret | 32-byte public key]. Built from sr25519-donna sources in this
 * package; see README.md.
 *
 * Inputs that are not valid key material (a non-canonical scalar, a public
 * key that is not a Ristretto point) end the process, as the Rust library
 * did, except in sr25519_verify, which returns false.
 */

#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

#ifdef __cplusplus
extern "C" {
#endif

/** Size of CHAINCODE, bytes */
#define SR25519_CHAINCODE_SIZE 32
/** Size of SR25519 KEYPAIR. [32 bytes key | 32 bytes nonce | 32 bytes public] */
#define SR25519_KEYPAIR_SIZE 96
/** Size of SR25519 PUBLIC KEY, bytes */
#define SR25519_PUBLIC_SIZE 32
/** Size of SR25519 PRIVATE (SECRET) KEY, which consists of [32 bytes key | 32 bytes nonce] */
#define SR25519_SECRET_SIZE 64
/** Size of input SEED (mini secret key) for derivation, bytes */
#define SR25519_SEED_SIZE 32
/** Size of SR25519 SIGNATURE, bytes */
#define SR25519_SIGNATURE_SIZE 64

/** Hard derivation (//junction): keypair_out from pair_ptr and chain code cc_ptr. */
void sr25519_derive_keypair_hard(uint8_t *keypair_out, const uint8_t *pair_ptr, const uint8_t *cc_ptr);

/** Soft derivation (/junction). The output nonce is random, as in schnorrkel. */
void sr25519_derive_keypair_soft(uint8_t *keypair_out, const uint8_t *pair_ptr, const uint8_t *cc_ptr);

/** Soft derivation of a public key alone. */
void sr25519_derive_public_soft(uint8_t *pubkey_out, const uint8_t *public_ptr, const uint8_t *cc_ptr);

/** Secret from its Ed25519 representation (scalar divided by the cofactor). */
void sr25519_from_ed25519_bytes(uint8_t *secret_out, const uint8_t *secret_ptr);

/** Keypair from a 32-byte mini secret key (schnorrkel ExpansionMode::Ed25519). */
void sr25519_keypair_from_seed(uint8_t *keypair_out, const uint8_t *seed_ptr);

/** Signs message with the "substrate" signing context. Signatures are randomized. */
void sr25519_sign(uint8_t *signature_out,
                  const uint8_t *public_ptr,
                  const uint8_t *secret_ptr,
                  const uint8_t *message_ptr,
                  unsigned long message_length);

/** Secret in Ed25519 representation (scalar multiplied by the cofactor). */
void sr25519_to_ed25519_bytes(uint8_t *secret_out, const uint8_t *secret_ptr);

/** true if public_ptr is a valid Ristretto255 encoding (a usable public key). */
bool sr25519_public_key_is_valid(const uint8_t *public_ptr);

/** true if secret_ptr holds a canonical scalar (a usable secret key). */
bool sr25519_secret_is_valid(const uint8_t *secret_ptr);

/** true if signature_ptr carries the schnorrkel marker and a canonical s. */
bool sr25519_signature_is_well_formed(const uint8_t *signature_ptr);

/** true if signature_ptr is a valid signature of message_ptr by public_ptr. */
bool sr25519_verify(const uint8_t *signature_ptr,
                    const uint8_t *message_ptr,
                    unsigned long message_length,
                    const uint8_t *public_ptr);

#ifdef __cplusplus
}
#endif

#endif /* __SR25519_INCLUDE_GUARD_H__ */
