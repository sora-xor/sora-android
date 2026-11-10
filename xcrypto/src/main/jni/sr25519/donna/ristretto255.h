/*
   This is based on isislovecruft's implementation (https://github.com/isislovecruft/ristretto-donna), fix some issues and make it working.
*/

#ifndef __ristretto255_H__
#define __ristretto255_H__

#include "ed25519-donna/ed25519-donna.h"

typedef uint8_t ristretto255_hash_output[64];

uint8_t uint8_32_ct_eq(const unsigned char a[32], const unsigned char b[32]);

int ristretto_decode(ge25519 *element, const unsigned char bytes[32]);
void ristretto_encode(unsigned char bytes[32], const ge25519 element);
int ristretto_ct_eq(const ge25519 *a, const ge25519 *b);
void ge25519_scalarmult_tg(ge25519 *r, const ge25519 *p1, const bignum256modm s1);

#endif
