#ifndef __SR25519_HASH_H__
#define __SR25519_HASH_H__

#if defined(SR25519_CUSTOMHASH)

#include "sr25519-hash-custom.h"

#else

#include "sha2.h"

typedef SHA512_CTX sr25519_hash_context;

static void
sr25519_hash_init(sr25519_hash_context *ctx) {
    sha512_Init(ctx);
}

static void
sr25519_hash_update(sr25519_hash_context *ctx, const uint8_t *in, size_t inlen) {
    sha512_Update(ctx, in, inlen);
}

static void
sr25519_hash_final(sr25519_hash_context *ctx, uint8_t *hash) {
    sha512_Final(ctx, hash);
}

static void
sr25519_hash(uint8_t *hash, const uint8_t *in, size_t inlen) {
    sha512_Raw(in, inlen, hash);
}

#endif

#endif
