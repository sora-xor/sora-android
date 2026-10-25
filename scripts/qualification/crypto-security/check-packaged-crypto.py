#!/usr/bin/env python3
"""Bind final APK native bytes to the retained source-built AAR and inspect DEX types."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[3]
VERSION = '1.2.8-sora.2'
MODULE = ROOT / 'vendor/soramitsu-maven/jp/co/soramitsu/xcrypto' / VERSION


def digest(data):
    return hashlib.sha256(data).hexdigest()


def dex_types(data):
    if not data.startswith(b'dex\n') or len(data) < 112:
        raise ValueError('Invalid DEX header')
    string_count, string_offset, type_count, type_offset = struct.unpack_from('<IIII', data, 56)
    result = []
    for i in range(type_count):
        index = struct.unpack_from('<I', data, type_offset + i * 4)[0]
        if index >= string_count:
            raise ValueError('Invalid DEX type string index')
        offset = struct.unpack_from('<I', data, string_offset + index * 4)[0]
        for _ in range(5):
            byte = data[offset]
            offset += 1
            if byte < 128:
                break
        else:
            raise ValueError('Invalid DEX string length')
        end = data.index(b'\0', offset)
        result.append(data[offset:end].decode('utf-8', errors='replace'))
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    metadata = json.loads((MODULE / f'xcrypto-{VERSION}.module').read_text())
    for variant in metadata['variants']:
        for record in variant.get('files', []):
            data = (MODULE / record['name']).read_bytes()
            for algorithm in ('sha256', 'sha512', 'sha1', 'md5'):
                if algorithm in record and hashlib.new(algorithm, data).hexdigest() != record[algorithm]:
                    raise ValueError(f'Module metadata digest mismatch: {record["name"]}')
            if len(data) != record['size']:
                raise ValueError('Module metadata byte count mismatch')
    with zipfile.ZipFile(MODULE / f'xcrypto-{VERSION}.aar') as aar:
        native = {name: digest(aar.read(name)) for name in aar.namelist() if name.endswith('.so')}
        if len(native) != 4:
            raise ValueError('Expected all four native ABI libraries')
        with zipfile.ZipFile(io.BytesIO(aar.read('classes.jar'))) as classes:
            legacy = [name for name in classes.namelist() if name.endswith('.class') and b'net/i2p/crypto/eddsa' in classes.read(name)]
    with zipfile.ZipFile(args.apk) as apk:
        packaged = {name: digest(apk.read(name)) for name in apk.namelist() if name.endswith('/libsr25519java_1.so')}
        expected = {name.replace('jni/', 'lib/', 1): value for name, value in native.items()}
        if expected != packaged:
            raise ValueError('APK native bytes do not match source-built AAR')
        dex_names = [name for name in apk.namelist() if name.endswith('.dex')]
        if not dex_names:
            raise ValueError('Expected app DEX files')
        types = {t for name in dex_names for t in dex_types(apk.read(name))}
        forbidden = sorted(t for t in types if t.startswith(('Lnet/i2p/crypto/eddsa/', 'Lio/mockk/', 'Lorg/junit/jupiter/')))
        if forbidden or legacy:
            raise ValueError(f'Legacy crypto or removed test-only type references remain: {forbidden or legacy}')
    report = {'coordinate': f'jp.co.soramitsu:xcrypto:{VERSION}', 'moduleFileDigestsMatch': True,
              'eddsaBytecodeReferences': len(legacy), 'nativeLibrarySha256': native,
              'apk': {'path': str(args.apk), 'sha256': digest(args.apk.read_bytes()),
              'nativeLibrariesMatchSourceBuiltAar': True, 'nativeLibrarySha256': packaged,
              'dexFileCount': len(dex_names), 'legacyEddsaMockkAndJupiterTypeReferences': forbidden}}
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print('All four APK JNI libraries match the source build; no legacy EdDSA/MockK/Jupiter DEX types')


if __name__ == '__main__':
    main()
