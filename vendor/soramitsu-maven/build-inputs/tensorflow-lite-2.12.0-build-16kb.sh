#!/usr/bin/env bash
# Rebuild the two 64-bit JNI libraries from TensorFlow Lite's exact v2.12.0 tag.
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "usage: $0 CLEAN_TF_SOURCE_DIR BAZEL_EXTERNAL_SOURCE_DIR OUTPUT_DIR" >&2
  exit 2
fi
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to the Android SDK containing NDK r28 beta2}"
source_dir="$(cd "$1" && pwd -P)"
external_dir="$(cd "$2" && pwd -P)"
output_dir="$3"
build_inputs="$(cd "$(dirname "$0")" && pwd -P)"
ndk_bin="$ANDROID_SDK_ROOT/ndk/28.0.12674087/toolchains/llvm/prebuilt/darwin-x86_64/bin"
cmake_bin="$(command -v cmake)"
ninja_bin="$(command -v ninja)"

[[ "$(git -C "$source_dir" rev-parse HEAD)" == 0db597d0d758aba578783b5bf46c889700a45085 ]]
[[ "$(git -C "$source_dir" rev-parse 'HEAD^{tree}')" == e32b1f067af476b65f944fdcc2390178200408d1 ]]
[[ -z "$(git -C "$source_dir" status --porcelain)" ]]
[[ "$(sed -n 's/^Pkg.Revision = //p' "$ANDROID_SDK_ROOT/ndk/28.0.12674087/source.properties")" == 28.0.12674087-beta2 ]]
[[ "$("$ndk_bin/aarch64-linux-android21-clang++" --version | head -1)" == *'clang version 19.0.0'* ]]
[[ "$("$cmake_bin" --version | head -1)" == 'cmake version 3.31.10' ]]
[[ "$("$ninja_bin" --version)" == 1.13.* ]]
for name in pthreadpool FP16 FXdiv; do
  [[ -d "$external_dir/$name" ]]
done

git -C "$source_dir" apply --check "$build_inputs/tensorflow-lite-2.12.0-cmake-16kb.patch"
git -C "$source_dir" apply "$build_inputs/tensorflow-lite-2.12.0-cmake-16kb.patch"
restore_source() {
  git -C "$source_dir" apply -R "$build_inputs/tensorflow-lite-2.12.0-cmake-16kb.patch"
}
trap restore_source EXIT

for abi in arm64-v8a x86_64; do
  build_dir="$output_dir/cmake-$abi"
  mkdir -p "$build_dir"
  args=(
    -S "$source_dir/tensorflow/lite" -B "$build_dir" -G Ninja
    "-DCMAKE_MAKE_PROGRAM=$ninja_bin"
    "-DCMAKE_TOOLCHAIN_FILE=$ANDROID_SDK_ROOT/ndk/28.0.12674087/build/cmake/android.toolchain.cmake"
    "-DANDROID_ABI=$abi" -DANDROID_PLATFORM=android-21 -DANDROID_STL=c++_static
    -DCMAKE_BUILD_TYPE=Release -DTFLITE_ENABLE_XNNPACK=ON
    "-DPTHREADPOOL_SOURCE_DIR=$external_dir/pthreadpool"
    "-DFP16_SOURCE_DIR=$external_dir/FP16"
    "-DFXDIV_SOURCE_DIR=$external_dir/FXdiv"
    "-DTFLITE_EXPORTS_MAP=$build_inputs/tensorflow-lite-2.12.0-exports.lds"
  )
  # Optional locally fetched, upstream-pinned CMake sources avoid redownloading
  # dependencies when two ABIs are built. Without this, CMake fetches them.
  if [[ -n "${TFLITE_CMAKE_SOURCE_CACHE:-}" ]]; then
    for name in abseil-cpp cpuinfo eigen farmhash fft2d flatbuffers gemmlowp neon2sse ruy xnnpack; do
      [[ -d "$TFLITE_CMAKE_SOURCE_CACHE/$name" ]]
      upper_name="$(printf '%s' "$name" | tr '[:lower:]' '[:upper:]')"
      args+=("-DFETCHCONTENT_SOURCE_DIR_${upper_name}=$TFLITE_CMAKE_SOURCE_CACHE/$name")
    done
  fi
  "$cmake_bin" "${args[@]}"
  "$cmake_bin" --build "$build_dir" --target tensorflowlite_jni_16kb -j "${TFLITE_BUILD_JOBS:-8}"
  mkdir -p "$output_dir/$abi"
  "$ndk_bin/llvm-strip" --strip-unneeded -o "$output_dir/$abi/libtensorflowlite_jni.so" \
    "$build_dir/libtensorflowlite_jni.so"
done

[[ "$(shasum -a 256 "$output_dir/arm64-v8a/libtensorflowlite_jni.so" | awk '{print $1}')" == c9099070c3034d21cbcda735cb8e39f83c34fa5e3ab079eb8d6d189e5c5fdae7 ]]
[[ "$(shasum -a 256 "$output_dir/x86_64/libtensorflowlite_jni.so" | awk '{print $1}')" == a95289cb76794d714a91ce3fa240b92f88b8a356293c7bcdc9ca503cfcc98bef ]]
echo 'TensorFlow Lite 2.12.0 64-bit JNI outputs match pinned SHA-256 digests.'
