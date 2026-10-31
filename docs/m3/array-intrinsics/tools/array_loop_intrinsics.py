# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others
"""array_loop_intrinsics.py: ARRAY_LOOP_INTRINSICS v1. Simple array COPY loops become System.arraycopy (a HotSpot
intrinsic) and simple FILL loops become Arrays.fill, and only where the replacement is provably equivalent. CPU only,
deterministic. Arrays.fill is not an intrinsic on JDK 21: it is a plain Java loop that C2 vectorizes the same way as
the loop it replaces, so the FILL forms are a readability change with no speed claim.

Admitted forms (atoms). Anything else is skipped with a reason:
  C1 COPY_FRESH v1  D = new T[N]; for (int i = 0; i < N; i++) D[i] = S[i];  ->  System.arraycopy(S, 0, D, 0, N);
     T is exactly the static component type of S (a widening loop, float[] <- int[], makes arraycopy throw
     ArrayStoreException). D is fresh: non-null, a different array from S, length N. S is non-null with length >= N:
     N is S.length, or S is a local allocated in an enclosing block as new T[N], N a final int or a literal, and S is
     never reassigned in that block.
  F1 FILL_FULL v1   for (int i = 0; i < D.length; i++) D[i] = V;            ->  Arrays.fill(D, V);
  F2 FILL_FRESH v1  D = new T[N]; for (int i = 0; i < N; i++) D[i] = V;    ->  Arrays.fill(D, V);
     V has no side effect and does not read i: a literal, a cast literal, a name, or name ? literal : literal.
     T is primitive (V is cast to byte, short or char as the assignment converted it), or V is null (no shared
     object identity, no boxing).
Type proof (TYPE-SCOPE v1). The element type of S (C1) and of D (F1) comes from the declaration that is in scope at
the loop, found by block structure: a local declared before the loop in an enclosing block, a parameter of the
enclosing method, or a field of an enclosing class; the innermost one wins. In a qualified chain q.f, q is resolved the
same way and f must be a field declared in the body of q's class, which must be declared in this file. The D of C1
and F2 needs no lookup: the new T[N] right before the loop gives its type. The tool reads one file at a time, so a
name or class declared anywhere else (another file, a supertype) is SKIP_TYPE_UNPROVEN, and so is a declaration
beyond a class with a supertype (an inherited member may shadow it). A declaration in scope whose type the text does
not give (lambda, for, catch, resource or pattern variable, var), or two at the same depth, is SKIP_TYPE_UNRESOLVED.
Skip reasons: the start is not 0 (Arrays.fill and arraycopy throw where the loop runs zero times), a <= bound,
offsets, the same array (a forward overlapping loop is not arraycopy), unproven, unresolved or different element
types, an unprovable bound or source, a value with a side effect or the index, object values, comments in the loop,
layout. The loop shape, the comment/string mask and the census body text are ARRAY-LOOP-PATTERN v1
(array_loop_census.py).
Known gaps of v1 (README.md): a value V that names a boxed variable (Integer v into an int[]), and the final-int
check of a bound N, which is by name in the file, not by scope.

Usage:
  census <worktree> <LOOPS.tsv> <ledger.tsv> [--apply] [--repo swt] [--root bundles] [--receipt <tsv>]
  scan [--apply] [--expect] <file.java>...
census: the census rows of one repo with pattern COPY or FILL; each loop is found again by its body text near the
census line; one ledger row per census row. scan: every counted loop of the files (fixtures); --expect checks the
`// expect: <DECISION> <FORM|REASON>` comment on the line above each loop. A second --apply rewrites nothing
(fixed point); rows already rewritten report DONE.
"""
import hashlib
import re
import sys
from bisect import bisect_right
from collections import Counter, namedtuple

MASK = re.compile(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])*\'', re.S)
NAME = r'[A-Za-z_$][\w$]*'
CHAIN = rf'{NAME}(?:\s*\.\s*{NAME})*'
TYPE = rf'(?:{NAME}\s*\.\s*)*{NAME}'
PRIMS = ('byte', 'short', 'char', 'int', 'long', 'float', 'double', 'boolean')
NARROW = ('byte', 'short', 'char')
INT_TYPES = ('int', 'short', 'byte', 'char')
NOT_VALUES = {'new', 'this', 'super', 'class'}
CALL_KEYWORDS = r'(?:if|for|while|switch|catch|synchronized|return|new|super|this)\b'
LOOP = re.compile(rf'for\s*\(\s*int\s+(?P<i>{NAME})\s*=\s*(?P<a>[^;]+?)\s*;\s*(?P=i)\s*(?P<op><=?)\s*(?P<b>[^;]+?)\s*;\s*'
                  rf'(?:(?P=i)\s*\+\+|\+\+\s*(?P=i)|(?P=i)\s*\+=\s*1)\s*\)\s*')
FOR_WORD = re.compile(r'(?<![\w$])for(?![\w$])')
ASSIGN = re.compile(rf'(?P<d>{CHAIN})\s*\[\s*(?P<di>[^\[\]]+?)\s*\]\s*=\s*(?P<v>[^;]+?)\s*;', re.S)
ELEMENT = re.compile(rf'(?P<s>{CHAIN})\s*\[\s*(?P<si>[^\[\]]+?)\s*\]')
SIMPLE_BOUND = re.compile(rf'(?:{CHAIN}|\d+)')
LITERAL = r"(?:0[xX][0-9a-fA-F_]+[lL]?|0[bB][01_]+[lL]?|\d[\d_]*(?:\.\d*)?(?:[eE][+-]?\d+)?[fFdDlL]?|'(?:\\.|[^'\\])+'|true|false|null)"
ATOM_VALUE = rf'(?:-\s*)?(?:{LITERAL}|{NAME})'
VALUE = re.compile(rf'(?:\(\s*(?P<cast>{"|".join(PRIMS)})\s*\)\s*)?(?P<core>{ATOM_VALUE}|{NAME}\s*\?\s*{ATOM_VALUE}\s*:\s*{ATOM_VALUE})')
EXPECT = re.compile(r'//\s*expect:\s*(\S+)\s+(\S+)')
WINDOW = 12
BODY_WIDTH = 160
TEXT_WIDTH = 200
Loop = namedtuple('Loop', 'start end i a op b body_m body_o census_body')
# TYPE-SCOPE v1: words before a name that make it a use, not a declaration; words before '(' that do not name a method
TYPE_WINDOW = 400
USE_WORDS = frozenset(('return', 'new', 'throw', 'case', 'else', 'instanceof', 'do', 'assert', 'yield', 'this', 'super',
                       'extends', 'implements', 'throws', 'permits', 'import', 'package', 'class', 'interface', 'enum',
                       'record', 'default', 'break', 'continue', 'when', 'goto', 'const'))
NOT_METHODS = frozenset(('if', 'for', 'while', 'switch', 'catch', 'synchronized', 'try', 'return', 'throw', 'new', 'super',
                         'this', 'assert', 'yield', 'case', 'else', 'do'))
PATTERN_WORDS = frozenset(('instanceof', 'case'))
DECL_END = frozenset('=;,):[')
INFERRED = 'var'
UNPROVEN, UNRESOLVED = 'SKIP_TYPE_UNPROVEN', 'SKIP_TYPE_UNRESOLVED'
GENERIC = r'<[^;{}()]*>'
THROWS = rf'(?:throws\s+{TYPE}(?:\s*,\s*{TYPE})*\s*)?'
TYPE_TAIL = re.compile(rf'(?<![\w$])(?P<t>{TYPE})\s*(?P<gen>{GENERIC})?(?P<dims>(?:\s*\[\s*\])*)\s*$')
DECL_HEAD = re.compile(rf'\s*(?:(?:@{NAME}(?:\s*\([^()]*\))?|final|static|private|public|protected|transient|volatile)\s+)*'
                       rf'(?P<t>{TYPE})\s*(?P<gen>{GENERIC})?(?P<dims>(?:\s*\[\s*\])*)\s+{NAME}(?:\s*\[\s*\])*\s*[=,]')
OWN_DIMS = re.compile(r'(?:\[\s*\]\s*)+')
NAME_TAIL = re.compile(rf'(?<![\w$.])(?P<n>{NAME})\s*$')
TYPE_END = re.compile(r'[\w$\]>]$')
WHEN = re.compile(r'when(?![\w$])')
ARROW = re.compile(r'\s*->')
CLASS_HEAD = re.compile(rf'(?<![\w$.])(?:class|interface|enum|record)\s+{NAME}(?P<rest>[^;{{}}]*)$')
SUPERTYPE = re.compile(r'(?<![\w$])(?:extends|implements)(?![\w$])')
ANON_TAIL = re.compile(rf'(?<![\w$])new\s+{TYPE}\s*(?:{GENERIC})?\s*$')
RECORD_TAIL = re.compile(rf'(?<![\w$.])record\s+{NAME}\s*(?:{GENERIC})?\s*$')
METHOD_TAIL = re.compile(rf'\)\s*{THROWS}$')
BODY_AFTER = re.compile(rf'\s*{THROWS}(?P<x>\{{|;|default(?![\w$]))')
Nest = namedtuple('Nest', 'starts ends parent')
Ctx = namedtuple('Ctx', 'masked braces parens brace_at paren_end memo')
Decl = namedtuple('Decl', 'kind lo hi scope base dims')


def norm(s):
    return re.sub(r'\s+', '', s)


def mask(s):
    return MASK.sub(lambda m: ''.join(c if c == '\n' else ' ' for c in m.group(0)), s)


def census_body(m, j):
    """ARRAY-LOOP-PATTERN v1 body_after, byte for byte, so that a loop is found again by its census text"""
    if j < len(m) and m[j] == '{':
        depth, k = 0, j
        while k < len(m):
            depth += (m[k] == '{') - (m[k] == '}')
            k += 1
            if depth == 0:
                break
        inner = m[j + 1:k - 1].strip()
        return inner if inner.count(';') <= 2 else None
    k = m.find(';', j)
    return m[j:k + 1].strip() if k > 0 else None


def parse_loop(masked, orig, pos):
    h = LOOP.match(masked, pos)
    if not h:
        return None
    j = h.end()
    if j < len(masked) and masked[j] == '{':
        depth, k = 0, j
        while k < len(masked):
            depth += (masked[k] == '{') - (masked[k] == '}')
            k += 1
            if depth == 0:
                break
        if depth:
            return None
        body_m, body_o = masked[j + 1:k - 1], orig[j + 1:k - 1]
    else:
        k = masked.find(';', j)
        if k < 0:
            return None
        k += 1
        body_m, body_o = masked[j:k], orig[j:k]
    cb = census_body(masked, j)
    return Loop(h.start(), k, h['i'], norm(orig[h.start('a'):h.end('a')]), h['op'], norm(orig[h.start('b'):h.end('b')]),
                body_m, body_o, re.sub(r'\s+', ' ', cb)[:BODY_WIDTH] if cb else None)


def blocks(masked):
    pairs, stack = [], []
    for k, c in enumerate(masked):
        if c == '{':
            stack.append(k)
        elif c == '}' and stack:
            pairs.append((stack.pop(), k))
    return pairs


def innermost(pairs, pos):
    inside = [p for p in pairs if p[0] < pos < p[1]]
    return max(inside) if inside else (-1, 1 << 62)


def nest(masked, open_ch, close_ch):
    """bracket pairs in start order, each with the index of the pair that encloses it (-1 at the top level)"""
    starts, ends, parent, stack = [], [], [], []
    for k, c in enumerate(masked):
        if c == open_ch:
            parent.append(stack[-1] if stack else -1)
            stack.append(len(starts))
            starts.append(k)
            ends.append(len(masked))
        elif c == close_ch and stack:
            ends[stack.pop()] = k
    return Nest(starts, ends, parent)


def enclosing(n, pos):
    """index of the innermost pair with start < pos < end, or -1"""
    j = bisect_right(n.starts, pos - 1) - 1
    while j >= 0 and n.ends[j] <= pos:
        j = n.parent[j]
    return j


def index(masked):
    braces, parens = nest(masked, '{', '}'), nest(masked, '(', ')')
    return Ctx(masked, braces, parens, {s: k for k, s in enumerate(braces.starts)},
               {e: k for k, e in enumerate(parens.ends)}, {})


def block_kind(ctx, b):
    """('class', has a supertype) | ('method', index of its parameter parens) | ('other', None) for brace pair b"""
    key = ('kind', b)
    if key not in ctx.memo:
        ctx.memo[key] = classify_block(ctx, b)
    return ctx.memo[key]


def classify_block(ctx, b):
    m, s = ctx.masked, ctx.braces.starts[b]
    k = max(m.rfind(';', 0, s), m.rfind('{', 0, s), m.rfind('}', 0, s))
    head = m[k + 1:s]
    c = CLASS_HEAD.search(head)
    if c:
        return 'class', bool(SUPERTYPE.search(c['rest']))
    t = METHOD_TAIL.search(head)
    p = ctx.paren_end.get(k + 1 + t.start(), -1) if t else -1
    if p < 0:
        return 'other', None
    before = m[max(0, ctx.parens.starts[p] - TYPE_WINDOW):ctx.parens.starts[p]]
    if ANON_TAIL.search(before):
        return 'class', True
    w = NAME_TAIL.search(before)
    return ('method', p) if w and w['n'] not in NOT_METHODS else ('other', None)


def own_dims(after):
    d = OWN_DIMS.match(after)
    return d.group(0).count('[') if d else 0


def lambda_params(ctx, g):
    return ARROW.match(ctx.masked, ctx.parens.ends[g] + 1) is not None


def declarations(ctx, n):
    """every declaration of simple name n in the file, each with the region where it is in scope"""
    key = ('decl', n)
    if key not in ctx.memo:
        ctx.memo[key] = [d for h in re.finditer(rf'(?<![\w$.]){re.escape(n)}(?![\w$])', ctx.masked)
                         for d in [declaration_at(ctx, h.start(), h.end())] if d]
    return ctx.memo[key]


def declaration_at(ctx, q, e):
    """the declaration made by the name at [q, e), or None where the name is used; 'unknown' where the text does not
    give its type or scope (lambda, for, catch, resource, pattern), with its whole enclosing block as region"""
    m = ctx.masked
    b = enclosing(ctx.braces, q)
    lo, hi = (ctx.braces.starts[b], ctx.braces.ends[b]) if b >= 0 else (-1, len(m))
    unknown = Decl('unknown', lo, hi, b, None, 0)
    after = m[e:e + TYPE_WINDOW].lstrip()
    if after.startswith('->') or WHEN.match(after):
        return unknown
    if not after or after[0] not in DECL_END or after.startswith('=='):
        return None
    before = m[max(0, q - TYPE_WINDOW):q].rstrip()
    varargs = before.endswith('...')
    before = before[:-3].rstrip() if varargs else before
    g = enclosing(ctx.parens, q)
    in_parens = g >= 0 and ctx.parens.starts[g] > lo
    if before.endswith(','):
        return comma_declaration(ctx, q, after, b, g, in_parens, unknown)
    if before.endswith('->') or not TYPE_END.search(before):
        return None
    w, t = NAME_TAIL.search(before), TYPE_TAIL.search(before)
    if w and w['n'] in USE_WORDS:
        return None
    if not t:
        return unknown
    pre = before[:t.start()].rstrip()
    intro = NAME_TAIL.search(pre)
    if pre.endswith(('@', '.')) or (intro and intro['n'] in PATTERN_WORDS):
        return unknown
    base = norm(t['t']) + norm(t['gen'] or '')
    dims = t['dims'].count('[') + own_dims(after) + varargs
    return typed_declaration(ctx, q, b, g, in_parens, None if base == INFERRED else base, dims, unknown)


def comma_declaration(ctx, q, after, b, g, in_parens, unknown):
    """a name after a comma: a further declarator of a declaration statement, a lambda or for-init name, or a use"""
    m = ctx.masked
    if in_parens:
        s = ctx.parens.starts[g]
        w = NAME_TAIL.search(m[max(0, s - TYPE_WINDOW):s])
        for_init = w is not None and w['n'] == 'for' and ';' not in m[s:q]
        return unknown if lambda_params(ctx, g) or for_init else None
    st = max(m.rfind(';', 0, q), m.rfind('{', 0, q), m.rfind('}', 0, q)) + 1
    h = DECL_HEAD.match(m, st, q)
    if not h or norm(h['t']) in USE_WORDS:
        return unknown
    base = norm(h['t']) + norm(h['gen'] or '')
    return typed_declaration(ctx, q, b, g, False, base, h['dims'].count('[') + own_dims(after), unknown)


def typed_declaration(ctx, q, b, g, in_parens, base, dims, unknown):
    if in_parens:
        return parameter(ctx, g, base, dims, unknown)
    if b >= 0 and block_kind(ctx, b)[0] == 'class':
        return Decl('field', unknown.lo, unknown.hi, b, base, dims)
    return Decl('local', q, unknown.hi, b, base, dims)


def parameter(ctx, g, base, dims, unknown):
    """a typed name in parens g: a method or constructor parameter is in scope in the body, an abstract one nowhere"""
    m, s, e = ctx.masked, ctx.parens.starts[g], ctx.parens.ends[g]
    before = m[max(0, s - TYPE_WINDOW):s]
    w = NAME_TAIL.search(before)
    if lambda_params(ctx, g) or not w or w['n'] in NOT_METHODS or ANON_TAIL.search(before) or RECORD_TAIL.search(before):
        return unknown
    x = BODY_AFTER.match(m, e + 1)
    if x and x['x'] != '{':
        return None
    body = ctx.brace_at.get(x.end() - 1, -1) if x else -1
    if body < 0 or block_kind(ctx, body) != ('method', g):
        return unknown
    return Decl('param', ctx.braces.starts[body], ctx.braces.ends[body], body, base, dims)


def crosses_supertype(ctx, pos, scope):
    """a class with a supertype lies between pos and the declaring block scope: an inherited member may shadow"""
    j = enclosing(ctx.braces, pos)
    while j != scope:
        if j < 0 or block_kind(ctx, j) == ('class', True):
            return True
        j = ctx.braces.parent[j]
    return False


def resolve_name(ctx, n, pos):
    """((base, dims), '') of the declaration of simple name n in scope at pos, or (None, skip reason)"""
    ds = [d for d in declarations(ctx, n) if d.lo < pos < d.hi]
    if any(d.kind == 'unknown' for d in ds):
        return None, UNRESOLVED
    if not ds:
        return None, UNPROVEN
    best = min(ds, key=lambda d: d.hi)
    if sum(d.hi == best.hi for d in ds) > 1:
        return None, UNRESOLVED
    if crosses_supertype(ctx, pos, best.scope):
        return None, UNPROVEN
    return (best.base, best.dims), ''


def enclosing_class(ctx, pos):
    j = enclosing(ctx.braces, pos)
    while j >= 0 and block_kind(ctx, j)[0] != 'class':
        j = ctx.braces.parent[j]
    return j


def class_of(ctx, t, pos):
    """(body, '') of the class named by declared type t: declared once in this file and visible at pos"""
    base, dims = t
    if base is None or dims or base in PRIMS or not re.fullmatch(NAME, base):
        return -1, UNPROVEN
    heads = list(re.finditer(rf'(?<![\w$.])(?:class|interface|enum|record)\s+{re.escape(base)}(?![\w$])', ctx.masked))
    if len(heads) != 1:
        return -1, UNRESOLVED if heads else UNPROVEN
    h = heads[0]
    body = ctx.brace_at.get(ctx.masked.find('{', h.end()), -1)
    outer = enclosing(ctx.braces, h.start())
    visible = outer < 0 or (ctx.braces.starts[outer] < pos < ctx.braces.ends[outer]
                            and (block_kind(ctx, outer)[0] == 'class' or h.start() < pos))
    if body < 0 or not visible or crosses_supertype(ctx, pos, outer):
        return -1, UNPROVEN
    return body, ''


def member(ctx, cls, name):
    """((base, dims), '') of field name declared directly in the class body cls"""
    ds = [d for d in declarations(ctx, name) if d.scope == cls]
    fields = [d for d in ds if d.kind == 'field']
    if len(fields) != len(ds) or len(fields) > 1:
        return None, UNRESOLVED
    if not fields:
        return None, UNPROVEN
    return (fields[0].base, fields[0].dims), ''


def chain_type(ctx, chain, pos):
    parts = norm(chain).split('.')
    if parts[0] == 'this':
        cls = enclosing_class(ctx, pos)
        t, why = member(ctx, cls, parts[1]) if cls >= 0 and len(parts) > 1 else (None, UNPROVEN)
        rest = parts[2:]
    else:
        t, why = resolve_name(ctx, parts[0], pos)
        rest = parts[1:]
    for name in rest:
        if why:
            break
        cls, why = class_of(ctx, t, pos)
        if not why:
            t, why = member(ctx, cls, name)
    return t, why


def element_type(ctx, chain, pos):
    """(static component type of chain[i] at pos, '') from the declaration in scope (TYPE-SCOPE v1), or (None, reason)"""
    t, why = chain_type(ctx, chain, pos)
    if why:
        return None, why
    base, dims = t
    if base is None or dims < 1:
        return None, UNRESOLVED
    return base + '[]' * (dims - 1), ''


def previous_statement(masked, pos):
    j = len(masked[:pos].rstrip())
    if not j or masked[j - 1] != ';':
        return None
    k = max(masked.rfind(';', 0, j - 1), masked.rfind('{', 0, j - 1), masked.rfind('}', 0, j - 1))
    return masked[k + 1:j].strip()


def fresh_allocation(masked, loop, d):
    """the statement right before the loop allocates d: `d = new T[N];` or `T[] d = new T[N];`; returns (T, N)"""
    st = previous_statement(masked, loop.start)
    if st is None:
        return None
    m = re.fullmatch(rf'(?:(?:final\s+)?(?P<dt>{TYPE})\s*\[\s*\]\s+)?(?P<d>{CHAIN})\s*=\s*new\s+(?P<t>{TYPE})\s*\[\s*(?P<n>[^\[\]]+?)\s*\]\s*;', st) \
        or re.fullmatch(rf'(?:final\s+)?(?P<dt>{TYPE})\s+(?P<d>{NAME})\s*\[\s*\]\s*=\s*new\s+(?P<t>{TYPE})\s*\[\s*(?P<n>[^\[\]]+?)\s*\]\s*;', st)
    if not m or norm(m['d']) != norm(d) or (m['dt'] and norm(m['dt']) != norm(m['t'])):
        return None
    return norm(m['t']), norm(m['n'])


def final_int(masked, n):
    if re.fullmatch(r'\d+', n):
        return True
    if not re.fullmatch(NAME, n):
        return False
    decls = re.findall(rf'(?<![\w$.])((?:(?:final|static|private|public|protected)\s+)*)(?:{"|".join(INT_TYPES)})\s+{re.escape(n)}\s*=', masked)
    return bool(decls) and all('final' in mods.split() for mods in decls)


def fresh_source(masked, pairs, loop, s, t, n):
    """S is a local allocated as new T[N] in a block enclosing the loop, N final, S never reassigned in that block"""
    if not re.fullmatch(NAME, s) or not final_int(masked, n):
        return False
    hits = [m for m in re.finditer(rf'(?<![\w$.]){re.escape(s)}\s*=\s*new\s+{re.escape(t)}\s*\[\s*{re.escape(n)}\s*\]', masked)
            if m.start() < loop.start]
    if not hits:
        return False
    m = hits[-1]
    k = max(masked.rfind(';', 0, m.start()), masked.rfind('{', 0, m.start()), masked.rfind('}', 0, m.start()))
    head = masked[k + 1:m.start()]
    if not re.fullmatch(rf'\s*(?:final\s+)?{re.escape(t)}\s*\[\s*\]\s+(?:{NAME}(?:\s*\[\s*\])*\s*=\s*[^;{{}}]*?,\s*)*', head):
        return False
    block = innermost(pairs, m.start())
    if not block[0] < loop.start < block[1]:
        return False
    return not re.search(rf'(?<![\w$.]){re.escape(s)}\s*=(?!=)', masked[m.end():block[1]])


def value_reason(v, i):
    if re.search(rf'(?<![\w$]){re.escape(i)}(?![\w$])', v):
        return 'SKIP_VALUE_INDEX'
    m = VALUE.fullmatch(v)
    if m and m['core'].strip() not in NOT_VALUES:
        return None
    if re.search(rf'(?<![\w$.])new(?![\w$])|{NAME}\s*\(|\+\+|--|(?<![=!<>])=(?!=)', v):
        return 'SKIP_VALUE_SIDE_EFFECT'
    return 'SKIP_VALUE_FIELD' if '.' in v else 'SKIP_VALUE_SHAPE'


def styles(masked):
    sp = len(re.findall(r'(?:System\.arraycopy|Arrays\.fill) \(', masked))
    ns = len(re.findall(r'(?:System\.arraycopy|Arrays\.fill)\(', masked))
    if sp == ns:
        sp = len(re.findall(rf'(?<![\w$.])(?!{CALL_KEYWORDS})[a-z][\w$]* \(', masked))
        ns = len(re.findall(rf'(?<![\w$.])(?!{CALL_KEYWORDS})[a-z][\w$]*\(', masked))
    cs = len(re.findall(rf'\((?:{"|".join(PRIMS)})\) ', masked))
    cn = len(re.findall(rf'\((?:{"|".join(PRIMS)})\)[^\s)]', masked))
    return (' ' if sp > ns else ''), (' ' if cs > cn else '')


def arrays_name(masked):
    if re.search(r'^import\s+java\.util\.(?:\*|Arrays)\s*;', masked, re.M):
        return 'Arrays', False
    if re.search(r'(?<![\w$])(?:class|interface|enum|record)\s+Arrays(?![\w$])|^import\s+[\w.]+\.Arrays\s*;', masked, re.M):
        return 'java.util.Arrays', False
    return 'Arrays', True


def classify(ctx, orig, pairs, loop, call_sp, cast_sp, arrays):
    """(form, reason, replacement statement); form None means skipped"""
    masked = ctx.masked
    span_o, span_m = orig[loop.start:loop.end], masked[loop.start:loop.end]
    if any(span_o[k] == '/' and span_m[k] == ' ' for k in range(len(span_o))):
        return None, 'SKIP_COMMENT_IN_LOOP', ''
    body = loop.body_m.strip()
    if body.count(';') != 1 or not body.endswith(';') or '{' in body or '}' in body:
        return None, 'SKIP_BODY_SHAPE', ''
    m = ASSIGN.fullmatch(loop.body_o.strip())
    if not m:
        return None, 'SKIP_BODY_SHAPE', ''
    if loop.op != '<':
        return None, 'SKIP_LE_BOUND', ''
    d, di, v = m['d'], norm(m['di']), m['v'].strip()
    if "'" not in v and '"' not in v:
        v = re.sub(r'\s+', ' ', v)
    e = ELEMENT.fullmatch(v)
    if e:
        return classify_copy(ctx, pairs, loop, d, di, e['s'], norm(e['si']), call_sp)
    if di != loop.i:
        return None, 'SKIP_OFFSET', ''
    reason = value_reason(v, loop.i)
    if reason:
        return None, reason, ''
    if loop.a != '0':
        return None, 'SKIP_START_NONZERO', ''
    if loop.b == norm(d) + '.length':
        t, why = element_type(ctx, d, loop.start)
        if why:
            return None, why, ''
        form = 'F1'
    else:
        alloc = fresh_allocation(masked, loop, d) if SIMPLE_BOUND.fullmatch(loop.b) else None
        if not alloc or alloc[1] != loop.b:
            return None, 'SKIP_BOUND_UNPROVEN', ''
        form, t = 'F2', alloc[0]
    if t not in PRIMS and v != 'null':
        return None, 'SKIP_VALUE_OBJECT', ''
    vm = VALUE.fullmatch(v)
    if t in NARROW and vm['cast'] != t:
        v = f'({t}){cast_sp}' + (f'({v})' if '?' in v else v)
    return form, '', f'{arrays}.fill{call_sp}({norm(d)}, {v});'


def classify_copy(ctx, pairs, loop, d, di, s, si, call_sp):
    masked = ctx.masked
    if norm(d) == norm(s):
        return None, 'SKIP_SAME_ARRAY', ''
    if di != loop.i or si != loop.i:
        return None, 'SKIP_OFFSET', ''
    if loop.a != '0':
        return None, 'SKIP_START_NONZERO', ''
    alloc = fresh_allocation(masked, loop, d) if SIMPLE_BOUND.fullmatch(loop.b) else None
    if not alloc or alloc[1] != loop.b:
        return None, 'SKIP_DEST_NOT_FRESH', ''
    ts, why = element_type(ctx, s, loop.start)
    if why:
        return None, why, ''
    if ts != alloc[0]:
        return None, 'SKIP_TYPE_MISMATCH', ''
    if loop.b != norm(s) + '.length' and not fresh_source(masked, pairs, loop, norm(s), alloc[0], loop.b):
        return None, 'SKIP_SOURCE_UNPROVEN', ''
    return 'C1', '', f'System.arraycopy{call_sp}({norm(s)}, 0, {norm(d)}, 0, {loop.b});'


def line_span(orig, loop):
    ls = orig.rfind('\n', 0, loop.start) + 1
    le = orig.find('\n', loop.end)
    le = len(orig) if le < 0 else le
    if orig[le - 1:le] == '\r':
        le -= 1
    if orig[ls:loop.start].strip() or orig[loop.end:le].strip():
        return None
    return ls, le, orig[ls:loop.start]


def add_import(text):
    eol = '\r\n' if '\r\n' in text else '\n'
    lines = text.split('\n')
    cr = '\r' if eol == '\r\n' else ''
    new = 'import java.util.Arrays;'
    imports = [k for k, line in enumerate(lines) if line.startswith('import ')]
    javas = [k for k in imports if lines[k].startswith('import java.')]
    if javas:
        pos = next((k for k in javas if lines[k].rstrip('\r') > new), javas[-1] + 1)
        lines[pos:pos] = [new + cr]
    elif imports:
        lines[imports[0]:imports[0]] = [new + cr, cr]
    else:
        pkg = next(k for k, line in enumerate(lines) if line.startswith('package '))
        lines[pkg + 1:pkg + 1] = [cr, new + cr]
    return '\n'.join(lines)


def rewrite(orig, edits, need_import):
    for ls, le, text in sorted(edits, reverse=True):
        orig = orig[:ls] + text + orig[le:]
    return add_import(orig) if need_import else orig


def plan_file(orig, loops):
    """classify loops of one file; returns per loop (form, reason, replacement, edit) and whether Arrays is imported"""
    masked = mask(orig)
    pairs, ctx = blocks(masked), index(masked)
    call_sp, cast_sp = styles(masked)
    arrays, importable = arrays_name(masked)
    out, need_import = [], False
    for loop in loops:
        form, reason, stmt = classify(ctx, orig, pairs, loop, call_sp, cast_sp, arrays)
        edit = None
        if form:
            span = line_span(orig, loop)
            if span is None:
                form, reason, stmt = None, 'SKIP_LAYOUT', ''
            else:
                edit = (span[0], span[1], span[2] + stmt)
                need_import |= form.startswith('F') and importable
        out.append((form, reason, stmt, edit))
    return out, need_import


def loops_at(masked, orig, line, body, window, claimed):
    starts = [0] + [k + 1 for k, c in enumerate(masked) if c == '\n']
    for ln in [line] + [line + s * k for k in range(1, window + 1) for s in (-1, 1)]:
        if not 1 <= ln <= len(starts):
            continue
        end = starts[ln] if ln < len(starts) else len(masked)
        for f in FOR_WORD.finditer(masked, starts[ln - 1], end):
            loop = parse_loop(masked, orig, f.start())
            if loop and loop.census_body == body and loop.start not in claimed:
                claimed.add(loop.start)
                return loop, ln
    return None, 0


def rewritten_near(orig, line, body, window):
    m = re.match(rf'\s*({CHAIN})\s*\[', body)
    if not m:
        return False
    lines = orig.split('\n')[max(0, line - 1 - window):line + window]
    dst = norm(m.group(1))
    return any(('System.arraycopy' in x or 'Arrays.fill' in x) and dst in norm(x) for x in lines)


def sha(text):
    return hashlib.sha256(text.encode('utf-8')).hexdigest()


def read(path):
    with open(path, encoding='utf-8', newline='') as f:
        return f.read()


def write(path, text):
    with open(path, 'w', encoding='utf-8', newline='') as f:
        f.write(text)


def one_line(s):
    return re.sub(r'\s+', ' ', s).strip()[:TEXT_WIDTH]


def census(argv):
    opts = {'--repo': 'swt', '--root': 'bundles', '--receipt': ''}
    args, k = [], 0
    while k < len(argv):
        if argv[k] in opts:
            opts[argv[k]] = argv[k + 1]
            k += 2
        else:
            args.append(argv[k])
            k += 1
    apply = '--apply' in args
    root, census_tsv, ledger = [a for a in args if a != '--apply'][:3]
    rows = [r.rstrip('\n').split('\t') for r in open(census_tsv, encoding='utf-8')][1:]
    rows = [r for r in rows if len(r) >= 6 and r[0] == opts['--repo'] and r[3] in ('COPY', 'FILL')]
    files = list(dict.fromkeys(r[1] for r in rows))
    out, receipt, tally = [], [], Counter()
    for rel in files:
        path = f'{root}/{opts["--root"]}/{rel}' if opts['--root'] else f'{root}/{rel}'
        orig = read(path)
        masked = mask(orig)
        mine = [r for r in rows if r[1] == rel]
        claimed = set()
        found = [loops_at(masked, orig, int(r[2]), r[5], WINDOW, claimed) for r in mine]
        plans, need_import = plan_file(orig, [lp for lp, _ in found if lp])
        it = iter(plans)
        edits = []
        for r, (loop, ln) in zip(mine, found):
            if loop is None:
                state = 'DONE' if rewritten_near(orig, int(r[2]), r[5], WINDOW) else 'ABSENT'
                out.append((rel, r[2], '', r[3], '', state, '', one_line(r[5]), ''))
                tally[state] += 1
                continue
            form, reason, stmt, edit = next(it)
            state = ('REWRITE' if apply else 'PLAN') if form else 'SKIP'
            tally[reason or state] += 1
            if edit:
                edits.append(edit)
            out.append((rel, r[2], str(ln), r[3], form or '', state, reason, one_line(orig[loop.start:loop.end]), stmt))
        if apply and edits:
            new = rewrite(orig, edits, need_import)
            write(path, new)
            receipt.append((f'{opts["--root"]}/{rel}' if opts['--root'] else rel, sha(orig), sha(new), str(len(edits)),
                            'yes' if need_import else 'no'))
    with open(ledger, 'w', encoding='utf-8', newline='\n') as w:
        w.write('id\tfile\tcensus_line\tline\tpattern\tform\tdecision\treason\tloop\treplacement\n')
        for n, row in enumerate(out, 1):
            w.write(f'A{n:02d}\t' + '\t'.join(c or '-' for c in row) + '\n')
    if opts['--receipt'] and receipt:
        with open(opts['--receipt'], 'w', encoding='utf-8', newline='\n') as w:
            w.write('file\tsha256_before\tsha256_after\trewrites\timport_added\n')
            w.write(''.join('\t'.join(r) + '\n' for r in receipt))
    print(f'ARRAY_LOOP_INTRINSICS {"apply" if apply else "plan"}: rows={len(out)} '
          + ' '.join(f'{k}={v}' for k, v in sorted(tally.items())) + f' files_written={len(receipt)}')
    return 0


def scan(argv):
    apply, expect = '--apply' in argv, '--expect' in argv
    bad = 0
    for path in [a for a in argv if not a.startswith('--')]:
        orig = read(path)
        masked = mask(orig)
        loops = [lp for f in FOR_WORD.finditer(masked) for lp in [parse_loop(masked, orig, f.start())] if lp]
        plans, need_import = plan_file(orig, loops)
        lines = orig.split('\n')
        checked = 0
        for loop, (form, reason, stmt, _) in zip(loops, plans):
            ln = orig.count('\n', 0, loop.start) + 1
            got = ('REWRITE', form) if form else ('SKIP', reason)
            want = EXPECT.search(lines[ln - 2]) if ln > 1 else None
            verdict = '' if not expect or not want else ('OK' if want.groups() == got else 'MISMATCH')
            bad += verdict == 'MISMATCH'
            checked += bool(want)
            print(f'{path.rsplit("/", 1)[-1]}\t{ln}\t{got[0]}\t{got[1]}\t{stmt}\t{verdict}')
        markers = sum(1 for line in lines if EXPECT.search(line))
        if expect and markers != checked:
            print(f'ARRAY_LOOP_INTRINSICS scan: {markers - checked} expect markers name no counted loop')
            bad += markers - checked
        edits = [p[3] for p in plans if p[3]]
        if apply and edits:
            write(path, rewrite(orig, edits, need_import))
        print(f'ARRAY_LOOP_INTRINSICS scan {path.rsplit("/", 1)[-1]}: loops={len(loops)} rewrites={len(edits)} '
              f'applied={"yes" if apply and edits else "no"} mismatches={bad}')
    return 1 if bad else 0


if __name__ == '__main__':
    mode = sys.argv[1] if len(sys.argv) > 1 else ''
    sys.exit(census(sys.argv[2:]) if mode == 'census' else scan(sys.argv[2:]) if mode == 'scan' else
             print(__doc__) or 2)
