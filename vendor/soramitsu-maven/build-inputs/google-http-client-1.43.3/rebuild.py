#!/usr/bin/env python3
"""Rebuild only SslUtils from verified Maven Central sources; retain all other payloads."""
import argparse
import hashlib
from pathlib import Path
import subprocess
import tempfile
import zipfile

VERSION = '1.43.3-sora.1'
HASHES = {
    'upstream.jar': '60aca7428c5a1ff3655b70541a98ff3d70dded48ac1324dae1af39f1b61914af',
    'upstream-sources.jar': '3d397d060e9ae0e8a71721ff836ed629ee66c81b9326e4f2a5aa892ae8c3a3f9',
    'upstream.pom': '54f7533dbbe7691797c623261ee7e26159a82f24cc205091756b2269f2b52deb',
}
SOURCE = 'com/google/api/client/util/SslUtils.java'
CLASS = SOURCE.replace('.java', '.class')
REMOVED = {CLASS.replace('.class', '$1.class'), CLASS.replace('.class', '$2.class')}


def replace_body(source, signature, body):
    start = source.index(signature) + len(signature)
    assert source[start] == '{'
    depth = 1
    end = start + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[:start] + '{\n    ' + body + '\n  }' + source[end:]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--javac', required=True)
    parser.add_argument('--annotations', type=Path, required=True)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    inputs = Path(__file__).resolve().parent
    for name, digest in HASHES.items():
        assert hashlib.sha256((inputs / name).read_bytes()).hexdigest() == digest, name
    output = args.output or inputs.parents[1] / 'com/google/http-client/google-http-client' / VERSION
    output.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(inputs / 'upstream-sources.jar') as jar:
        source = jar.read(SOURCE).decode()
    source = replace_body(source, 'public static SSLContext trustAllSSLContext() throws GeneralSecurityException ',
                          'throw new GeneralSecurityException("Certificate verification cannot be disabled");')
    source = replace_body(source, 'public static HostnameVerifier trustAllHostnameVerifier() ',
                          'throw new UnsupportedOperationException("Hostname verification cannot be disabled");')
    source = source.replace('public final class SslUtils {',
        '// SORA modification: insecure opt-out helpers fail closed; normal TLS is unchanged.\npublic final class SslUtils {')
    with tempfile.TemporaryDirectory(prefix='sora-http-tls-') as temporary:
        temp = Path(temporary)
        java = temp / SOURCE
        java.parent.mkdir(parents=True)
        java.write_text(source)
        subprocess.run([args.javac, '--release', '8', '-proc:none', '-g', '-classpath',
            str(inputs / 'upstream.jar') + ':' + str(args.annotations.resolve()), '-d', str(temp / 'classes'), str(java)], check=True)
        replacement = (temp / 'classes' / CLASS).read_bytes()
        for upstream, suffix, replacements, removed in [
            ('upstream.jar', '.jar', {CLASS: replacement}, REMOVED),
            ('upstream-sources.jar', '-sources.jar', {SOURCE: source.encode()}, set()),
        ]:
            with zipfile.ZipFile(inputs / upstream) as original, zipfile.ZipFile(output / ('google-http-client-' + VERSION + suffix), 'w') as result:
                for item in original.infolist():
                    if item.filename not in removed:
                        result.writestr(item, replacements.get(item.filename, original.read(item)))
    pom = (inputs / 'upstream.pom').read_text()
    pom = pom.replace('<artifactId>google-http-client</artifactId>\n  <version>1.43.3</version>',
        '<artifactId>google-http-client</artifactId>\n  <version>' + VERSION + '</version>', 1)
    (output / ('google-http-client-' + VERSION + '.pom')).write_text(pom)
    print(output)

if __name__ == '__main__':
    main()
