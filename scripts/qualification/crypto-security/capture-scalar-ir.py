#!/usr/bin/env python3
"""Retain optimized scalar subtraction excerpts after the documented 8 rustc builds."""
import argparse
import hashlib
import json
from pathlib import Path
import re

parser = argparse.ArgumentParser()
parser.add_argument('target_directory', type=Path)
parser.add_argument('output_directory', type=Path)
args = parser.parse_args()
args.output_directory.mkdir(parents=True, exist_ok=True)
records = []
for target in ('armv7-linux-androideabi', 'aarch64-linux-android', 'i686-linux-android', 'x86_64-linux-android'):
    matches = {}
    for source in (args.target_directory / target / 'release/deps').glob('curve25519_dalek-*.ll'):
        ir = source.read_text()
        backend = 'u32' if 'Scalar293sub' in ir else 'u64'
        functions = []
        for definition in re.finditer(r'^define[^\n]+', ir, re.M):
            header = definition.group()
            selected = ('Scalar293sub' in header if backend == 'u32' else
                        ('6scalar' in header and 'arith3Sub3sub' in header))
            if selected:
                block = ir[definition.start():ir.index('\n}', definition.start()) + 2]
                if 'load volatile' in block:
                    functions.append(block)
        if len(functions) != 1:
            raise SystemExit(f'Expected one barrier-bearing subtraction function in {source}, found {len(functions)}')
        if backend in matches:
            raise SystemExit(f'Ambiguous stale IR files for {target}/{backend}')
        block = functions[0]
        branches = re.findall(r'^\s+(?:br|switch)\b[^\n]*', block, re.M)
        if branches:
            raise SystemExit(f'Branch requires manual analysis in {source}: {branches}')
        excerpt = args.output_directory / f'{target}-{backend}-sub.ll.txt'
        excerpt.write_text('; Selected optimized function excerpt; metadata definitions omitted.\n' + block + '\n')
        matches[backend] = {
            'target': target, 'backend': backend,
            'completeIrSha256': hashlib.sha256(source.read_bytes()).hexdigest(),
            'excerpt': excerpt.name,
            'excerptSha256': hashlib.sha256(excerpt.read_bytes()).hexdigest(),
            'volatileLoadCount': block.count('load volatile'),
            'branchInstructionCount': len(branches),
        }
    if set(matches) != {'u32', 'u64'}:
        raise SystemExit(f'Missing one backend for {target}')
    records.extend(matches.values())
report = {
    'scope': 'Rust 1.97.1 release optimization; scalar subtraction functions only; compiler-specific evidence, not a general constant-time proof',
    'records': records,
}
(args.output_directory / 'manifest.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps({'targetBackendPairs': len(records), 'branchesInExaminedFunctions': 0}))
