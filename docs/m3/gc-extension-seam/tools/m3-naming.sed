# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0
# M3 naming (brief rule 14) for text, docs and paths: GNU sed -E. Same rules as M3Naming.java (Java sources
# go through the OpenRewrite recipe M3NamingJava). Licence lines and third-party or donor spans are kept.
/Copyright|SPDX-|Apache-2\.0|BSD-|EPL-|\(MIT\)|\bMIT\b|LICENSE|NOTICE|THIRD_PARTY/b
/[Ss]kija|SKIJA/!b
s/m3\.skija\./m3.gpu./g
s/m3-skija-/m3-gpu-/g
s/m3\.skija\b/m3.gpu/g
:p1
s/(io[./]github[./]humbleui[A-Za-z0-9_.\/*]*)([Ss])kija/\1\2\x01ija/
t p1
s/([Ss])kija-/\1\x01ija-/g
s/-([Ss])kija/-\1\x01ija/g
s/HumbleUI([ \/]?)([Ss])kija/HumbleUI\1\2\x01ija/g
s/JetBrains\/([Ss])kija/JetBrains\/\1\x01ija/g
s/SwtSkija/SwtS\x01ija/g
# References to the retired name stay: the old token next to its own M3 rename (SKIJA->M3, `skija` becomes `m3`,
# SkijaGC to M3GC) or after was/formerly. \x02 masks the K of SKIJA as \x01 masks the k of Skija.
:p3
s/(^|[^A-Za-z0-9_])([A-Za-z0-9_]*)Skija([A-Za-z0-9_]*)([`'"}]?([[:space:]]*(->|→|=>)[[:space:]]*|[[:space:]]+(becomes|to)[[:space:]]+)(\{@code[[:space:]]+|[`'"])?\2M3\3)($|[^A-Za-z0-9_])/\1\2S\x01ija\3\4\9/
t p3
:p4
s/(^|[^A-Za-z0-9_])([A-Za-z0-9_]*)SKIJA([A-Za-z0-9_]*)([`'"}]?([[:space:]]*(->|→|=>)[[:space:]]*|[[:space:]]+(becomes|to)[[:space:]]+)(\{@code[[:space:]]+|[`'"])?\2M3\3)($|[^A-Za-z0-9_])/\1\2S\x02IJA\3\4\9/
t p4
:p5
s/(^|[^A-Za-z0-9_])([A-Za-z0-9_]*)skija([A-Za-z0-9_]*)([`'"}]?([[:space:]]*(->|→|=>)[[:space:]]*|[[:space:]]+(becomes|to)[[:space:]]+)(\{@code[[:space:]]+|[`'"])?\2m3\3)($|[^A-Za-z0-9_])/\1\2s\x01ija\3\4\9/
t p5
s/(^|[^A-Za-z0-9_])(was|formerly)([[:space:]]+[`'"]?[A-Za-z0-9_]*)([Ss])kija/\1\2\3\4\x01ija/g
s/(^|[^A-Za-z0-9_])(was|formerly)([[:space:]]+[`'"]?[A-Za-z0-9_]*)SKIJA/\1\2\3S\x02IJA/g
s/Eclipse SWT GPU Skija/Eclipse SWT GPU M3/g
s/Eclipse SWT Render Skija/Eclipse SWT Render M3/g
s/through Skija \(Skia\)/through the M3 renderer/g
s/SWT through Skija\./SWT through the M3 renderer./g
s/Skija (backend|implementation|mode|renderer)/M3 \1/g
:p2
s#(org[./]eclipse[./]swt([./][A-Za-z0-9_]+)*)([./])skija\b#\1\3m3#
t p2
s/(^|[^A-Za-z0-9_.\/-])skija($|[^A-Za-z0-9_.-])/\1m3\2/g
s/(^|[^A-Za-z0-9_.\/-])skija($|[^A-Za-z0-9_.-])/\1m3\2/g
s/([A-Za-z0-9_])Skija/\1M3/g
s/Skija([A-Za-z0-9_])/M3\1/g
s/(^|[^A-Za-z0-9_])skija([A-Z0-9_])/\1m3\2/g
s/_skija($|[^A-Za-z0-9])/_m3\1/g
s/_skija($|[^A-Za-z0-9])/_m3\1/g
s/(^|[^A-Za-z0-9])SKIJA($|[^A-Za-z0-9])/\1M3\2/g
s/(^|[^A-Za-z0-9])SKIJA($|[^A-Za-z0-9])/\1M3\2/g
s/\x01/k/g
s/\x02/K/g
