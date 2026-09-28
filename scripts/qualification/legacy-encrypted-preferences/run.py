#!/usr/bin/env python3
"""Build isolated old/current storage probes without changing production Gradle inventories.

Every packaged Maven artifact and its POM is retrieved from its primary publisher repository,
compared to cached bytes when present, and compared to frozen HEAD pins when present. The
result is local interoperability evidence, never dependency approval or release admission.
"""
import argparse, concurrent.futures, hashlib, json, os, pathlib, re, shutil, subprocess, tempfile, uuid, zipfile
import xml.etree.ElementTree as ET

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[2]
CACHE = pathlib.Path.home() / ".gradle/caches/modules-2/files-2.1"
SDK = pathlib.Path(os.environ.get("ANDROID_HOME", pathlib.Path.home() / "Library/Android/sdk"))
JAVA = pathlib.Path(os.environ.get("JAVA_HOME", "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home")) / "bin"
TOOLS = SDK / "build-tools/36.0.0"
ANDROID = SDK / "platforms/android-36/android.jar"

OLD = [
    "io.github.osipxd:security-crypto-datastore-preferences:1.0.0-beta01:aar",
    "io.github.osipxd:security-crypto-datastore:1.0.0-beta01:aar",
    "io.github.osipxd:encrypted-datastore-preferences:1.0.0-beta01:jar",
    "io.github.osipxd:encrypted-datastore:1.0.0-beta01:jar",
    "androidx.datastore:datastore-preferences:1.0.0:aar",
    "androidx.datastore:datastore:1.0.0:aar",
    "androidx.datastore:datastore-preferences-core:1.0.0:jar",
    "androidx.datastore:datastore-core:1.0.0:jar",
    "com.google.crypto.tink:tink-android:1.8.0:jar",
    "org.jetbrains.kotlin:kotlin-stdlib:1.9.24:jar",
    "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.24:jar",
    "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.24:jar",
    "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.1:jar",
    "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1:jar",
    "com.google.code.gson:gson:2.10.1:jar",
    "androidx.collection:collection:1.1.0:jar",
    "androidx.annotation:annotation:1.1.0:jar",
]
CURRENT = [
    "io.github.osipxd:security-crypto-datastore-preferences:1.1.1-beta03:aar",
    "io.github.osipxd:security-crypto-datastore:1.1.1-beta03:aar",
    "io.github.osipxd:encrypted-datastore-preferences:1.1.1-beta03:jar",
    "io.github.osipxd:encrypted-datastore:1.1.1-beta03:jar",
    "androidx.datastore:datastore-preferences-android:1.2.1:aar",
    "androidx.datastore:datastore-android:1.2.1:aar",
    "androidx.datastore:datastore-preferences-core-android:1.2.1:aar",
    "androidx.datastore:datastore-core-android:1.2.1:aar",
    "androidx.datastore:datastore-core-okio-jvm:1.2.1:jar",
    "androidx.datastore:datastore-preferences-proto:1.2.1:jar",
    "androidx.datastore:datastore-preferences-external-protobuf:1.2.1:jar",
    "com.squareup.okio:okio-jvm:3.9.1:jar",
    "com.google.crypto.tink:tink-android:1.13.0:jar",
    "org.jetbrains.kotlin:kotlin-stdlib:2.3.21:jar",
    "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.10:jar",
    "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.10:jar",
    "org.jetbrains.kotlin:kotlin-parcelize-runtime:2.3.21:jar",
    "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2:jar",
    "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2:jar",
    "com.google.code.gson:gson:2.13.2:jar",
    "androidx.collection:collection-jvm:1.5.0:jar",
    "androidx.annotation:annotation-jvm:1.9.1:jar",
    "com.google.errorprone:error_prone_annotations:2.41.0:jar",
]
SHARED = [
    "androidx.security:security-crypto:1.1.0-alpha06:aar",
    "org.jetbrains:annotations:23.0.0:jar",
    "com.google.code.findbugs:jsr305:3.0.2:jar",
]
TOOLCHAIN_FALLBACK_SHA256 = {
    ("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "1.9.24"): "e71ff19e6b141ab85a9328fd010941531a302543026bd4244c95adc208d501f6",
    ("org.jetbrains.kotlin", "kotlin-stdlib", "1.9.24"): "858b902696da9cf585ab9d98ffc1c2712269828354dfe9107e3711b084a36468",
    ("org.jetbrains.kotlin", "kotlin-script-runtime", "1.9.24"): "314c7d308fe750654365bac6144780613c9169cf3d9e1dafbab91cf80bdc357c",
}

def sha(data): return hashlib.sha256(data).hexdigest()
def invoke(args, log, **kwargs):
    log.write("$ " + " ".join(map(str, args)) + "\n"); log.flush()
    result = subprocess.run(list(map(str, args)), stdout=subprocess.PIPE, stderr=subprocess.STDOUT, **kwargs)
    output = result.stdout.decode(errors="replace")
    log.write(output); log.flush()
    if result.returncode: raise RuntimeError(f"COMMAND_FAILED:{args[0]}:{result.returncode}")
    return output

def frozen_pins():
    data = subprocess.check_output(["git", "show", "HEAD:gradle/verification-metadata.xml"], cwd=ROOT)
    tree = ET.fromstring(data)
    for element in tree.iter(): element.tag = element.tag.rsplit("}", 1)[-1]
    return {(':'.join(component.attrib[k] for k in ("group", "name", "version")), artifact.attrib['name']):
            {checksum.attrib['value'] for checksum in artifact.findall('sha256')}
            for component in tree.findall('.//component') for artifact in component.findall('artifact')}

def verify_artifact(spec, destination, pins):
    group, name, version, extension = spec.split(":")
    coordinate = f"{group}:{name}:{version}"
    base = "https://dl.google.com/dl/android/maven2/" if group.startswith("androidx.") else "https://repo.maven.apache.org/maven2/"
    prefix = f"{group.replace('.', '/')}/{name}/{version}/"
    folder = destination / group / name / version
    folder.mkdir(parents=True, exist_ok=True)
    cache = CACHE / group / name / version
    records = []
    binary = None
    for suffix in ("pom", extension):
        filename = f"{name}-{version}.{suffix}"
        path = folder / filename
        url = base + prefix + filename
        # A caller-supplied work directory never becomes an authority for unpinned bytes.
        subprocess.run(["curl", "--fail", "--silent", "--show-error", "--location", "--connect-timeout", "15", "--max-time", "90", url, "--output", str(path)], check=True)
        digest = sha(path.read_bytes())
        candidates = list(cache.glob(f"*/{filename}"))
        # Android multiplatform AAR cache names differ from their publisher URL.
        if not candidates and suffix == "aar":
            candidates = list(cache.glob("*/*.aar"))
        if candidates and any(sha(p.read_bytes()) != digest for p in candidates):
            raise RuntimeError(f"PUBLISHER_CACHE_MISMATCH:{coordinate}:{filename}")
        names = [filename] + [p.name for p in candidates]
        expected = set().union(*(pins.get((coordinate, n), set()) for n in names))
        if expected and digest not in expected:
            raise RuntimeError(f"FROZEN_PIN_MISMATCH:{coordinate}:{filename}")
        record = {"coordinate": coordinate, "artifact": filename, "publisherUrl": url, "sha256": digest,
                  "cachedBytesMatched": bool(candidates), "frozenHeadPinMatched": bool(expected)}
        if suffix == "pom":
            pom = ET.fromstring(path.read_bytes()); ns = {"m": "http://maven.apache.org/POM/4.0.0"}
            record["declaredDependencies"] = [{key: dep.findtext("m:" + key, "", ns) for key in ("groupId", "artifactId", "version", "scope", "optional")} for dep in pom.findall("m:dependencies/m:dependency", ns)]
        records.append(record)
        if suffix == extension: binary = path
    return spec, binary, records

def classes_jar(path, folder):
    if path.suffix == ".jar": return path
    target = folder / (path.parent.parent.name + "-" + path.parent.name + "-classes.jar")
    with zipfile.ZipFile(path) as archive: target.write_bytes(archive.read("classes.jar"))
    return target

def cached(group, name, version):
    choices = list((CACHE / group / name / version).glob(f"*/{name}-{version}.jar"))
    expected = TOOLCHAIN_FALLBACK_SHA256.get((group, name, version))
    if len(choices) == 1:
        if expected is not None and sha(choices[0].read_bytes()) != expected:
            raise RuntimeError("TOOLCHAIN_ARTIFACT_DIGEST_MISMATCH:" + name)
        return choices[0]
    if choices: raise RuntimeError("TOOLCHAIN_ARTIFACT_AMBIGUOUS:" + name)
    if expected is None: raise RuntimeError("TOOLCHAIN_ARTIFACT_MISSING:" + name)
    target = pathlib.Path(tempfile.gettempdir()) / "sora-storage-toolchain" / f"{name}-{version}.jar"
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.is_file() or sha(target.read_bytes()) != expected:
        url = f"https://repo.maven.apache.org/maven2/{group.replace('.', '/')}/{name}/{version}/{target.name}"
        subprocess.run(["curl", "--fail", "--silent", "--show-error", "--location", "--connect-timeout", "15", "--max-time", "90", url, "--output", str(target)], check=True)
    if sha(target.read_bytes()) != expected: raise RuntimeError("TOOLCHAIN_ARTIFACT_DIGEST_MISMATCH:" + name)
    return target

def build(stage, graph, artifacts, work, package, version, log):
    folder = work / stage; folder.mkdir(exist_ok=True)
    jars = [classes_jar(artifacts[s], folder) for s in graph + SHARED]
    classpath = os.pathsep.join(map(str, [ANDROID] + jars))
    compiler_version = "1.9.24" if graph == OLD else "2.3.21"
    compiler_jars = [cached("org.jetbrains.kotlin", "kotlin-compiler-embeddable", compiler_version),
                     cached("org.jetbrains.kotlin", "kotlin-stdlib", compiler_version),
                     cached("org.jetbrains.kotlin", "kotlin-script-runtime", compiler_version),
                     cached("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10"),
                     cached("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.0"),
                     cached("org.jetbrains", "annotations", "13.0")]
    if compiler_version == "1.9.24": compiler_jars.append(cached("org.jetbrains.intellij.deps", "trove4j", "1.0.20200330"))
    own = folder / "probe.jar"
    invoke([JAVA / "java", "-cp", os.pathsep.join(map(str, compiler_jars)), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            "-no-stdlib", "-no-reflect", "-jvm-target", "1.8", "-classpath", classpath, "-d", own, HERE / "StorageProbe.kt"], log)
    dex = folder / "dex"; dex.mkdir(exist_ok=True)
    invoke([TOOLS / "d8", "--min-api", "26", "--lib", ANDROID, "--output", dex, own] + jars, log,
           env={**os.environ, "JAVA_HOME": str(JAVA.parent)})
    manifest = folder / "AndroidManifest.xml"
    manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{package}" android:versionCode="{version}" android:versionName="interop-{stage}"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="34"/><application android:debuggable="true" android:allowBackup="false" android:label="Isolated storage interop"/><instrumentation android:name="jp.co.soramitsu.interop.StorageProbe" android:targetPackage="{package}" android:functionalTest="true"/></manifest>''')
    unsigned = folder / "unsigned.apk"
    invoke([TOOLS / "aapt2", "link", "-I", ANDROID, "--manifest", manifest, "-o", unsigned], log)
    with zipfile.ZipFile(unsigned, 'a') as archive:
        for path in sorted(dex.glob("*.dex")): archive.write(path, path.name)
    aligned = folder / "aligned.apk"
    invoke([TOOLS / "zipalign", "-f", "4", unsigned, aligned], log)
    apk = folder / "probe.apk"
    invoke([TOOLS / "apksigner", "sign", "--ks", work / "test-only.p12", "--ks-pass", "pass:isolated-fixture", "--out", apk, aligned], log,
           env={**os.environ, "JAVA_HOME": str(JAVA.parent)})
    signer = invoke([TOOLS / "apksigner", "verify", "--print-certs", apk], log,
                    env={**os.environ, "JAVA_HOME": str(JAVA.parent)})
    return {"stage": stage, "path": str(apk), "sha256": sha(apk.read_bytes()), "versionCode": version,
            "signerSha256": re.search(r"certificate SHA-256 digest: ([0-9a-f]+)", signer).group(1),
            "runtimeArtifacts": graph + SHARED,
            "compilerArtifacts": [{"path": str(p), "sha256": sha(p.read_bytes())} for p in compiler_jars]}

def main():
    parser = argparse.ArgumentParser(); parser.add_argument("--work", type=pathlib.Path); parser.add_argument("--serial", default="emulator-5554")
    args = parser.parse_args()
    work = args.work or pathlib.Path(tempfile.mkdtemp(prefix="sora-storage-interop-"))
    work.mkdir(parents=True, exist_ok=True)
    if not (work / "package.txt").exists(): (work / "package.txt").write_text("jp.co.soramitsu.interop.p" + uuid.uuid4().hex)
    package = (work / "package.txt").read_text().strip()
    assert re.fullmatch(r"jp\.co\.soramitsu\.interop\.p[0-9a-f]{32}", package)
    print("Evidence directory:", work, flush=True)
    baseline_tag = "release/sora/3.8.6.3"
    baseline_commit = subprocess.check_output(["git", "rev-parse", baseline_tag], cwd=ROOT).decode().strip()
    if baseline_commit != "c79ad6c7c64ad92e756023ff51d9e2a7040da375": raise RuntimeError("RETAINED_TAG_IDENTITY_CHANGED")
    versions = subprocess.check_output(["git", "show", baseline_tag + ":gradle/libs.versions.toml"], cwd=ROOT)
    for required in (b'kotlin = "1.9.24"', b'coroutines = "1.8.1"', b'datastore = "1.0.0"', b'gson = "2.10.1"'):
        if required not in versions: raise RuntimeError("RETAINED_DECLARED_VERSION_CHANGED")
    lock = (ROOT / "app/gradle.lockfile").read_text()
    for spec in CURRENT + SHARED:
        coordinate = spec.rsplit(":", 1)[0]
        if not any(line.startswith(coordinate + "=") and "productionReleaseRuntimeClasspath" in line for line in lock.splitlines()):
            raise RuntimeError("CURRENT_STORAGE_GRAPH_NOT_IN_RUNTIME_LOCK:" + coordinate)
    frozen_metadata = subprocess.check_output(["git", "show", "HEAD:gradle/verification-metadata.xml"], cwd=ROOT)
    source_evidence = {
        "retainedTag": baseline_tag, "retainedCommit": baseline_commit,
        "retainedVersionCatalogSha256": sha(versions), "currentAppLockSha256": sha(lock.encode()),
        "frozenHeadVerificationMetadataSha256": sha(frozen_metadata),
        "sources": {p.name: sha(p.read_bytes()) for p in HERE.iterdir() if p.is_file()},
        "oldGraph": OLD + SHARED, "currentGraph": CURRENT + SHARED,
        "scope": "Storage dependency closure with retained catalog overrides; not a reproduction of the entire released APK",
    }
    (work / "source-evidence.json").write_text(json.dumps(source_evidence, indent=2) + "\n")
    pins = frozen_pins(); specs = sorted(set(OLD + CURRENT + SHARED)); artifacts = {}; provenance = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        for spec, binary, records in pool.map(lambda spec: verify_artifact(spec, work / "publisher-artifacts", pins), specs):
            artifacts[spec] = binary; provenance.extend(records)
    (work / "publisher-evidence.json").write_text(json.dumps(provenance, indent=2) + "\n")
    print("Publisher-verified runtime artifacts:", len(artifacts), flush=True)
    with (work / "commands.log").open("a") as log:
        adb = [SDK / "platform-tools/adb", "-s", args.serial]
        device = {key: invoke(adb + ["shell", "getprop", key], log).strip() for key in
                  ("ro.build.version.sdk", "ro.build.version.release", "ro.product.cpu.abi", "ro.build.fingerprint")}
        (work / "device.json").write_text(json.dumps(device, indent=2) + "\n")
        packages = invoke(adb + ["shell", "pm", "list", "packages", package], log)
        if f"package:{package}" in packages: raise RuntimeError("TEST_PACKAGE_ALREADY_EXISTS")
        if not (work / "test-only.p12").exists():
            invoke([JAVA / "keytool", "-genkeypair", "-keystore", work / "test-only.p12", "-storepass", "isolated-fixture", "-keypass", "isolated-fixture", "-alias", "interop", "-keyalg", "RSA", "-keysize", "2048", "-validity", "30", "-dname", "CN=Isolated storage interoperability test"], log)
        builds = [build("old-writer", OLD, artifacts, work, package, 1, log),
                  build("current-reader", CURRENT, artifacts, work, package, 2, log),
                  build("old-rollback-reader", OLD, artifacts, work, package, 3, log)]
        assert len({b['signerSha256'] for b in builds}) == 1
        (work / "apks.json").write_text(json.dumps(builds, indent=2) + "\n")
        results = []
        for index, (apk, phases) in enumerate(zip(builds, [["old-write"], ["current-update", "current-reopen"], ["old-rollback-read"]])):
            invoke(adb + ["install"] + (["-r"] if index else []) + [apk['path']], log)
            for phase in phases:
                invoke(adb + ["shell", "am", "force-stop", package], log)
                output = invoke(adb + ["shell", "am", "instrument", "-w", "-r", "-e", "phase", phase, package + "/jp.co.soramitsu.interop.StorageProbe"], log)
                (work / (phase + ".log")).write_text(output)
                passed = "INSTRUMENTATION_RESULT: result=PASS" in output and "INSTRUMENTATION_CODE: 0" in output
                fields = dict(re.findall(r"^INSTRUMENTATION_RESULT: (\w+)=(.*)$", output, re.MULTILINE))
                installed = {key: fields.get(key) for key in ("uid", "installedVersionCode", "installedSignerSha256")}
                passed = passed and installed["installedVersionCode"] == str(apk["versionCode"]) and installed["installedSignerSha256"] == apk["signerSha256"]
                if results: passed = passed and installed["uid"] == results[0]["uid"]
                results.append({"phase": phase, "passed": passed, **installed})
                (work / "results.json").write_text(json.dumps({"qualification": "local-isolated-storage-interop-only", "package": package, "serial": args.serial, "results": results}, indent=2) + "\n")
                if not passed: raise RuntimeError("INTEROPERABILITY_FAILED:" + phase)
        print("PASS: real old write, current read/update/reopen, old rollback read", flush=True)

if __name__ == "__main__": main()
