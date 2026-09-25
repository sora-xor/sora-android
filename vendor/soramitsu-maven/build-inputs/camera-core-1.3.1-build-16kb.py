#!/usr/bin/env python3
"""Build CameraX 1.3.1's 64-bit JNI from pinned AndroidX/libyuv source trees."""

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET


SUPPORT_COMMIT = "ee5fe2aa34dba21365bb402477c32c593ccbecda"
SUPPORT_TREE = "0ca4c35926f8b22e4e660dee2f6fc807b69c9738"
CAMERA_CPP_TREE = "4bf7c67fce6b47c7c19c4b047416f23bf481c68d"
MANIFEST_COMMIT = "2f7e8332eff3d34fdfb1b471f44d654aa6518e41"
MANIFEST_TREE = "984e5a745f5a677e05e778d6f9c528be9f8d5951"
MANIFEST_SUPPORT_COMMIT = "0e832b9f4335f232b8520df7e430b5f8e8544dc0"
LIBYUV_COMMIT = "096484820d74c72a6838b3e80743fc7a5d94784b"
LIBYUV_TREE = "303e8eabb435a80ebec36a19c23ca85744d58fb5"
EXPECTED = {
    "arm64-v8a": "2e7b45ad96e790e1b11095f22021232d29d9b128d776eb5a2a7485d22da19e4e",
    "x86_64": "c8ac7fe97507a374f789b42f8ca41dff671c4723eb8ec78ec76872746143a4d0",
}


def command(*parts: str) -> str:
    return subprocess.check_output(parts, text=True).strip()


def git(directory: Path, *args: str) -> str:
    return command("git", "-C", str(directory), *args)


def assert_source(support: Path, libyuv: Path, manifest: Path) -> None:
    if (git(support, "rev-parse", "HEAD") != SUPPORT_COMMIT or
            git(support, "rev-parse", "HEAD^{tree}") != SUPPORT_TREE or
            git(support, "rev-parse", "HEAD:camera/camera-core/src/main/cpp") != CAMERA_CPP_TREE or
            git(support, "rev-parse", f"{MANIFEST_SUPPORT_COMMIT}:camera/camera-core/src/main/cpp") != CAMERA_CPP_TREE or
            git(support, "status", "--porcelain", "--", "camera/camera-core/src/main/cpp")):
        raise ValueError("AndroidX source identity or cleanliness mismatch")
    if (git(libyuv, "rev-parse", "HEAD") != LIBYUV_COMMIT or
            git(libyuv, "rev-parse", "HEAD^{tree}") != LIBYUV_TREE or
            git(libyuv, "status", "--porcelain", "--", "files/source", "files/include")):
        raise ValueError("libyuv source identity or cleanliness mismatch")
    if (git(manifest, "rev-parse", "HEAD") != MANIFEST_COMMIT or
            git(manifest, "rev-parse", "HEAD^{tree}") != MANIFEST_TREE or
            git(manifest, "status", "--porcelain", "--", "default.xml")):
        raise ValueError("AndroidX release manifest identity or cleanliness mismatch")
    manifest_xml = ET.fromstring((manifest / "default.xml").read_bytes())
    projects = {project.get("path"): project.get("revision")
                for project in manifest_xml.findall("project")}
    if (projects.get("frameworks/support") != MANIFEST_SUPPORT_COMMIT or
            projects.get("external/libyuv") != LIBYUV_COMMIT):
        raise ValueError("release manifest does not pin the expected source pair")


def compile_one(source: Path, output: Path, compiler: Path, include: Path) -> None:
    subprocess.run([
        str(compiler), "-std=c++11", "-O2", "-fPIC", "-fno-exceptions", "-fno-rtti",
        "-ffunction-sections", "-fdata-sections", f"-I{include}", "-c", str(source),
        "-o", str(output),
    ], check=True)


def build_one(abi: str, support: Path, libyuv: Path, output: Path, ndk_bin: Path) -> None:
    compiler = ndk_bin / {
        "arm64-v8a": "aarch64-linux-android21-clang++",
        "x86_64": "x86_64-linux-android21-clang++",
    }[abi]
    libyuv_files = libyuv / "files"
    include = libyuv_files / "include"
    sources = sorted((libyuv_files / "source").glob("*.cc"))
    if len(sources) != 51 or len({source.stem for source in sources}) != 51:
        raise ValueError("libyuv CMake source glob changed")
    abi_dir = output / abi
    abi_dir.mkdir(parents=True, exist_ok=True)
    objects = [abi_dir / f"{source.stem}.o" for source in sources]
    with ThreadPoolExecutor(max_workers=min(8, os.cpu_count() or 1)) as pool:
        for result in as_completed([
            pool.submit(compile_one, source, obj, compiler, include)
            for source, obj in zip(sources, objects)
        ]):
            result.result()
    archive = abi_dir / "libyuv.a"
    subprocess.run([str(ndk_bin / "llvm-ar"), "rcs", str(archive),
                    *(str(obj) for obj in objects)], check=True)
    cpp = support / "camera/camera-core/src/main/cpp"
    library = abi_dir / "libimage_processing_util_jni.so"
    subprocess.run([
        str(compiler), "-std=c++17", "-O3", "-flto", "-fPIC", "-fno-exceptions",
        "-fno-rtti", "-fomit-frame-pointer", "-fdata-sections", "-ffunction-sections",
        "-shared", "-static-libstdc++", str(cpp / "image_processing_util_jni.cc"),
        f"-I{include}", str(archive), "-llog", "-landroid", "-ljnigraphics", "-lm",
        "-ldl", "-Wl,--gc-sections", "-Wl,--undefined-version",
        f"-Wl,--version-script={cpp / 'jni.lds'}", "-Wl,-z,relro", "-Wl,-z,now",
        "-Wl,-z,max-page-size=16384", "-Wl,-z,common-page-size=16384",
        "-Wl,-soname,libimage_processing_util_jni.so", "-o", str(library),
    ], check=True)
    actual = hashlib.sha256(library.read_bytes()).hexdigest()
    if actual != EXPECTED[abi]:
        raise ValueError(f"{abi} native output digest mismatch: {actual}")
    print(f"{abi} {actual}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("support_source", "libyuv_source", "manifest_source", "output"):
        parser.add_argument(name, type=Path)
    args = parser.parse_args()
    sdk = os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        raise ValueError("ANDROID_SDK_ROOT must name the SDK with NDK r28 beta2")
    ndk = Path(sdk) / "ndk/28.0.12674087"
    if "Pkg.Revision = 28.0.12674087-beta2" not in (ndk / "source.properties").read_text():
        raise ValueError("NDK revision mismatch")
    ndk_bin = ndk / "toolchains/llvm/prebuilt/darwin-x86_64/bin"
    if "clang version 19.0.0" not in command(str(ndk_bin / "aarch64-linux-android21-clang++"), "--version"):
        raise ValueError("NDK Clang revision mismatch")
    support, libyuv, manifest, output = [
        path.resolve() for path in (args.support_source, args.libyuv_source,
                                   args.manifest_source, args.output)
    ]
    if len({support, libyuv, manifest, output}) != 4:
        raise ValueError("source and output paths must be distinct")
    assert_source(support, libyuv, manifest)
    for abi in EXPECTED:
        build_one(abi, support, libyuv, output, ndk_bin)


if __name__ == "__main__":
    main()
