#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""Deterministic Java-vs-C++ native dirty-region admission matrix."""
import random
import sys
R = random.Random(20261118)
out = sys.stdout

def put(w, h, count, rects):
    print(w, h, count, len(rects), *rects, file=out)

# Boundary choices that hit the exact unsigned UINT 32-bit row pitch limit.
for w in (0, 1, 2, 46340, 65535, 1073741822, 1073741823, 1073741824, 1073741825, 2147483647):
    for h in (-1, 0, 1, 2, 16384, 2147483647):
        for bw in (0, 1, max(1, w - 1), max(1, w), max(1, w + 1 if w < 2147483647 else w)):
            put(w, h, 1, [0, 0, bw, 1])

# Nonempty rectangular prefixes, spare capacity, negative counts and truncated quadruples.
for trial in range(40000):
    variant=trial % 13
    if variant in (0,1,2,3,4,5,6):
        w=R.randrange(1,512); h=R.randrange(1,512)
    elif variant in (7,8):
        w=R.choice((46340,65535,1073741823,1073741824,2147483647)); h=1
    else:
        w=R.choice((-1,0,1,2,3,257,2147483647));h=R.choice((-1,0,1,2,32767,2147483647))
    n=R.randrange(0,10)
    rects=[]
    for i in range(n):
        if variant in (0,1,2,3,7) and w>0 and h>0:
            x=R.randrange(w);y=R.randrange(h)
            rw=R.randrange(1,w-x+1);rh=R.randrange(1,h-y+1)
        else:
            x=R.choice((-2147483648,-1,0,1,w-1,w,2147483647))
            y=R.choice((-2147483648,-1,0,1,h-1,h,2147483647))
            rw=R.choice((-2147483648,-1,0,1,2,max(w-1,1),w,2147483647))
            rh=R.choice((-2147483648,-1,0,1,2,max(h-1,1),h,2147483647))
        rects.extend((x,y,rw,rh))
    count = R.choice((-1,0,n,max(0,n-1),n+1,4096,4097)) if variant in (11,12) else n
    if variant==10 and rects: rects=rects[:-R.randrange(1,4)]
    put(w,h,count,rects)

# Repeated full-size dirty regions overflow the cumulative packed int prefix.
for width in (1,2,16384,46340,65535):
    for count in range(1,8):
        put(width,width,count,[0,0,width,width]*count)
