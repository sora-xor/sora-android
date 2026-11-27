/*
 * sr25519 C API over sr25519-donna.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Derived from sr25519-donna's src/sr25519.c (Copyright Terence Ge,
 * Apache-2.0, https://github.com/TerenceGe/sr25519-donna). Changed for SORA:
 *  - byte formats of the sr25519-crust/schnorrkel API the app shipped with:
 *    secret scalars are stored canonically (SecretKey::to_bytes), not
 *    multiplied by the cofactor, in keypairs and secrets;
 *  - soft derivation of a keypair commits the keypair's stored public key,
 *    as schnorrkel's Keypair::derived_key and sr25519-crust do;
 *  - the signing witness takes 64 bytes from the transcript RNG, as
 *    schnorrkel's witness_scalar;
 *  - invalid key material ends the process (the Rust library panicked);
 *    sr25519_verify returns false for an invalid public key or signature;
 *  - secrets and nonces are wiped after use, and so is the stack that the
 *    computations on them used; no VRF.
 * The algorithms follow schnorrkel 0.9 (w3f/schnorrkel): MiniSecretKey
 * expand_ed25519, SecretKey/PublicKey derived_key_simple,
 * hard_derive_mini_secret_key, sign_simple and verify_simple.
 */

#include <string.h>

#include "sr25519lib/sr25519.h"
#include "donna/sr25519-randombytes.h"
#include "donna/sr25519-hash.h"
#include "donna/ristretto255.h"
#include "donna/merlin.h"
#include "donna/memzero.h"

/*
 * ed25519-donna's x86-64 inline-asm table select (ed25519-donna-64bit-x86.h,
 * -64bit-x86-32bit.h) writes xmm15 without naming it in its clobber list, so
 * a compiler may keep a live value there across the asm. Both app builds
 * define ED25519_NO_INLINE_ASM, which compiles the portable constant-time C
 * select instead; a build that would compile the asm one stops here.
 */
#if defined(HAVE_GE25519_SCALARMULT_BASE_CHOOSE_NIELS)
#error "build sr25519 with -DED25519_NO_INLINE_ASM"
#endif

static const uint8_t SIGNING_CONTEXT[] = "substrate";

/*
 * Bytes of stack that wipe_stack() zeroes. The deepest call, sr25519_sign,
 * uses under 10 KiB below its caller at -O0 (iOS Debug) and under 3.6 KiB at
 * -O2 and -Os, in 64- and 32-bit arithmetic, on arm64 and x86-64.
 */
#define SR25519_STACK_WIPE_BYTES 16384

/*
 * Zeroes the stack below the calling function's frame. Each public function
 * that handles a secret does its work in a DONNA_NOINLINE helper and calls
 * this after the helper returns, so this frame covers the helper's frame and
 * the frames of everything it called: the SHA-512 message schedule, Keccak
 * scratch, the signed window digits of a scalar, scalar products and spilled
 * registers. memzero stores through a volatile pointer, so the stores stay.
 * AddressSanitizer builds with use-after-return detection put frames on a
 * separate fake stack that this does not reach; no shipped build uses it.
 */
static DONNA_NOINLINE void wipe_stack(void) {
    uint8_t scratch[SR25519_STACK_WIPE_BYTES];
    memzero(scratch, sizeof scratch);
}

static void sr25519_fail(void) {
    /* Not valid key material: the Rust library panicked here. */
    abort();
}

static void divide_scalar_bytes_by_cofactor(uint8_t scalar[32]) {
    uint8_t low = 0;
    for (int i = 31; i >= 0; i--) {
        uint8_t r = scalar[i] & 7;
        scalar[i] >>= 3;
        scalar[i] += low;
        low = (uint8_t)(r << 5);
    }
}

static void multiply_scalar_bytes_by_cofactor(uint8_t scalar[32]) {
    uint8_t high = 0;
    for (int i = 0; i < 32; i++) {
        uint8_t r = scalar[i] & (7 << 5);
        scalar[i] <<= 3;
        scalar[i] += high;
        high = r >> 5;
    }
}

/* Scalar::from_canonical_bytes: true when the 32 bytes encode a value < l. */
static int load_canonical_scalar(bignum256modm out, const uint8_t bytes[32]) {
    if (bytes[31] & 0x80) {
        return 0;
    }
    expand_raw256_modm(out, bytes);
    return is_reduced256_modm(out);
}

/* Secret key: canonical scalar + nonce, else the process ends. */
static void parse_secret(bignum256modm key, uint8_t key_bytes[32], uint8_t nonce[32], const uint8_t *secret) {
    memcpy(key_bytes, secret, 32);
    memcpy(nonce, secret + 32, 32);
    if (!load_canonical_scalar(key, key_bytes)) {
        memzero(key_bytes, 32);
        memzero(nonce, 32);
        sr25519_fail();
    }
}

/* Public key: a Ristretto encoding, else the process ends. */
static void parse_public(ge25519 *point, const uint8_t *public_key) {
    if (!ristretto_decode(point, public_key)) {
        sr25519_fail();
    }
}

static void public_from_scalar(uint8_t public_out[32], const bignum256modm key) {
    ge25519 P;
    ge25519_scalarmult_base_niels(&P, ge25519_niels_base_multiples, key);
    ristretto_encode(public_out, P);
}

/* MiniSecretKey::expand_ed25519 */
static void expand_ed25519(uint8_t key[32], uint8_t nonce[32], const uint8_t mini_secret_key[32]) {
    uint8_t hash[64];
    sr25519_hash(hash, mini_secret_key, 32);
    memcpy(key, hash, 32);
    key[0] &= 248;
    key[31] &= 63;
    key[31] |= 64;
    divide_scalar_bytes_by_cofactor(key);
    memcpy(nonce, hash + 32, 32);
    memzero(hash, sizeof hash);
}

static DONNA_NOINLINE void keypair_from_mini_secret(uint8_t *keypair_out, const uint8_t mini_secret_key[32]) {
    uint8_t key[32], nonce[32], public_key[32];
    bignum256modm scalar;
    expand_ed25519(key, nonce, mini_secret_key);
    expand_raw256_modm(scalar, key);
    public_from_scalar(public_key, scalar);
    memcpy(keypair_out, key, 32);
    memcpy(keypair_out + 32, nonce, 32);
    memcpy(keypair_out + 64, public_key, 32);
    memzero(key, sizeof key);
    memzero(nonce, sizeof nonce);
    memzero(scalar, sizeof(bignum256modm));
}

/* Transcript challenge of 64 bytes reduced mod l (challenge_scalar). */
static void challenge_scalar(merlin_transcript *t, const char *label, bignum256modm out) {
    uint8_t buf[64];
    merlin_transcript_challenge_bytes(t, (const uint8_t *)label, strlen(label), buf, 64);
    expand256_modm(out, buf, 64);
    memzero(buf, sizeof buf);
}

/* PublicKey::derive_scalar_and_chaincode */
static void derive_scalar_and_chaincode(merlin_transcript *t, bignum256modm scalar, uint8_t chain_code_out[32],
                                        const uint8_t public_key[32], const uint8_t chain_code_in[32]) {
    merlin_transcript_commit_bytes(t, (const uint8_t *)"chain-code", 10, chain_code_in, 32);
    merlin_transcript_commit_bytes(t, (const uint8_t *)"public-key", 10, public_key, 32);
    challenge_scalar(t, "HDKD-scalar", scalar);
    merlin_transcript_challenge_bytes(t, (const uint8_t *)"HDKD-chaincode", 14, chain_code_out, 32);
}

static void hdkd_transcript(merlin_transcript *t) {
    merlin_transcript_init(t, (const uint8_t *)"SchnorrRistrettoHDKD", 20);
    merlin_transcript_commit_bytes(t, (const uint8_t *)"sign-bytes", 10, (const uint8_t *)"", 0);
}

void sr25519_keypair_from_seed(uint8_t *keypair_out, const uint8_t *seed_ptr) {
    keypair_from_mini_secret(keypair_out, seed_ptr);
    wipe_stack();
}

static DONNA_NOINLINE void derive_keypair_hard(uint8_t *keypair_out, const uint8_t *pair_ptr, const uint8_t *cc_ptr) {
    bignum256modm key;
    uint8_t key_bytes[32], nonce[32], mini[32], chain_code_out[32];
    ge25519 public_point;
    parse_secret(key, key_bytes, nonce, pair_ptr);
    parse_public(&public_point, pair_ptr + 64);

    /* SecretKey::hard_derive_mini_secret_key(Some(cc), b"") */
    merlin_transcript t;
    hdkd_transcript(&t);
    merlin_transcript_commit_bytes(&t, (const uint8_t *)"chain-code", 10, cc_ptr, 32);
    merlin_transcript_commit_bytes(&t, (const uint8_t *)"secret-key", 10, key_bytes, 32);
    merlin_transcript_challenge_bytes(&t, (const uint8_t *)"HDKD-hard", 9, mini, 32);
    merlin_transcript_challenge_bytes(&t, (const uint8_t *)"HDKD-chaincode", 14, chain_code_out, 32);

    keypair_from_mini_secret(keypair_out, mini);

    memzero(&t, sizeof t);
    memzero(key, sizeof(bignum256modm));
    memzero(key_bytes, sizeof key_bytes);
    memzero(nonce, sizeof nonce);
    memzero(mini, sizeof mini);
    memzero(chain_code_out, sizeof chain_code_out);
}

void sr25519_derive_keypair_hard(uint8_t *keypair_out, const uint8_t *pair_ptr, const uint8_t *cc_ptr) {
    derive_keypair_hard(keypair_out, pair_ptr, cc_ptr);
    wipe_stack();
}

static DONNA_NOINLINE void derive_keypair_soft(uint8_t *keypair_out, const uint8_t *pair_ptr, const uint8_t *cc_ptr) {
    bignum256modm key, scalar, derived;
    uint8_t key_bytes[32], nonce[32], chain_code_out[32];
    uint8_t derived_bytes[32], derived_nonce[32], derived_public[32];
    ge25519 public_point;
    parse_secret(key, key_bytes, nonce, pair_ptr);
    parse_public(&public_point, pair_ptr + 64);

    /* Keypair::derived_key: the transcript commits the keypair's stored public
     * key, as sr25519-crust and the prebuilt library did. For every keypair
     * the apps build it is secret·B. The derived public key comes from the
     * derived secret. */
    merlin_transcript t;
    hdkd_transcript(&t);
    derive_scalar_and_chaincode(&t, scalar, chain_code_out, pair_ptr + 64, cc_ptr);

    /* nonce: t.witness_bytes(b"HDKD-nonce", [nonce, secret.to_bytes()]) */
    uint8_t secret_bytes[64];
    memcpy(secret_bytes, key_bytes, 32);
    memcpy(secret_bytes + 32, nonce, 32);
    merlin_rng rng;
    merlin_rng_init(&rng, &t);
    merlin_rng_commit_witness_bytes(&rng, (const uint8_t *)"HDKD-nonce", 10, nonce, 32);
    merlin_rng_commit_witness_bytes(&rng, (const uint8_t *)"HDKD-nonce", 10, secret_bytes, 64);
    uint8_t entropy[32];
    sr25519_randombytes(entropy, 32);
    merlin_rng_finalize(&rng, entropy);
    merlin_rng_random_bytes(&rng, derived_nonce, 32);

    add256_modm(derived, key, scalar);
    contract256_modm(derived_bytes, derived);
    public_from_scalar(derived_public, derived);

    memcpy(keypair_out, derived_bytes, 32);
    memcpy(keypair_out + 32, derived_nonce, 32);
    memcpy(keypair_out + 64, derived_public, 32);

    merlin_rng_wipe(&rng);
    memzero(&t, sizeof t);
    memzero(entropy, sizeof entropy);
    memzero(secret_bytes, sizeof secret_bytes);
    memzero(key, sizeof(bignum256modm));
    memzero(derived, sizeof(bignum256modm));
    memzero(key_bytes, sizeof key_bytes);
    memzero(nonce, sizeof nonce);
    memzero(derived_bytes, sizeof derived_bytes);
    memzero(derived_nonce, sizeof derived_nonce);
}

void sr25519_derive_keypair_soft(uint8_t *keypair_out, const uint8_t *pair_ptr, const uint8_t *cc_ptr) {
    derive_keypair_soft(keypair_out, pair_ptr, cc_ptr);
    wipe_stack();
}

void sr25519_derive_public_soft(uint8_t *pubkey_out, const uint8_t *public_ptr, const uint8_t *cc_ptr) {
    ge25519 public_point, offset, derived;
    bignum256modm scalar;
    uint8_t chain_code_out[32];
    parse_public(&public_point, public_ptr);

    merlin_transcript t;
    hdkd_transcript(&t);
    derive_scalar_and_chaincode(&t, scalar, chain_code_out, public_ptr, cc_ptr);

    ge25519_scalarmult_base_niels(&offset, ge25519_niels_base_multiples, scalar);
    ge25519_add(&derived, &public_point, &offset);
    ristretto_encode(pubkey_out, derived);
}

static DONNA_NOINLINE void sign_message(uint8_t *signature_out,
                                        const uint8_t *public_ptr,
                                        const uint8_t *secret_ptr,
                                        const uint8_t *message_ptr,
                                        unsigned long message_length) {
    bignum256modm key, r, k, s, kx;
    uint8_t key_bytes[32], nonce[32];
    ge25519 public_point;
    parse_secret(key, key_bytes, nonce, secret_ptr);
    parse_public(&public_point, public_ptr);

    /* SigningContext::new(b"substrate").bytes(message) */
    merlin_transcript t;
    merlin_transcript_init(&t, (const uint8_t *)"SigningContext", 14);
    merlin_transcript_commit_bytes(&t, (const uint8_t *)"", 0, SIGNING_CONTEXT, sizeof SIGNING_CONTEXT - 1);
    merlin_transcript_commit_bytes(&t, (const uint8_t *)"sign-bytes", 10, message_ptr, message_length);

    merlin_transcript_commit_bytes(&t, (const uint8_t *)"proto-name", 10, (const uint8_t *)"Schnorr-sig", 11);
    merlin_transcript_commit_bytes(&t, (const uint8_t *)"sign:pk", 7, public_ptr, 32);

    /* r = t.witness_scalar(b"signing", [nonce]) */
    merlin_rng rng;
    merlin_rng_init(&rng, &t);
    merlin_rng_commit_witness_bytes(&rng, (const uint8_t *)"signing", 7, nonce, 32);
    uint8_t entropy[32];
    sr25519_randombytes(entropy, 32);
    merlin_rng_finalize(&rng, entropy);
    uint8_t witness[64];
    merlin_rng_random_bytes(&rng, witness, 64);
    expand256_modm(r, witness, 64);

    ge25519 R;
    uint8_t R_bytes[32];
    ge25519_scalarmult_base_niels(&R, ge25519_niels_base_multiples, r);
    ristretto_encode(R_bytes, R);
    merlin_transcript_commit_bytes(&t, (const uint8_t *)"sign:R", 6, R_bytes, 32);

    challenge_scalar(&t, "sign:c", k);
    mul256_modm(kx, k, key);
    add256_modm(s, kx, r);

    memcpy(signature_out, R_bytes, 32);
    contract256_modm(signature_out + 32, s);
    signature_out[63] |= 128;

    merlin_rng_wipe(&rng);
    memzero(&t, sizeof t);
    memzero(entropy, sizeof entropy);
    memzero(witness, sizeof witness);
    memzero(key, sizeof(bignum256modm));
    memzero(r, sizeof(bignum256modm));
    memzero(kx, sizeof(bignum256modm));
    memzero(s, sizeof(bignum256modm));
    memzero(key_bytes, sizeof key_bytes);
    memzero(nonce, sizeof nonce);
}

void sr25519_sign(uint8_t *signature_out,
                  const uint8_t *public_ptr,
                  const uint8_t *secret_ptr,
                  const uint8_t *message_ptr,
                  unsigned long message_length) {
    sign_message(signature_out, public_ptr, secret_ptr, message_ptr, message_length);
    wipe_stack();
}

bool sr25519_verify(const uint8_t *signature_ptr,
                    const uint8_t *message_ptr,
                    unsigned long message_length,
                    const uint8_t *public_ptr) {
    /* Signature::from_bytes: the schnorrkel marker bit, then a canonical s. */
    uint8_t s_bytes[32];
    memcpy(s_bytes, signature_ptr + 32, 32);
    if ((s_bytes[31] & 128) == 0) {
        return false;
    }
    s_bytes[31] &= 127;
    bignum256modm s;
    if (!load_canonical_scalar(s, s_bytes)) {
        return false;
    }

    ge25519 A;
    if (!ristretto_decode(&A, public_ptr)) {
        return false;
    }

    merlin_transcript t;
    merlin_transcript_init(&t, (const uint8_t *)"SigningContext", 14);
    merlin_transcript_commit_bytes(&t, (const uint8_t *)"", 0, SIGNING_CONTEXT, sizeof SIGNING_CONTEXT - 1);
    merlin_transcript_commit_bytes(&t, (const uint8_t *)"sign-bytes", 10, message_ptr, message_length);
    merlin_transcript_commit_bytes(&t, (const uint8_t *)"proto-name", 10, (const uint8_t *)"Schnorr-sig", 11);
    merlin_transcript_commit_bytes(&t, (const uint8_t *)"sign:pk", 7, public_ptr, 32);
    merlin_transcript_commit_bytes(&t, (const uint8_t *)"sign:R", 6, signature_ptr, 32);

    bignum256modm k;
    challenge_scalar(&t, "sign:c", k);

    /* R' = s*B - k*A, compared in compressed form */
    curve25519_neg(A.x, A.x);
    curve25519_neg(A.t, A.t);
    ge25519 R;
    ge25519_double_scalarmult_vartime(&R, &A, k, s);
    uint8_t R_bytes[32];
    ristretto_encode(R_bytes, R);
    return uint8_32_ct_eq(R_bytes, signature_ptr) == 1;
}

bool sr25519_public_key_is_valid(const uint8_t *public_ptr) {
    ge25519 point;
    return ristretto_decode(&point, public_ptr) == 1;
}

static DONNA_NOINLINE bool secret_is_valid(const uint8_t *secret_ptr) {
    bignum256modm key;
    int valid = load_canonical_scalar(key, secret_ptr);
    memzero(key, sizeof(bignum256modm));
    return valid != 0;
}

bool sr25519_secret_is_valid(const uint8_t *secret_ptr) {
    bool valid = secret_is_valid(secret_ptr);
    wipe_stack();
    return valid;
}

bool sr25519_signature_is_well_formed(const uint8_t *signature_ptr) {
    uint8_t s_bytes[32];
    bignum256modm s;
    memcpy(s_bytes, signature_ptr + 32, 32);
    if ((s_bytes[31] & 128) == 0) {
        return false;
    }
    s_bytes[31] &= 127;
    return load_canonical_scalar(s, s_bytes) != 0;
}

static DONNA_NOINLINE void to_ed25519_bytes(uint8_t *secret_out, const uint8_t *secret_ptr) {
    bignum256modm key;
    uint8_t key_bytes[32], nonce[32];
    parse_secret(key, key_bytes, nonce, secret_ptr);
    multiply_scalar_bytes_by_cofactor(key_bytes);
    memcpy(secret_out, key_bytes, 32);
    memcpy(secret_out + 32, nonce, 32);
    memzero(key, sizeof(bignum256modm));
    memzero(key_bytes, sizeof key_bytes);
    memzero(nonce, sizeof nonce);
}

void sr25519_to_ed25519_bytes(uint8_t *secret_out, const uint8_t *secret_ptr) {
    to_ed25519_bytes(secret_out, secret_ptr);
    wipe_stack();
}

static DONNA_NOINLINE void from_ed25519_bytes(uint8_t *secret_out, const uint8_t *secret_ptr) {
    /* SecretKey::from_ed25519_bytes(..).to_bytes() in schnorrkel 0.9: divide by
     * the cofactor, with no range check. schnorrkel 0.11 rejects a result that
     * is not canonical; callers check it with sr25519_secret_is_valid before
     * they keep it, since signing with it would end the process. */
    uint8_t key_bytes[32];
    memcpy(key_bytes, secret_ptr, 32);
    divide_scalar_bytes_by_cofactor(key_bytes);
    memcpy(secret_out, key_bytes, 32);
    memmove(secret_out + 32, secret_ptr + 32, 32);
    memzero(key_bytes, sizeof key_bytes);
}

void sr25519_from_ed25519_bytes(uint8_t *secret_out, const uint8_t *secret_ptr) {
    from_ed25519_bytes(secret_out, secret_ptr);
    wipe_stack();
}
