/*
 * JNI binding of jp.co.soramitsu.xcrypto.encryption.sr25519.Sr25519JNI over the
 * in-repo sr25519 C library (sr25519/). It replaces the Rust sr25519-java
 * library (x-crypto, schnorrkel 0.9.1) with the same byte formats and the same
 * Java-visible behaviour:
 *  - invalid key material throws java.lang.Exception with the old messages
 *    (the pair message no longer prints the key pair);
 *  - verify() returns false for a malformed signature and throws for a public
 *    key that is not a Ristretto point, checked in that order;
 *  - fromEd25519Bytes() returns null for an input that is not 64 bytes;
 *  - a chain code that is not 32 bytes throws instead of aborting the process.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <jni.h>
#include <stdint.h>
#include <string.h>

#include "sr25519lib/sr25519.h"
#include "donna/memzero.h"

#define EXCEPTION_CLASS "java/lang/Exception"

static void throw_exception(JNIEnv *env, const char *message) {
    jclass cls = (*env)->FindClass(env, EXCEPTION_CLASS);
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, message);
    }
}

/* Copies a Java byte array of exactly `size` bytes; 0 if null or another size. */
static int read_exact(JNIEnv *env, jbyteArray array, uint8_t *out, jsize size) {
    if (array == NULL) {
        return 0;
    }
    if ((*env)->GetArrayLength(env, array) != size) {
        return 0;
    }
    (*env)->GetByteArrayRegion(env, array, 0, size, (jbyte *)out);
    return (*env)->ExceptionCheck(env) ? 0 : 1;
}

static jbyteArray new_array(JNIEnv *env, const uint8_t *bytes, jsize size) {
    jbyteArray result = (*env)->NewByteArray(env, size);
    if (result != NULL) {
        (*env)->SetByteArrayRegion(env, result, 0, size, (const jbyte *)bytes);
    }
    return result;
}

/* Reads a key pair: 96 bytes, canonical secret scalar, valid public key. */
static int read_pair(JNIEnv *env, jbyteArray pair, uint8_t out[SR25519_KEYPAIR_SIZE]) {
    if (!read_exact(env, pair, out, SR25519_KEYPAIR_SIZE) ||
        !sr25519_secret_is_valid(out) ||
        !sr25519_public_key_is_valid(out + SR25519_SECRET_SIZE)) {
        memzero(out, SR25519_KEYPAIR_SIZE);
        if (!(*env)->ExceptionCheck(env)) {
            throw_exception(env, "Provided pair is invalid");
        }
        return 0;
    }
    return 1;
}

static int read_chain_code(JNIEnv *env, jbyteArray cc, uint8_t out[SR25519_CHAINCODE_SIZE]) {
    if (!read_exact(env, cc, out, SR25519_CHAINCODE_SIZE)) {
        if (!(*env)->ExceptionCheck(env)) {
            throw_exception(env, "Provided chain code is invalid");
        }
        return 0;
    }
    return 1;
}

static int read_public(JNIEnv *env, jbyteArray public_key, uint8_t out[SR25519_PUBLIC_SIZE]) {
    if (!read_exact(env, public_key, out, SR25519_PUBLIC_SIZE) || !sr25519_public_key_is_valid(out)) {
        if (!(*env)->ExceptionCheck(env)) {
            throw_exception(env, "Provided public key is invalid.");
        }
        return 0;
    }
    return 1;
}

static int read_secret(JNIEnv *env, jbyteArray secret, uint8_t out[SR25519_SECRET_SIZE]) {
    if (!read_exact(env, secret, out, SR25519_SECRET_SIZE) || !sr25519_secret_is_valid(out)) {
        memzero(out, SR25519_SECRET_SIZE);
        if (!(*env)->ExceptionCheck(env)) {
            throw_exception(env, "Provided private key is invalid.");
        }
        return 0;
    }
    return 1;
}

JNIEXPORT jstring JNICALL
Java_jp_co_soramitsu_xcrypto_encryption_sr25519_Sr25519JNI_test(JNIEnv *env, jclass cls, jstring hello_what) {
    (void)cls;
    const char *what = hello_what != NULL ? (*env)->GetStringUTFChars(env, hello_what, NULL) : NULL;
    if (what == NULL) {
        if (!(*env)->ExceptionCheck(env)) {
            throw_exception(env, "Provided string is invalid");
        }
        return NULL;
    }
    char buffer[256];
    size_t length = strlen(what);
    if (length > sizeof buffer - 9) {
        length = sizeof buffer - 9;
    }
    memcpy(buffer, "Hello, ", 7);
    memcpy(buffer + 7, what, length);
    buffer[7 + length] = '!';
    buffer[8 + length] = '\0';
    (*env)->ReleaseStringUTFChars(env, hello_what, what);
    return (*env)->NewStringUTF(env, buffer);
}

JNIEXPORT jboolean JNICALL
Java_jp_co_soramitsu_xcrypto_encryption_sr25519_Sr25519JNI_verify(JNIEnv *env, jclass cls,
                                                                  jbyteArray signature, jbyteArray message,
                                                                  jbyteArray public_key) {
    (void)cls;
    if (signature == NULL || message == NULL || public_key == NULL) {
        throw_exception(env, "Provided array is null");
        return JNI_FALSE;
    }
    uint8_t sig[SR25519_SIGNATURE_SIZE];
    if (!read_exact(env, signature, sig, SR25519_SIGNATURE_SIZE) || !sr25519_signature_is_well_formed(sig)) {
        return JNI_FALSE;
    }
    uint8_t pub[SR25519_PUBLIC_SIZE];
    if (!read_public(env, public_key, pub)) {
        return JNI_FALSE;
    }
    jsize length = (*env)->GetArrayLength(env, message);
    jbyte *bytes = (*env)->GetByteArrayElements(env, message, NULL);
    if (bytes == NULL) {
        return JNI_FALSE;
    }
    bool valid = sr25519_verify(sig, (const uint8_t *)bytes, (unsigned long)length, pub);
    (*env)->ReleaseByteArrayElements(env, message, bytes, JNI_ABORT);
    return valid ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_jp_co_soramitsu_xcrypto_encryption_sr25519_Sr25519JNI_sign(JNIEnv *env, jclass cls, jbyteArray public_key,
                                                                jbyteArray secret, jbyteArray message) {
    (void)cls;
    if (message == NULL) {
        throw_exception(env, "Provided array is null");
        return NULL;
    }
    uint8_t sec[SR25519_SECRET_SIZE];
    if (!read_secret(env, secret, sec)) {
        return NULL;
    }
    uint8_t pub[SR25519_PUBLIC_SIZE];
    if (!read_public(env, public_key, pub)) {
        memzero(sec, sizeof sec);
        return NULL;
    }
    jsize length = (*env)->GetArrayLength(env, message);
    jbyte *bytes = (*env)->GetByteArrayElements(env, message, NULL);
    if (bytes == NULL) {
        memzero(sec, sizeof sec);
        return NULL;
    }
    uint8_t sig[SR25519_SIGNATURE_SIZE];
    sr25519_sign(sig, pub, sec, (const uint8_t *)bytes, (unsigned long)length);
    (*env)->ReleaseByteArrayElements(env, message, bytes, JNI_ABORT);
    memzero(sec, sizeof sec);
    return new_array(env, sig, SR25519_SIGNATURE_SIZE);
}

JNIEXPORT jbyteArray JNICALL
Java_jp_co_soramitsu_xcrypto_encryption_sr25519_Sr25519JNI_deriveKeypairHard(JNIEnv *env, jclass cls,
                                                                             jbyteArray pair, jbyteArray cc) {
    (void)cls;
    uint8_t in[SR25519_KEYPAIR_SIZE], chain[SR25519_CHAINCODE_SIZE], out[SR25519_KEYPAIR_SIZE];
    if (!read_pair(env, pair, in)) {
        return NULL;
    }
    if (!read_chain_code(env, cc, chain)) {
        memzero(in, sizeof in);
        return NULL;
    }
    sr25519_derive_keypair_hard(out, in, chain);
    jbyteArray result = new_array(env, out, SR25519_KEYPAIR_SIZE);
    memzero(in, sizeof in);
    memzero(out, sizeof out);
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_jp_co_soramitsu_xcrypto_encryption_sr25519_Sr25519JNI_deriveKeypairSoft(JNIEnv *env, jclass cls,
                                                                             jbyteArray pair, jbyteArray cc) {
    (void)cls;
    uint8_t in[SR25519_KEYPAIR_SIZE], chain[SR25519_CHAINCODE_SIZE], out[SR25519_KEYPAIR_SIZE];
    if (!read_pair(env, pair, in)) {
        return NULL;
    }
    if (!read_chain_code(env, cc, chain)) {
        memzero(in, sizeof in);
        return NULL;
    }
    sr25519_derive_keypair_soft(out, in, chain);
    jbyteArray result = new_array(env, out, SR25519_KEYPAIR_SIZE);
    memzero(in, sizeof in);
    memzero(out, sizeof out);
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_jp_co_soramitsu_xcrypto_encryption_sr25519_Sr25519JNI_derivePublicSoft(JNIEnv *env, jclass cls,
                                                                            jbyteArray public_key, jbyteArray cc) {
    (void)cls;
    uint8_t pub[SR25519_PUBLIC_SIZE], chain[SR25519_CHAINCODE_SIZE], out[SR25519_PUBLIC_SIZE];
    if (!read_public(env, public_key, pub) || !read_chain_code(env, cc, chain)) {
        return NULL;
    }
    sr25519_derive_public_soft(out, pub, chain);
    return new_array(env, out, SR25519_PUBLIC_SIZE);
}

JNIEXPORT jbyteArray JNICALL
Java_jp_co_soramitsu_xcrypto_encryption_sr25519_Sr25519JNI_keypairFromSeed(JNIEnv *env, jclass cls, jbyteArray seed) {
    (void)cls;
    uint8_t mini[SR25519_SEED_SIZE], out[SR25519_KEYPAIR_SIZE];
    if (!read_exact(env, seed, mini, SR25519_SEED_SIZE)) {
        if (!(*env)->ExceptionCheck(env)) {
            throw_exception(env, "Provided seed is invalid");
        }
        return NULL;
    }
    sr25519_keypair_from_seed(out, mini);
    jbyteArray result = new_array(env, out, SR25519_KEYPAIR_SIZE);
    memzero(mini, sizeof mini);
    memzero(out, sizeof out);
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_jp_co_soramitsu_xcrypto_encryption_sr25519_Sr25519JNI_toEd25519Bytes(JNIEnv *env, jclass cls, jbyteArray secret) {
    (void)cls;
    uint8_t sec[SR25519_SECRET_SIZE], out[SR25519_SECRET_SIZE];
    if (!read_secret(env, secret, sec)) {
        return NULL;
    }
    sr25519_to_ed25519_bytes(out, sec);
    jbyteArray result = new_array(env, out, SR25519_SECRET_SIZE);
    memzero(sec, sizeof sec);
    memzero(out, sizeof out);
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_jp_co_soramitsu_xcrypto_encryption_sr25519_Sr25519JNI_fromEd25519Bytes(JNIEnv *env, jclass cls,
                                                                            jbyteArray ed_secret) {
    (void)cls;
    uint8_t in[SR25519_SECRET_SIZE], out[SR25519_SECRET_SIZE];
    if (ed_secret == NULL) {
        throw_exception(env, "Provided array is null");
        return NULL;
    }
    if (!read_exact(env, ed_secret, in, SR25519_SECRET_SIZE)) {
        return NULL;
    }
    sr25519_from_ed25519_bytes(out, in);
    jbyteArray result = new_array(env, out, SR25519_SECRET_SIZE);
    memzero(in, sizeof in);
    memzero(out, sizeof out);
    return result;
}
