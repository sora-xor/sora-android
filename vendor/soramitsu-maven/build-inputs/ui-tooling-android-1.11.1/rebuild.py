#!/usr/bin/env python3
"""Rebuild PreviewActivity with Material2; preserve every unrelated tooling byte."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

VERSION = '1.11.1-sora.1'
SOURCE = 'androidMain/androidx/compose/ui/tooling/PreviewActivity.android.kt'
FAMILY = ('androidx/compose/ui/tooling/PreviewActivity',
          'androidx/compose/ui/tooling/ComposableSingletons$PreviewActivity_androidKt')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def rewrite_zip(original, replacements, remove=()):
    result = io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(original)) as source, zipfile.ZipFile(result, 'w') as target:
        for entry in source.infolist():
            if entry.filename not in remove:
                target.writestr(entry, replacements.pop(entry.filename, source.read(entry)))
        for name, data in sorted(replacements.items()):
            entry = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            target.writestr(entry, data)
    return result.getvalue()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--java', required=True)
    parser.add_argument('--android-jar', type=Path, required=True)
    parser.add_argument('--gradle-cache', type=Path, default=Path.home() / '.gradle/caches/modules-2/files-2.1')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    inputs = Path(__file__).resolve().parent
    manifest = json.loads((inputs / 'inputs.json').read_text())
    for name, digest in manifest['upstream'].items():
        assert sha((inputs / name).read_bytes()) == digest, name
    assert sha(args.android_jar.read_bytes()) == manifest['androidJarSha256'], 'Use the pinned Android 37.0 SDK jar'
    output = args.output or inputs.parents[1] / 'androidx/compose/ui/ui-tooling-android' / VERSION
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='sora-preview-') as directory:
        temp = Path(directory)

        def resolve(record):
            coordinate = record['coordinate']
            location = args.gradle_cache.joinpath(*coordinate.split(':'))
            for candidate in sorted(location.rglob('*')):
                if not candidate.is_file() or candidate.suffix not in ('.aar', '.jar'):
                    continue
                if record.get('filename') and candidate.name != record['filename']:
                    continue
                data = candidate.read_bytes()
                if candidate.suffix == '.aar':
                    with zipfile.ZipFile(candidate) as aar:
                        if 'classes.jar' not in aar.namelist():
                            continue
                        data = aar.read('classes.jar')
                if sha(data) == record['sha256']:
                    target = temp / (coordinate.replace(':', '-') + '.jar')
                    target.write_bytes(data)
                    return target
            raise ValueError('Missing pinned build input: ' + coordinate)

        compiler = [resolve(r) for r in manifest['compiler']]
        plugin = resolve(manifest['plugin'])
        libraries = [resolve(r) for r in manifest['libraries']]
        original_aar = (inputs / 'upstream.aar').read_bytes()
        with zipfile.ZipFile(io.BytesIO(original_aar)) as aar:
            original_jar = aar.read('classes.jar')
        friend = next(path for path in libraries if path.name == 'androidx.compose.ui-ui-tooling-android-1.11.1.jar')
        assert friend.read_bytes() == original_jar
        with zipfile.ZipFile(inputs / 'upstream-sources.jar') as jar:
            original_source = jar.read(SOURCE).decode()
        assert original_source.count('androidx.compose.material3.') == 3
        source = original_source.replace('androidx.compose.material3.', 'androidx.compose.material.')
        assert source.count('content = { Text("Next") }') == 1
        source = source.replace('content = { Text("Next") }', 'text = { Text("Next") }')
        source = source.replace('class PreviewActivity : ComponentActivity() {',
            '// SORA: keep preview controls in the same Material2 theme as the previewed app.\nclass PreviewActivity : ComponentActivity() {')
        kotlin = temp / 'PreviewActivity.android.kt'
        kotlin.write_text(source)
        classes = temp / 'classes'
        subprocess.run([args.java, '-cp', os.pathsep.join(map(str, compiler)),
            'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
            '-jvm-target', '11', '-Xplugin=' + str(plugin), '-Xfriend-paths=' + str(friend),
            '-classpath', os.pathsep.join(map(str, libraries + [args.android_jar])),
            '-d', str(classes), str(kotlin)], check=True)
        replacements = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
        assert replacements and all(name.startswith(FAMILY) for name in replacements)
        with zipfile.ZipFile(io.BytesIO(original_jar)) as jar:
            removed = {name for name in jar.namelist() if name.startswith(FAMILY)} - replacements.keys()
        modified_jar = rewrite_zip(original_jar, replacements, removed)
        modified_aar = rewrite_zip(original_aar, {'classes.jar': modified_jar})
        stem = 'ui-tooling-android-' + VERSION
        (output / (stem + '.aar')).write_bytes(modified_aar)
        (output / (stem + '-sources.jar')).write_bytes(rewrite_zip(
            (inputs / 'upstream-sources.jar').read_bytes(), {SOURCE: source.encode()}))
        pom = (inputs / 'upstream.pom').read_text().replace(
            '<artifactId>ui-tooling-android</artifactId>\n  <version>1.11.1</version>',
            '<artifactId>ui-tooling-android</artifactId>\n  <version>' + VERSION + '</version>', 1)
        pom = pom.replace('<groupId>androidx.compose.material3</groupId>\n      <artifactId>material3-android</artifactId>\n      <version>1.3.1</version>',
            '<groupId>androidx.compose.material</groupId>\n      <artifactId>material-android</artifactId>\n      <version>1.11.1</version>')
        (output / (stem + '.pom')).write_text(pom)
        module = json.loads((inputs / 'upstream.module').read_text())
        module['component'] = {'group': 'androidx.compose.ui', 'module': 'ui-tooling-android', 'version': VERSION,
                               'attributes': {'org.gradle.status': 'release'}}
        for variant in module['variants']:
            for dependency in variant.get('dependencies', []):
                if dependency['group'] == 'androidx.compose.material3':
                    dependency.update(group='androidx.compose.material', module='material', version={'requires': '1.11.1'})
            # Samples remain available in the upstream source release; this fork ships the patched library source.
            suffix = '-sources.jar' if variant['attributes'].get('org.gradle.category') == 'documentation' else '.aar'
            artifact = output / (stem + suffix)
            data = artifact.read_bytes()
            variant['files'] = [{'name': artifact.name, 'url': artifact.name, 'size': len(data),
                                 **{algorithm: hashlib.new(algorithm, data).hexdigest() for algorithm in ('sha512', 'sha256', 'sha1', 'md5')}}]
        (output / (stem + '.module')).write_text(json.dumps(module, indent=2) + '\n')
    print(output)


if __name__ == '__main__':
    main()
