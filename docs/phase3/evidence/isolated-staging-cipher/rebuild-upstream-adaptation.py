"""Reproduce only the exact final unified diff; never rewrite production sources."""
import pathlib, difflib
evidence = pathlib.Path(__file__).resolve().parent
root = evidence.parents[3]
source = 'app/src/main/java/uk/co/traynor/privategallery/core/security/staging/'
result = []
for name in ['ChaCha20Poly1305', 'ChaCha7539Engine', 'ChaChaEngine', 'Salsa20Engine', 'Poly1305']:
    original = evidence / 'upstream-r1rv79' / (name + '.java')
    final = root / source / ('Staging' + name + '.java')
    result.extend(difflib.unified_diff(original.read_text().splitlines(keepends=True),
                                     final.read_text().splitlines(keepends=True),
                                     fromfile='upstream-r1rv79/' + name + '.java',
                                     tofile=source + 'Staging' + name + '.java'))
(evidence / 'upstream-adaptation.diff').write_text(''.join(result))
print('Exact pinned-original/final adaptation diff regenerated')
