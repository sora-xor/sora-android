# sr25519 for Sr25519JNI, built from the C sources in sr25519/ (the same files
# as sora-ios VendorPackages/shared-features-spm/Sources/sr25519lib).
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := sora_sr25519
LOCAL_SRC_FILES := \
    sr25519_jni.c \
    sr25519/sr25519.c \
    sr25519/donna/merlin.c \
    sr25519/donna/ristretto255.c \
    sr25519/donna/sha2.c \
    sr25519/donna/memzero.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/sr25519/include $(LOCAL_PATH)/sr25519
LOCAL_CFLAGS := -O2 -fvisibility=hidden -fstack-protector-strong -D_FORTIFY_SOURCE=2 \
    -Wall -Wno-unused-function -Wno-incompatible-pointer-types -Wno-macro-redefined
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 -Wl,-z,relro -Wl,-z,now
include $(BUILD_SHARED_LIBRARY)
