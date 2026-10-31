#!/usr/bin/env python3
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""Method length gate (rule: methods <= 15 lines) for Java sources, CPU only (python -I).

A method is a brace block whose header (the code since the previous ';', '{' or '}') ends with ')' or a throws clause and
does not start with a control keyword; its length is the number of lines between the opening and the closing brace.
Strings, characters, text blocks and comments are blanked first. Prints one TSV row per method over the limit and a
summary; exit 1 when any method is over.

Usage: python -I method_gate.py [--limit 15] <file.java>...
"""
import re
import sys

CONTROL = re.compile(r'^(if|for|while|switch|try|catch|else|do|synchronized|finally|return|case|default)\b')
SIGNATURE = re.compile(r'\)\s*(throws\s+[\w.,\s]+)?$')


def blank(text):
    out, i, n = [], 0, len(text)
    while i < n:
        if text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append(re.sub(r'[^\n]', ' ', text[i:j])); i = j
        elif text[i] == '"' or text[i] == "'":
            q, j = text[i], i + 1
            while j < n and text[j] != q:
                j += 2 if text[j] == '\\' else 1
            out.append(' ' * (j + 1 - i)); i = j + 1
        elif text.startswith('//', i):
            j = text.find('\n', i); j = n if j < 0 else j
            out.append(' ' * (j - i)); i = j
        elif text.startswith('/*', i):
            j = text.find('*/', i + 2); j = n if j < 0 else j + 2
            out.append(re.sub(r'[^\n]', ' ', text[i:j])); i = j
        else:
            out.append(text[i]); i += 1
    return ''.join(out)


def methods(text):
    code = blank(text)
    stack, header_start, found = [], 0, []
    for i, c in enumerate(code):
        if c == '{':
            header = ' '.join(code[header_start:i].split())
            is_method = bool(SIGNATURE.search(header)) and not CONTROL.match(header) and '->' not in header and ' new ' not in ' ' + header
            stack.append((i, is_method, header))
            header_start = i + 1
        elif c == '}':
            if stack:
                start, is_method, header = stack.pop()
                if is_method:
                    first, last = code.count('\n', 0, start), code.count('\n', 0, i)
                    found.append((header[-90:], first + 1, max(0, last - first - 1)))
            header_start = i + 1
        elif c == ';':
            header_start = i + 1
    return found


def main(argv):
    limit = 15
    if argv[:1] == ['--limit']:
        limit, argv = int(argv[1]), argv[2:]
    over, total = 0, 0
    for path in argv:
        for header, line, length in methods(open(path, encoding='utf-8').read()):
            total += 1
            if length > limit:
                over += 1
                print(f'{path}\t{line}\t{length}\t{header}')
    print(f'METHOD_GATE limit={limit} methods={total} over={over}')
    return 1 if over else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
