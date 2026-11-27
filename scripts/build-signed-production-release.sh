#!/usr/bin/env bash
# Builds the Play production bundle and APK so that no build tool ever holds the upload key.
#
#   SORA_VERSION_CODE=3090202 scripts/build-signed-production-release.sh [options]
#
#   --commit REV    build this commit (default HEAD); only committed files are used
#   --out DIR       where the signed artifacts and the receipt go
#                   (default build/release/<versionCode>-<short commit> in this checkout)
#   --reproduce     build twice, each time in a fresh directory with a fresh Gradle home, and
#                   fail unless both builds produce the same bundle and APK contents
#   --no-sandbox    run Gradle without the macOS sandbox (only where sandbox-exec is unavailable)
#   --keep-work     keep the build directories (several GB) instead of deleting them
#
# What it does, and why (audit 2026-11-07, finding H3):
#  1. Exports the commit with `git archive` into a new directory: no uncommitted or untracked
#     file and no earlier build output can reach the release.
#  2. Runs Gradle there with an empty Gradle home of its own, --no-build-cache,
#     --no-configuration-cache, --no-daemon and a cleared environment. No cached task output,
#     artifact transform or daemon from any other build is reused; every dependency is
#     downloaded again and checked against gradle/verification-metadata.xml.
#  3. Gradle signs only with a throwaway key made for this build. It never receives the upload
#     key, its password or a Play credential. On macOS it runs under sandbox-exec and cannot read
#     or write files in the home directory, apart from the build directory, the Android SDK and
#     the JDK, so a build plugin cannot read the upload key, SSH keys or tokens either.
#  4. After Gradle has exited, the throwaway signature is removed and the bundle is signed with
#     the upload key by jarsigner, the APK by apksigner. Passwords are read from a file, never
#     from the command line or the environment.
#  5. The existing checks run on the signed files: upload-certificate pin, manifest policy,
#     16 KB native alignment and the JSON-export contract. A receipt records the commit, the
#     checksums of the unsigned and signed files and the tool versions.
#
# Inputs (environment):
#   SORA_VERSION_CODE           required; the build enforces its range
#   JAVA_HOME                   JDK 17 (default: Homebrew openjdk@17)
#   ANDROID_HOME                Android SDK with platform 37 and build-tools 36.0.0
#                               (default: ~/Library/Android/sdk)
#   SORA_UPLOAD_KEYSTORE        default ~/.sora-release/sora-upload.jks (PKCS12)
#   SORA_UPLOAD_KEY_ALIAS       default sora-upload
#   SORA_UPLOAD_STOREPASS_FILE  file holding the keystore password
#                               (default: .storepass next to the keystore)
#   SORA_UPLOAD_KEYPASS_FILE    file holding the key password (default: the storepass file)
#   SORA_SIGNING_PIN_FILE       public certificate pins (default: the commit's
#                               config/android-production-signing-identity.json); only for
#                               test runs with a test key
#   SORA_RELEASE_WORK           parent of the build directories (default: $TMPDIR)
set -euo pipefail

usage() {
    sed -n '2,24p' "$0" | sed 's/^# \{0,1\}//'
    exit "${1:-0}"
}

ROOT=$(cd "$(dirname "$0")/.." && pwd -P)
COMMIT=HEAD
OUT=""
REPRODUCE=0
SANDBOX=1
KEEP_WORK=0
while [ $# -gt 0 ]; do
    case "$1" in
        --commit) COMMIT=${2:?--commit needs a revision}; shift 2 ;;
        --out) OUT=${2:?--out needs a directory}; shift 2 ;;
        --reproduce) REPRODUCE=1; shift ;;
        --no-sandbox) SANDBOX=0; shift ;;
        --keep-work) KEEP_WORK=1; shift ;;
        -h|--help) usage 0 ;;
        *) echo "unknown option: $1" >&2; usage 2 ;;
    esac
done

fail() { echo "build-signed-production-release: $*" >&2; exit 1; }
log() { echo "==> $*" >&2; }

: "${SORA_VERSION_CODE:?set SORA_VERSION_CODE to the Play versionCode of this release}"
[[ "$SORA_VERSION_CODE" =~ ^[1-9][0-9]{0,8}$ ]] || fail "SORA_VERSION_CODE must be a positive number"

JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}
ANDROID_HOME=${ANDROID_HOME:-$HOME/Library/Android/sdk}
[ -x "$JAVA_HOME/bin/java" ] || fail "JAVA_HOME ($JAVA_HOME) has no bin/java"
"$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "17' || fail "JAVA_HOME must be a JDK 17"
JAVA_HOME=$(cd "$JAVA_HOME" && pwd -P)
ANDROID_HOME=$(cd "$ANDROID_HOME" && pwd -P)
APKSIGNER="$ANDROID_HOME/build-tools/36.0.0/apksigner"
[ -x "$APKSIGNER" ] || fail "build-tools 36.0.0 is missing ($APKSIGNER)"

KEYSTORE=${SORA_UPLOAD_KEYSTORE:-$HOME/.sora-release/sora-upload.jks}
KEY_ALIAS=${SORA_UPLOAD_KEY_ALIAS:-sora-upload}
STOREPASS_FILE=${SORA_UPLOAD_STOREPASS_FILE:-$(dirname "$KEYSTORE")/.storepass}
KEYPASS_FILE=${SORA_UPLOAD_KEYPASS_FILE:-$STOREPASS_FILE}
for f in "$KEYSTORE" "$STOREPASS_FILE" "$KEYPASS_FILE"; do
    [ -f "$f" ] || fail "missing signing input: $f"
done
KEY_DIR=$(cd "$(dirname "$KEYSTORE")" && pwd -P)
PASS_DIR=$(cd "$(dirname "$STOREPASS_FILE")" && pwd -P)

REV=$(git -C "$ROOT" rev-parse --verify "${COMMIT}^{commit}") || fail "unknown commit $COMMIT"
SHORT=${REV:0:12}
OUT=${OUT:-$ROOT/build/release/$SORA_VERSION_CODE-$SHORT}
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd -P)
[ -z "$(ls -A "$OUT")" ] || fail "output directory is not empty: $OUT"

if [ "$SANDBOX" = 1 ]; then
    command -v sandbox-exec >/dev/null || fail "sandbox-exec not found; pass --no-sandbox to build without it"
fi

WORK_PARENT=${SORA_RELEASE_WORK:-${TMPDIR:-/tmp}}
WORK_ROOT=$(mktemp -d "$WORK_PARENT/sora-release-$SORA_VERSION_CODE-$SHORT.XXXXXX")
WORK_ROOT=$(cd "$WORK_ROOT" && pwd -P)
cleanup() {
    if [ "$KEEP_WORK" = 0 ]; then
        chmod -R u+w "$WORK_ROOT" 2>/dev/null || true
        rm -rf "$WORK_ROOT"
    else
        log "build directories kept in $WORK_ROOT"
    fi
}
trap cleanup EXIT

# Zip helpers. Run isolated (-I) so nothing in the exported tree is imported.
zip_tool() {
    python3 -I - "$@" <<'PY'
import hashlib, json, re, sys, zipfile

SIGNATURE = re.compile(r"^META-INF/([^/]+\.(SF|RSA|DSA|EC)|MANIFEST\.MF)$", re.IGNORECASE)

def strip(src, dst):
    """Copy a JAR-signed zip without its signature files, keeping every other entry as is."""
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w") as zout:
        removed = 0
        for info in zin.infolist():
            if SIGNATURE.match(info.filename):
                removed += 1
                continue
            zout.writestr(info, zin.read(info.filename), compress_type=info.compress_type)
        if removed == 0:
            sys.exit(f"{src}: no JAR signature to remove")

def contents(path):
    """Entry name -> SHA-256 of its data, signature files left out."""
    with zipfile.ZipFile(path) as z:
        return {
            i.filename: hashlib.sha256(z.read(i.filename)).hexdigest()
            for i in z.infolist()
            if not SIGNATURE.match(i.filename)
        }

def compare(a, b):
    left, right = contents(a), contents(b)
    differ = sorted(n for n in left.keys() | right.keys() if left.get(n) != right.get(n))
    if differ:
        print(json.dumps({"differing_entries": differ[:50], "count": len(differ)}, indent=1))
        sys.exit(1)

command = sys.argv[1]
if command == "strip":
    strip(sys.argv[2], sys.argv[3])
elif command == "compare":
    compare(sys.argv[2], sys.argv[3])
else:
    sys.exit(f"unknown command {command}")
PY
}

sha256() { shasum -a 256 "$1" | cut -d' ' -f1; }

# One complete Gradle build of the export in its own directory and Gradle home. Prints the
# directory that holds the throwaway-signed bundle, APK and mapping.
gradle_build() {
    local name=$1 work="$WORK_ROOT/$1"
    mkdir -p "$work/src" "$work/gradle-home" "$work/android-user-home" "$work/tmp" "$work/throwaway"
    git -C "$ROOT" archive "$REV" | tar -x -C "$work/src"

    # A key that exists only for this build, so Gradle's own signing step has something to sign
    # with. Its signature is removed before the upload key signs.
    local throwaway_pass
    throwaway_pass=$(openssl rand -hex 24)
    "$JAVA_HOME/bin/keytool" -J-Duser.language=en -genkeypair -noprompt \
        -keystore "$work/throwaway/throwaway.p12" -storetype PKCS12 \
        -storepass "$throwaway_pass" -keypass "$throwaway_pass" -alias throwaway \
        -keyalg RSA -keysize 2048 -validity 2 -dname "CN=SORA throwaway build key" >/dev/null 2>&1
    chmod 600 "$work/throwaway/throwaway.p12"

    local -a run=()
    if [ "$SANDBOX" = 1 ]; then
        # Last matching rule wins: the home directory is closed, then the build directory, the
        # SDK and the JDK are opened again, and the key directories are closed in any case.
        # Metadata (stat, not contents or directory listings) stays readable under the home
        # directory: resolving a path to the SDK, as javac and ndk-build do, stats each parent.
        local profile
        profile="(version 1)(allow default)"
        profile+="(deny file-read* file-write* (subpath \"$HOME\"))"
        profile+="(allow file-read-metadata (subpath \"$HOME\"))"
        profile+="(allow file-read* file-write* (subpath \"$work\"))"
        profile+="(allow file-read* (subpath \"$ANDROID_HOME\"))"
        profile+="(allow file-read* (subpath \"$JAVA_HOME\"))"
        profile+="(deny file-read* file-write* (subpath \"$KEY_DIR\"))"
        profile+="(deny file-read* file-write* (subpath \"$PASS_DIR\"))"
        run=(sandbox-exec -p "$profile")
    fi

    log "[$name] gradle build of $SHORT (fresh Gradle home, no build cache, no daemon)"
    (
        cd "$work/src"
        env -i \
            HOME="$HOME" USER="${USER:-}" LANG=en_US.UTF-8 \
            PATH="$JAVA_HOME/bin:/usr/bin:/bin:/usr/sbin:/sbin" \
            JAVA_HOME="$JAVA_HOME" ANDROID_HOME="$ANDROID_HOME" ANDROID_SDK_ROOT="$ANDROID_HOME" \
            ANDROID_USER_HOME="$work/android-user-home" GRADLE_USER_HOME="$work/gradle-home" \
            TMPDIR="$work/tmp" \
            CI_KEYSTORE_PATH="$work/throwaway/throwaway.p12" CI_KEYSTORE_PASS="$throwaway_pass" \
            CI_KEYSTORE_KEY_ALIAS=throwaway CI_KEYSTORE_KEY_PASS="$throwaway_pass" \
            SORA_VERSION_CODE="$SORA_VERSION_CODE" \
            ${run[@]+"${run[@]}"} ./gradlew --no-daemon --no-build-cache --no-configuration-cache \
                --dependency-verification=strict --console=plain \
                -Porg.gradle.java.installations.paths="$JAVA_HOME" \
                -Porg.gradle.java.installations.auto-detect=false \
                -Porg.gradle.java.installations.auto-download=false \
                -Pkotlin.compiler.execution.strategy=in-process \
                :app:bundleProductionRelease :app:assembleProductionRelease
    ) > "$work/gradle.log" 2>&1 || {
        tail -40 "$work/gradle.log" >&2
        fail "[$name] gradle build failed; full log: $work/gradle.log (use --keep-work to keep it)"
    }
    cp "$work/gradle.log" "$OUT/gradle-$name.log"

    local outputs="$work/src/app/build/outputs"
    mkdir -p "$work/artifacts"
    cp "$outputs/bundle/productionRelease/app-production-release.aab" "$work/artifacts/throwaway-signed.aab"
    cp "$outputs/apk/production/release/app-production-release.apk" "$work/artifacts/throwaway-signed.apk"
    cp "$outputs/mapping/productionRelease/mapping.txt" "$work/artifacts/mapping.txt"
    zip_tool strip "$work/artifacts/throwaway-signed.aab" "$work/artifacts/unsigned.aab"
    echo "$work"
}

FIRST=$(gradle_build build-1)
if [ "$REPRODUCE" = 1 ]; then
    SECOND=$(gradle_build build-2)
    log "comparing the two builds"
    zip_tool compare "$FIRST/artifacts/unsigned.aab" "$SECOND/artifacts/unsigned.aab" \
        || fail "the two bundles differ: the build is not reproducible or an input changed"
    zip_tool compare "$FIRST/artifacts/throwaway-signed.apk" "$SECOND/artifacts/throwaway-signed.apk" \
        || fail "the two APKs differ: the build is not reproducible or an input changed"
    cmp -s "$FIRST/artifacts/mapping.txt" "$SECOND/artifacts/mapping.txt" \
        || fail "the two R8 mappings differ"
fi

log "signing with the upload key (alias $KEY_ALIAS) outside Gradle"
cp "$FIRST/artifacts/mapping.txt" "$OUT/mapping.txt"
cp "$FIRST/artifacts/unsigned.aab" "$OUT/app-production-release-unsigned.aab"
"$JAVA_HOME/bin/jarsigner" -J-Duser.language=en \
    -keystore "$KEYSTORE" -storetype PKCS12 \
    -storepass:file "$STOREPASS_FILE" -keypass:file "$KEYPASS_FILE" \
    -sigalg SHA256withRSA -digestalg SHA-256 \
    -signedjar "$OUT/app-production-release.aab" \
    "$OUT/app-production-release-unsigned.aab" "$KEY_ALIAS" >/dev/null
# apksigner reads a password file given twice as two lines, so --key-pass is passed only when
# the key password has a file of its own; otherwise it uses the keystore password.
KEY_PASS_ARGS=()
if [ "$KEYPASS_FILE" != "$STOREPASS_FILE" ]; then
    KEY_PASS_ARGS=(--key-pass "file:$KEYPASS_FILE")
fi
JAVA_HOME="$JAVA_HOME" "$APKSIGNER" sign \
    --ks "$KEYSTORE" --ks-type PKCS12 --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "file:$STOREPASS_FILE" ${KEY_PASS_ARGS[@]+"${KEY_PASS_ARGS[@]}"} \
    --alignment-preserved true \
    --out "$OUT/app-production-release.apk" "$FIRST/artifacts/throwaway-signed.apk"
rm -f "$OUT/app-production-release.apk.idsig"

log "checking the signed artifacts"
SRC="$FIRST/src"
PIN_FILE=${SORA_SIGNING_PIN_FILE:-$SRC/config/android-production-signing-identity.json}
VERSION_NAME=$(sed -n 's/^val appVersionName = "\([0-9.]*\)"$/\1/p' "$SRC/app/build.gradle.kts")
[ -n "$VERSION_NAME" ] || fail "cannot read appVersionName from app/build.gradle.kts"
AAB="$OUT/app-production-release.aab"
APK="$OUT/app-production-release.apk"
(
    cd "$SRC"
    export JAVA_HOME ANDROID_HOME APKSIGNER
    python3 -I scripts/verify-release-signing.py --pin-file "$PIN_FILE" "$AAB" "$APK"
    python3 -I scripts/verify-release-manifest.py \
        --policy config/android-nexus-manifest-policy.json \
        --expect-version-code "$SORA_VERSION_CODE" --expect-version-name "$VERSION_NAME" \
        "$AAB" "$APK"
    python3 -I scripts/verify-android-native-16kb.py "$AAB"
    python3 -I scripts/verify-android-native-16kb.py "$APK"
    python3 -I scripts/verify-release-json-export.py "$AAB" "$APK"
) >&2 || fail "a release check failed"

cat > "$OUT/receipt.json" <<JSON
{
  "commit": "$REV",
  "versionCode": $SORA_VERSION_CODE,
  "versionName": "$VERSION_NAME",
  "reproducedInSecondBuild": $([ "$REPRODUCE" = 1 ] && echo true || echo false),
  "gradleSandboxed": $([ "$SANDBOX" = 1 ] && echo true || echo false),
  "unsignedBundleSha256": "$(sha256 "$OUT/app-production-release-unsigned.aab")",
  "bundleSha256": "$(sha256 "$AAB")",
  "apkSha256": "$(sha256 "$APK")",
  "mappingSha256": "$(sha256 "$OUT/mapping.txt")",
  "signingCertificateSha256": "$("$JAVA_HOME/bin/keytool" -J-Duser.language=en -printcert -jarfile "$AAB" | sed -n 's/^[[:space:]]*SHA256: //p' | head -1 | tr -d ':' | tr 'A-F' 'a-f')",
  "java": "$("$JAVA_HOME/bin/java" -version 2>&1 | head -1 | tr -d '"')",
  "builtAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
JSON
log "done: $OUT"
ls -l "$OUT" >&2
