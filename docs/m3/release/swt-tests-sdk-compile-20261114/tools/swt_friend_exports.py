# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Adds friend-only exports to the org.eclipse.swt host MANIFEST: SWT's own test bundle may use these fork packages, nobody
# else gets them as API (x-friends: PDE/Tycho give friends access, everyone else a discouraged-access error/warning).
# The packages come from the ECJ "Access restriction" rows of org.eclipse.swt.tests in the G6 SDK build (2026-11-10).
# Entries go into the Export-Package clause in package-name order; CR bytes and the 72-byte layout of the file are kept.
# Usage: python swt_friend_exports.py <MANIFEST.MF>   (fixed point: an existing entry is left as it is)
import sys
FRIEND = 'x-friends:="org.eclipse.swt.tests"'
PACKAGES = ['org.eclipse.swt.internal.canvasext', 'org.eclipse.swt.internal.gpu', 'org.eclipse.swt.internal.m3.collections',
            'org.eclipse.swt.internal.render', 'org.eclipse.swt.m3']
p = sys.argv[1]
raw = open(p, 'rb').read()
nl = b'\r\n' if b'\r\n' in raw else b'\n'
lines = raw.split(nl)
start = next(i for i, l in enumerate(lines) if l.startswith(b'Export-Package:'))
end = start + 1
while end < len(lines) and lines[end].startswith(b' '):
    end += 1
entries = [l[1:] for l in lines[start + 1:end]]          # ' org.eclipse.swt,' -> 'org.eclipse.swt,'
names = [e.split(b';')[0].rstrip(b',') for e in entries]
added = 0
for pkg in PACKAGES:
    b = pkg.encode()
    if b in names:
        continue
    pos = next((i for i, n in enumerate(names) if n > b), len(names))
    entries.insert(pos, b + b';' + FRIEND.encode() + b',')
    names.insert(pos, b)
    added += 1
# the last entry carries no trailing comma, every other entry does
entries = [e.rstrip(b',') + b',' for e in entries[:-1]] + [entries[-1].rstrip(b',')]
lines[start + 1:end] = [b' ' + e for e in entries]
open(p, 'wb').write(nl.join(lines))
print('added %d friend exports' % added)
