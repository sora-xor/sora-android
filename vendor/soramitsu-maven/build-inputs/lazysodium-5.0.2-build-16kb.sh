#!/usr/bin/env bash
# Rebuild the exact libsodium 1.0.18 native component used by Lazysodium 5.0.2.
set -euo pipefail

if [[ $# -ne 3 ]]; then
    echo "usage: $0 libsodium-1.0.18.tar.gz android-ndk-r28 output-directory" >&2
    exit 2
fi

tarball=$1
ndk_root=$2
output_dir=$3
tarball=$(cd "$(dirname "$tarball")" && pwd)/$(basename "$tarball")
ndk_root=$(cd "$ndk_root" && pwd)
expected_tarball=6f504490b342a4f8a4c4a02fc9b866cbef8622d5df4e5452b46be121e46636c1
actual_tarball=$(shasum -a 256 "$tarball" | awk '{print $1}')
if [[ "$actual_tarball" != "$expected_tarball" ]]; then
    echo "libsodium source archive checksum mismatch" >&2
    exit 1
fi
if [[ ! -f "$ndk_root/source.properties" ]] ||
    ! grep -q '^Pkg.BaseRevision = 28.0.12674087$' "$ndk_root/source.properties"; then
    echo "Android NDK r28 beta2 (base revision 28.0.12674087) is required" >&2
    exit 1
fi
if [[ -e "$output_dir" ]]; then
    echo "output directory already exists; use an empty, new location" >&2
    exit 1
fi

mkdir -p "$output_dir"
output_dir=$(cd "$output_dir" && pwd)
tar -xzf "$tarball" -C "$output_dir"
toolchain="$ndk_root/toolchains/llvm/prebuilt/darwin-x86_64/bin"
if [[ ! -x "$toolchain/llvm-ar" ]]; then
    echo "this recipe requires the NDK r28 macOS toolchain" >&2
    exit 1
fi

build_one() {
    local abi=$1 compiler=$2 host=$3 arch_flag=$4 expected_so=$5
    local build_dir="$output_dir/build-$abi"
    local install_dir="$output_dir/install-$abi"
    mkdir -p "$build_dir"
    (
        cd "$build_dir"
        env CC="$toolchain/$compiler" \
            AR="$toolchain/llvm-ar" \
            RANLIB="$toolchain/llvm-ranlib" \
            STRIP="$toolchain/llvm-strip" \
            CFLAGS="-Os -march=$arch_flag -fPIC" \
            LDFLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384" \
            ../libsodium-1.0.18/configure \
                --disable-soname-versions --enable-minimal \
                --host="$host" --prefix="$install_dir"
        make -j8 install
    ) > "$output_dir/build-$abi.log" 2>&1
    mkdir -p "$output_dir/$abi"
    cp "$install_dir/lib/libsodium.so" "$output_dir/$abi/libsodium.so"
    local actual_so
    actual_so=$(shasum -a 256 "$output_dir/$abi/libsodium.so" | awk '{print $1}')
    if [[ "$actual_so" != "$expected_so" ]]; then
        echo "$abi native output checksum mismatch: $actual_so" >&2
        exit 1
    fi
    echo "$abi: $actual_so"
}

build_one arm64-v8a aarch64-linux-android21-clang aarch64-linux-android armv8-a \
    917eb22551c9792b0b5316a8dae614d7a68c2232e28075ae73afeeac94a8e3cb
build_one x86_64 x86_64-linux-android21-clang x86_64-linux-android westmere \
    8e7278efed584d83f99b1a374443fdbc2612c60b0ff17b8aa5a5152d1a909b26
