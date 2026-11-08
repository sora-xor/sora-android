#ifndef __SR25519_RANDOM_H__
#define __SR25519_RANDOM_H__

/* Replaces sr25519-donna's randombytes selection: always the platform CSPRNG
 * (arc4random_buf on Apple platforms and Android). */
#include <stdlib.h>

static inline void sr25519_randombytes(void *p, size_t len) {
    arc4random_buf(p, len);
}

#endif
