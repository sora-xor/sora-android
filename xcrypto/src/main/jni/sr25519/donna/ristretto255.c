#include "ristretto255.h"
#include "memzero.h"

#if defined(ED25519_64BIT)
const bignum25519 one = {0x01, 0x00, 0x00, 0x00, 0x00};
const bignum25519 zero = {0, 0, 0, 0, 0};
const bignum25519 MINUS_ONE = {
    2251799813685228,
    2251799813685247,
    2251799813685247,
    2251799813685247,
    2251799813685247
};
const bignum25519 SQRT_M1 = {
    1718705420411056,
    234908883556509,
    2233514472574048,
    2117202627021982,
    765476049583133
};
const bignum25519 EDWARDS_D = {
    929955233495203,
    466365720129213,
    1662059464998953,
    2033849074728123,
    1442794654840575
};
const bignum25519 INVSQRT_A_MINUS_D = {
  278908739862762,
  821645201101625,
  8113234426968,
  1777959178193151,
  2118520810568447
};
const bignum25519 EDWARDS_D_MINUS_ONE_SQUARED = {
    1507062230895904,
    1572317787530805,
    683053064812840,
    317374165784489,
    1572899562415810
};
const bignum25519 ONE_MINUS_EDWARDS_D_SQUARED = {
    1136626929484150,
    1998550399581263,
    496427632559748,
    118527312129759,
    45110755273534
};
const bignum25519 SQRT_AD_MINUS_ONE = {
    2241493124984347,
    425987919032274,
    2207028919301688,
    1220490630685848,
    974799131293748
};
#else
const bignum25519 one = {0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
const bignum25519 zero = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
const bignum25519 MINUS_ONE = {
    67108844, 33554431, 67108863, 33554431, 67108863, 33554431, 67108863, 33554431, 67108863, 33554431
};
const bignum25519 SQRT_M1 = {
    34513072, 25610706,  9377949, 3500415, 12389472,
    33281959, 41962654, 31548777,  326685, 11406482
};
const bignum25519 EDWARDS_D = {
    56195235, 13857412, 51736253,  6949390,   114729,
    24766616, 60832955, 30306712, 48412415, 21499315
};
const bignum25519 INVSQRT_A_MINUS_D = {
  6111466,  4156064, 39310137, 12243467, 41204824,
  120896, 20826367, 26493656,  6093567, 31568420
};
const bignum25519 EDWARDS_D_MINUS_ONE_SQUARED = {
  15551776, 22456977, 53683765, 23429360, 55212328, 10178283, 40474537, 4729243, 61826754, 23438029
};
const bignum25519 ONE_MINUS_EDWARDS_D_SQUARED = {
  6275446, 16937061, 44170319, 29780721, 11667076, 7397348, 39186143, 1766194, 42675006, 672202
};
const bignum25519 SQRT_AD_MINUS_ONE = {
  24849947, 33400850, 43495378, 6347714, 46036536, 32887293, 41837720, 18186727, 66238516, 14525638
};
#endif

static uint8_t uchar_ct_eq(const uint8_t a, const uint8_t b);
static uint8_t bignum25519_is_negative(unsigned char bytes[32]);

/**
 * Check if two bytes are equal in constant time.
 *
 * Returns 1 iff the bytes are equals and 0 otherwise.
 */
static uint8_t uchar_ct_eq(const unsigned char a, const unsigned char b) {
    unsigned char x = ~(a ^ b);

    x &= x >> 4;
    x &= x >> 2;
    x &= x >> 1;

    return (uint8_t)x;
}

/**
 * Check if two 32 bytes arrays are equal in constant time.
 *
 * Returns 1 iff the bytes are equals and 0 otherwise.
 */
uint8_t uint8_32_ct_eq(const unsigned char a[32], const unsigned char b[32]) {
    unsigned char x = 1;
    unsigned char i;

    for (i=0; i<32; i++) {
        x &= uchar_ct_eq(a[i], b[i]);
    }

    return (uint8_t)x;
}

/**
 * Check if two field elements are equal in constant time.
 *
 * Returns 1 iff the elements are equals and 0 otherwise.
 */
uint8_t bignum25519_ct_eq(const bignum25519 a, const bignum25519 b) {
    unsigned char c[32] = {0};
    unsigned char d[32] = {0};

    curve25519_contract(c, a);
    curve25519_contract(d, b);

    uint8_t result = uint8_32_ct_eq(c, d);

    return result;
}

/**
 * Ascertain if a field element (encoded as bytes) is negative.
 *
 * Returns 1 iff the element is negative and 0 otherwise.
 */
static uint8_t bignum25519_is_negative(unsigned char bytes[32]) {
    uint8_t low_bit_is_set = bytes[0] & 1;

    return low_bit_is_set;
}

uint8_t curve25519_sqrt_ratio_i(bignum25519 out, const bignum25519 u, const bignum25519 v) {
    bignum25519 tmp = {0}, v3 = {0}, v7 = {0}, r = {0}, r_prime = {0}, r_negative = {0}, check = {0}, i = {0}, u_neg = {0}, u_neg_i = {0};
    unsigned char r_bytes[32] = {0};
    uint8_t r_is_negative;
    uint8_t correct_sign_sqrt;
    uint8_t flipped_sign_sqrt;
    uint8_t flipped_sign_sqrt_i;
    uint8_t was_nonzero_square;
    uint8_t should_rotate;

    curve25519_square(tmp, v);      // v²
    curve25519_mul(v3, tmp, v);     // v³
    curve25519_square(tmp, v3);      // v⁶
    curve25519_mul(v7, tmp, v);     // v⁷
    curve25519_mul(tmp, u, v7);     // u*v^7
    curve25519_pow_two252m3(r, tmp); // (u*v^7)^{(p-5)/8}
    curve25519_mul(r, r, u);        // (u)*(u*v^7)^{(p-5)/8}
    curve25519_mul(r, r, v3);        // (u)*(u*v^7)^{(p-5)/8}
    curve25519_square(tmp, r);       // tmp = r^2
    curve25519_mul(check, v, tmp);  // check = r^2 * v

    curve25519_neg(u_neg, u);
    curve25519_mul(u_neg_i, u_neg, SQRT_M1);

    correct_sign_sqrt = bignum25519_ct_eq(check, u);
    flipped_sign_sqrt = bignum25519_ct_eq(check, u_neg);
    flipped_sign_sqrt_i = bignum25519_ct_eq(check, u_neg_i);

    curve25519_mul(r_prime, r, SQRT_M1);
    should_rotate = flipped_sign_sqrt | flipped_sign_sqrt_i;
    curve25519_swap_conditional(r, r_prime, should_rotate);

    // Choose the non-negative square root
    curve25519_contract(r_bytes, r);
    r_is_negative = bignum25519_is_negative(r_bytes);
    curve25519_neg(r_negative, r);
    curve25519_swap_conditional(r, r_negative, r_is_negative);

    was_nonzero_square = correct_sign_sqrt | flipped_sign_sqrt;

    curve25519_copy(out, r);

    return was_nonzero_square;
}

/**
 * Calculate either `sqrt(1/v)` for a field element `v`.
 *
 * Returns:
 *  - 1 and stores `+sqrt(1/v)` in `out` if `v` was a non-zero square,
 *  - 0 and stores `0` in `out` if `v` was zero,
 *  - 0 and stores `+sqrt(i/v)` in `out` if `v` was a non-zero non-square.
 */
uint8_t curve25519_invsqrt(bignum25519 out, const bignum25519 v) {
    return curve25519_sqrt_ratio_i(out, one, v);
}

/**
 * Attempt to decompress `bytes` to a Ristretto group `element`.
 *
 * Returns 0 if the point could not be decoded and 1 otherwise.
 */
int ristretto_decode(ge25519 *element, const unsigned char bytes[32]) {
    bignum25519 s = {0}, ss = {0};
    bignum25519 u1 = {0}, u1_sqr = {0}, u2 = {0}, u2_sqr = {0};
    bignum25519 v = {0}, i = {0}, minus_d = {0}, dx = {0}, dy = {0}, x = {0}, y = {0}, t = {0};
    bignum25519 tmp = {0};
    unsigned char s_bytes_check[32] = {0};
    unsigned char x_bytes[32] = {0};
    unsigned char t_bytes[32] = {0};
    uint8_t s_encoding_is_canonical;
    uint8_t s_is_negative;
    uint8_t x_is_negative;
    uint8_t t_is_negative;
    uint8_t y_is_zero;
    uint8_t ok;

    // Step 1: Check that the encoding of the field element is canonical
    curve25519_expand(s, bytes);
    curve25519_contract(s_bytes_check, s);

    s_encoding_is_canonical = uint8_32_ct_eq(bytes, s_bytes_check);
    s_is_negative = bignum25519_is_negative(s_bytes_check);

    // Bail out if the field element encoding was non-canonical or negative
    if (s_encoding_is_canonical == 0 || s_is_negative == 1) {
        return 0;
    }

    // Step 2: Compute (X:Y:Z:T)
    // XXX can we eliminate these reductions
    curve25519_square(ss, s);
    curve25519_sub_reduce(u1, one, ss);    //  1 + as², where a = -1, d = -121665/121666
    curve25519_add_reduce(u2, one, ss);    //  1 - as²
    curve25519_square(u1_sqr, u1);         // (1 + as²)²
    curve25519_square(u2_sqr, u2);         // (1 - as²)²
    curve25519_neg(minus_d, EDWARDS_D);    // -d               // XXX store as const?
    curve25519_mul(tmp, minus_d, u1_sqr);  // ad(1+as²)²
    curve25519_sub_reduce(v, tmp, u2_sqr); // ad(1+as²)² - (1-as²)²
    curve25519_mul(tmp, v, u2_sqr);        // v = (ad(1+as²)² - (1-as²)²)(1-as²)²

    ok = curve25519_invsqrt(i, tmp);       // i = 1/sqrt{(ad(1+as²)² - (1-as²)²)(1-as²)²}

    // Step 3: Calculate x and y denominators, then compute x.
    curve25519_mul(dx, i, u2);             // 1/sqrt(v)
    curve25519_mul(tmp, dx, v);            // v/sqrt(v)
    curve25519_mul(dy, i, tmp);            // 1/(1-as²)
    curve25519_add_reduce(tmp, s, s);      // 2s
    curve25519_mul(x, tmp, dx);            // x = |2s/sqrt(v)| = +sqrt(4s²/(ad(1+as²)² - (1-as²)²))
    curve25519_contract(x_bytes, x);

    // Step 4: Conditionally negate x if it's negative.
    x_is_negative = bignum25519_is_negative(x_bytes);

    curve25519_neg(tmp, x);
    curve25519_swap_conditional(x, tmp, x_is_negative);

    // Step 5: Compute y = (1-as²)/(1+as²) and t = {(1+as²)sqrt(4s²/(ad(1+as²)²-(1-as²)²))}/(1-as²)
    curve25519_mul(y, u1, dy);
    curve25519_mul(t, x, y);
    curve25519_contract(t_bytes, t);

    t_is_negative = bignum25519_is_negative(t_bytes);
    y_is_zero = bignum25519_ct_eq(zero, y);

    if (ok == 0 || t_is_negative == 1 || y_is_zero == 1) {
        return 0;
    }

    curve25519_copy(element->x, x);
    curve25519_copy(element->y, y);
    curve25519_copy(element->z, one);
    curve25519_copy(element->t, t);

    return 1;
}

void ristretto_encode(unsigned char bytes[32], const ge25519 element) {
    bignum25519 u1 = {0}, u2 = {0}, u22 = {0}, i1 = {0}, i2 = {0}, z_inv = {0}, den_inv = {0}, ix = {0}, iy = {0}, invsqrt = {0}, tmp1 = {0}, tmp2 = {0};
    bignum25519 x = {0}, y = {0}, y_neg = {0}, s = {0}, s_neg = {0};
    bignum25519 enchanted_denominator = {0};
    unsigned char contracted[32] = {0};
    uint8_t x_zinv_is_negative;
    uint8_t s_is_negative;
    uint8_t rotate;

    curve25519_add_reduce(tmp1, element.z, element.y);
    curve25519_sub_reduce(tmp2, element.z, element.y);
    curve25519_mul(u1, tmp1, tmp2);
    curve25519_mul(u2, element.x, element.y);

    curve25519_square(u22, u2);
    curve25519_mul(tmp1, u1, u22);

    // This is always square so we don't need to check the return value
    int ok = curve25519_invsqrt(invsqrt, tmp1);

    curve25519_mul(i1, invsqrt, u1);
    curve25519_mul(i2, invsqrt, u2);
    curve25519_mul(tmp1, i2, element.t);
    curve25519_mul(z_inv, tmp1, i1);
    curve25519_mul(ix, element.x, SQRT_M1);
    curve25519_mul(iy, element.y, SQRT_M1);
    curve25519_mul(enchanted_denominator, i1, INVSQRT_A_MINUS_D);
    curve25519_mul(tmp1, element.t, z_inv);
    curve25519_contract(contracted, tmp1);

    rotate = bignum25519_is_negative(contracted);

    curve25519_copy(x, element.x);
    curve25519_copy(y, element.y);

    // Rotate into the distinguished Jacobi quartic quadrant
    curve25519_swap_conditional(x, iy, rotate);
    curve25519_swap_conditional(y, ix, rotate);
    curve25519_swap_conditional(i2, enchanted_denominator, rotate);

    // Next we torque the points to be non-negative

    // Conditionally flip the sign of y to be positive
    curve25519_mul(tmp1, x, z_inv);
    curve25519_contract(contracted, tmp1);

    x_zinv_is_negative = bignum25519_is_negative(contracted);

    curve25519_neg(y_neg, y);
    curve25519_swap_conditional(y, y_neg, x_zinv_is_negative);

    curve25519_sub_reduce(tmp1, element.z, y);
    curve25519_mul(s, i2, tmp1);
    curve25519_contract(contracted, s);

    // Conditionally flip the sign of s to be positive
    s_is_negative = bignum25519_is_negative(contracted);

    curve25519_neg(s_neg, s);
    curve25519_swap_conditional(s, s_neg, s_is_negative);

    // Output the compressed form of s
    curve25519_contract(bytes, s);
}

/**
 * Test equality of two `ristretto_point_t`s in constant time.
 *
 * Returns 1 if the two points are equal, and 0 otherwise.
 */
int ristretto_ct_eq(const ge25519 *a, const ge25519 *b) {
    bignum25519 x1y2 = {0}, y1x2 = {0}, x1x2 = {0}, y1y2 = {0};
    uint8_t check_one, check_two;

    curve25519_mul(x1y2, a->x, b->y);
    curve25519_mul(y1x2, a->y, b->x);
    curve25519_mul(x1x2, a->x, b->x);
    curve25519_mul(y1y2, a->y, b->y);

    check_one = bignum25519_ct_eq(x1y2, y1x2);
    check_two = bignum25519_ct_eq(x1x2, y1y2);

    return check_one | check_two;
}

/* sr25519lib: Elligator hash-to-group removed (VRF only, unused). */

/*
    scalarmults
*/

void ge25519_set_neutral(ge25519 *r)
{
    memzero(r, sizeof(ge25519));
    r->y[0] = 1;
    r->z[0] = 1;
}

static void ge25519_cmove_stride4b(long * r, long * p, long * pos, long * n, int stride) {
  long x0=p[0], x1=p[1], x2=p[2], x3=p[3], y0 = 0, y1 = 0, y2 = 0, y3 = 0;
  for(p+=stride; p<n; p+=stride) {
    volatile int flag=(p==pos);
    y0 = p[0];
    y1 = p[1];
    y2 = p[2];
    y3 = p[3];
    x0 = flag ? y0 : x0;
    x1 = flag ? y1 : x1;
    x2 = flag ? y2 : x2;
    x3 = flag ? y3 : x3;
  }
  r[0] = x0;
  r[1] = x1;
  r[2] = x2;
  r[3] = x3;
}
#define HAS_CMOVE_STRIDE4B

void ge25519_move_conditional_pniels_array(ge25519_pniels * r, const ge25519_pniels * p, int pos, int n) {
#ifdef HAS_CMOVE_STRIDE4B
  size_t i = 0;
  for(i=0; i<sizeof(ge25519_pniels)/sizeof(long); i+=4) {
    ge25519_cmove_stride4b(((long*)r)+i,
               ((long*)p)+i,
               ((long*)(p+pos))+i,
               ((long*)(p+n))+i,
               sizeof(ge25519_pniels)/sizeof(long));
  }
#else
  size_t i = 0;
  for(i=0; i<n; i++) {
    ge25519_move_conditional_pniels(r, p+i, pos==i);
  }
#endif
}

/* computes [s1]p1, constant time */
void ge25519_scalarmult_tg(ge25519 *r, const ge25519 *p1, const bignum256modm s1) {
    signed char slide1[64] = {0};
    ge25519_pniels pre1[9] = {0};
    ge25519_pniels pre = {0};
    ge25519 d1 = {0};
    ge25519_p1p1 t = {0};
    int32_t i = 0;

    contract256_window4_modm(slide1, s1);

    ge25519_full_to_pniels(pre1+1, p1);
    ge25519_double(&d1, p1);

    ge25519_set_neutral(r);
    ge25519_full_to_pniels(pre1, r);

    ge25519_full_to_pniels(pre1+2, &d1);
    for (i = 1; i < 7; i++) {
        ge25519_pnielsadd(&pre1[i+2], &d1, &pre1[i]);
    }

    for (i = 63; i >= 0; i--) {
        int k=abs(slide1[i]);
        ge25519_double_partial(r, r);
        ge25519_double_partial(r, r);
        ge25519_double_partial(r, r);
        ge25519_double_p1p1(&t, r);
        ge25519_move_conditional_pniels_array(&pre, pre1, k, 9);
        ge25519_p1p1_to_full(r, &t);
        ge25519_pnielsadd_p1p1(&t, r, &pre, (unsigned char)slide1[i] >> 7);
        ge25519_p1p1_to_partial(r, &t);
    }
    curve25519_mul(r->t, t.x, t.y);
}
