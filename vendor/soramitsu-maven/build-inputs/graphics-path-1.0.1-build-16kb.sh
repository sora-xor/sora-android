#!/usr/bin/env bash
# Rebuild only AndroidX graphics-path 1.0.1's 64-bit JNI libraries.
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 PINNED_SUPPORT_SOURCE_DIR OUTPUT_DIR" >&2
  exit 2
fi
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to the Android SDK containing NDK r28 beta2}"
source_dir="$(cd "$1" && pwd -P)"
output_dir="$2"
cpp_dir="$source_dir/graphics/graphics-path/src/main/cpp"
ndk_bin="$ANDROID_SDK_ROOT/ndk/28.0.12674087/toolchains/llvm/prebuilt/darwin-x86_64/bin"

[[ "$(git -C "$source_dir" rev-parse HEAD)" == 8a05a22af450d589ef911d772a001a49dcb05b71 ]]
[[ "$(git -C "$source_dir" rev-parse 'HEAD^{tree}')" == f1ec6450df09939325e8dbe5cf57b589a673afc3 ]]
[[ "$(git -C "$source_dir" rev-parse 'HEAD:graphics/graphics-path/src/main/cpp')" == 27981507de4b994a6e92a9da8d89e5436600f00c ]]
[[ -z "$(git -C "$source_dir" status --porcelain -- graphics/graphics-path/src/main/cpp)" ]]
[[ "$(sed -n 's/^Pkg.Revision = //p' "$ANDROID_SDK_ROOT/ndk/28.0.12674087/source.properties")" == 28.0.12674087-beta2 ]]
[[ "$("$ndk_bin/aarch64-linux-android21-clang++" --version | head -1)" == *'clang version 19.0.0'* ]]

mkdir -p "$output_dir/arm64-v8a" "$output_dir/x86_64"
flags=(-std=c++17 -fPIC -O2 -fstack-protector-strong
  -fno-exceptions -fno-unwind-tables -fno-asynchronous-unwind-tables -fno-rtti
  -ffast-math -ffp-contract=fast -fvisibility-inlines-hidden -fvisibility=hidden
  -fomit-frame-pointer -ffunction-sections -fdata-sections -nostdlib++
  -Wl,--hash-style=both -Wl,--gc-sections -Wl,-Bsymbolic-functions
  -Wl,-z,relro -Wl,-z,now -Wl,-z,max-page-size=16384
  -Wl,-z,common-page-size=16384 -Wl,-soname,libandroidx.graphics.path.so
  "-Wl,--version-script=$cpp_dir/libandroidx.graphics.path.map")
sources=("$cpp_dir/Conic.cpp" "$cpp_dir/PathIterator.cpp" "$cpp_dir/pathway.cpp")

"$ndk_bin/aarch64-linux-android21-clang++" "${flags[@]}" -shared "${sources[@]}" \
  -o "$output_dir/arm64-v8a/libandroidx.graphics.path.so"
"$ndk_bin/x86_64-linux-android21-clang++" "${flags[@]}" -shared "${sources[@]}" \
  -o "$output_dir/x86_64/libandroidx.graphics.path.so"
[[ "$(shasum -a 256 "$output_dir/arm64-v8a/libandroidx.graphics.path.so" | awk '{print $1}')" == d7ec2a1d0e1c1c652dd7bc0f70825835b1c9a6d26f72caab33b88f4f425d5e9e ]]
[[ "$(shasum -a 256 "$output_dir/x86_64/libandroidx.graphics.path.so" | awk '{print $1}')" == c286266ff104672626bce3b87b3b3367e017db972cff12dd53290c967de42e17 ]]
echo 'graphics-path 1.0.1 64-bit JNI outputs match pinned SHA-256 digests.'
