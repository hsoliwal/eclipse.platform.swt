# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others
"""array_loop_intrinsics.py: ARRAY_LOOP_INTRINSICS v1. Simple array COPY and FILL loops become the HotSpot intrinsics
System.arraycopy and Arrays.fill, and only where the replacement is provably equivalent. CPU only, deterministic.

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
Skip reasons: the start is not 0 (Arrays.fill and arraycopy throw where the loop runs zero times), a <= bound,
offsets, the same array (a forward overlapping loop is not arraycopy), unresolved or different element types, an
unprovable bound or source, a value with a side effect or the index, object values, comments in the loop, layout.
The loop shape, the comment/string mask and the census body text are ARRAY-LOOP-PATTERN v1 (array_loop_census.py).

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
from collections import Counter, namedtuple

MASK = re.compile(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])*\'', re.S)
NAME = r'[A-Za-z_$][\w$]*'
CHAIN = rf'{NAME}(?:\s*\.\s*{NAME})*'
TYPE = rf'(?:{NAME}\s*\.\s*)*{NAME}'
PRIMS = ('byte', 'short', 'char', 'int', 'long', 'float', 'double', 'boolean')
NARROW = ('byte', 'short', 'char')
INT_TYPES = ('int', 'short', 'byte', 'char')
NOT_TYPES = {'new', 'return', 'case', 'else', 'throw', 'instanceof', 'this', 'super'}
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


def element_type(masked, chain):
    """static component type of chain[i]: every declaration of its last name in the file must agree, else None"""
    n = re.escape(norm(chain).split('.')[-1])
    pats = (rf'(?<![\w$.])(?P<t>{TYPE})(?P<dims>(?:\s*\[\s*\])+)\s+{n}(?![\w$]|\s*\()',
            rf'(?<![\w$.])(?P<t>{TYPE})\s+{n}(?P<dims>(?:\s*\[\s*\])+)',
            rf'(?<![\w$.])(?P<t>{TYPE})(?P<dims>(?:\s*\[\s*\])+)\s+{NAME}(?:\s*\[\s*\])*(?:\s*=\s*[^;{{}}]*?)?\s*,\s*{n}(?![\w$])')
    found = {(norm(m['t']), m['dims'].count('[')) for p in pats for m in re.finditer(p, masked) if norm(m['t']) not in NOT_TYPES}
    if len(found) != 1:
        return None
    t, dims = found.pop()
    return t + '[]' * (dims - 1)


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


def classify(masked, orig, pairs, loop, call_sp, cast_sp, arrays):
    """(form, reason, replacement statement); form None means skipped"""
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
        return classify_copy(masked, pairs, loop, d, di, e['s'], norm(e['si']), call_sp)
    if di != loop.i:
        return None, 'SKIP_OFFSET', ''
    reason = value_reason(v, loop.i)
    if reason:
        return None, reason, ''
    if loop.a != '0':
        return None, 'SKIP_START_NONZERO', ''
    if loop.b == norm(d) + '.length':
        form, t = 'F1', element_type(masked, d)
    else:
        alloc = fresh_allocation(masked, loop, d) if SIMPLE_BOUND.fullmatch(loop.b) else None
        if not alloc or alloc[1] != loop.b:
            return None, 'SKIP_BOUND_UNPROVEN', ''
        form, t = 'F2', alloc[0]
    if t is None:
        return None, 'SKIP_TYPE_UNRESOLVED', ''
    if t not in PRIMS and v != 'null':
        return None, 'SKIP_VALUE_OBJECT', ''
    vm = VALUE.fullmatch(v)
    if t in NARROW and vm['cast'] != t:
        v = f'({t}){cast_sp}' + (f'({v})' if '?' in v else v)
    return form, '', f'{arrays}.fill{call_sp}({norm(d)}, {v});'


def classify_copy(masked, pairs, loop, d, di, s, si, call_sp):
    if norm(d) == norm(s):
        return None, 'SKIP_SAME_ARRAY', ''
    if di != loop.i or si != loop.i:
        return None, 'SKIP_OFFSET', ''
    if loop.a != '0':
        return None, 'SKIP_START_NONZERO', ''
    alloc = fresh_allocation(masked, loop, d) if SIMPLE_BOUND.fullmatch(loop.b) else None
    if not alloc or alloc[1] != loop.b:
        return None, 'SKIP_DEST_NOT_FRESH', ''
    ts = element_type(masked, s)
    if ts is None:
        return None, 'SKIP_TYPE_UNRESOLVED', ''
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
    pairs = blocks(masked)
    call_sp, cast_sp = styles(masked)
    arrays, importable = arrays_name(masked)
    out, need_import = [], False
    for loop in loops:
        form, reason, stmt = classify(masked, orig, pairs, loop, call_sp, cast_sp, arrays)
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
