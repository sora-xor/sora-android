#!/usr/bin/env bash
# Rebuild only RootBeer 0.1.0's 64-bit JNI libraries from the pinned upstream source.
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 PINNED_SOURCE_DIR OUTPUT_DIR" >&2
  exit 2
fi
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to the Android SDK containing NDK r28}"
source_dir="$(cd "$1" && pwd -P)"
output_dir="$2"
ndk_bin="$ANDROID_SDK_ROOT/ndk/28.0.12674087/toolchains/llvm/prebuilt/darwin-x86_64/bin"
source_file="$source_dir/rootbeerlib/src/main/cpp/toolChecker.cpp"
header_file="$source_dir/rootbeerlib/src/main/cpp/toolChecker.h"

[[ "$(git -C "$source_dir" rev-parse HEAD)" == d9057ce490c3481bc9be852e343678c93860e6a8 ]]
[[ "$(git -C "$source_dir" rev-parse 'HEAD^{tree}')" == e50696d927e68c68e88520a013d50b481c3a1f8c ]]
[[ "$(shasum -a 256 "$source_file" | awk '{print $1}')" == b6239571e19ce95e5e6c90ff0502a94468e4023ecfb100350fa0c04c0489ae62 ]]
[[ "$(shasum -a 256 "$header_file" | awk '{print $1}')" == e82c0796eff735c5afe0210e41d1411368f922fe902d83b8e789d72ec9dc2de8 ]]
[[ "$(sed -n 's/^Pkg.Revision = //p' "$ANDROID_SDK_ROOT/ndk/28.0.12674087/source.properties")" == 28.0.12674087 ]]
[[ "$("$ndk_bin/aarch64-linux-android21-clang++" --version | head -1)" == *'clang version 19.0.0'* ]]

mkdir -p "$output_dir/arm64-v8a" "$output_dir/x86_64"
flags=(-std=c++11 -fPIC -fstack-protector-all -O2 -static-libstdc++
  -Wl,-z,relro -Wl,-z,now
  -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384)
"$ndk_bin/aarch64-linux-android21-clang++" "${flags[@]}" -shared "$source_file" -llog -landroid \
  -o "$output_dir/arm64-v8a/libtoolChecker.so"
"$ndk_bin/x86_64-linux-android21-clang++" "${flags[@]}" -shared "$source_file" -llog -landroid \
  -o "$output_dir/x86_64/libtoolChecker.so"
[[ "$(shasum -a 256 "$output_dir/arm64-v8a/libtoolChecker.so" | awk '{print $1}')" == 656be4fef61da1f1fe9740beac8c11297b3337f9028dceb87bdbbf7a65414cfa ]]
[[ "$(shasum -a 256 "$output_dir/x86_64/libtoolChecker.so" | awk '{print $1}')" == 15b820b18ac414d90dff74e225743f05b3ba793bd6ad9084b7c1a7bd5e52eee5 ]]
echo 'RootBeer 0.1.0 64-bit JNI outputs match pinned SHA-256 digests.'
