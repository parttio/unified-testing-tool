#!/usr/bin/env python3
"""PROTOTYPE scorer: intent share per style and test, using the token rules of the noise baseline
(docs/research/unified-ui-test-noise-baseline.md on branch research/unified-ui-test-noise-baseline),
extended with the new styles' verbs. Run: python3 -I score/score.py src/main/java/proto"""
import re, sys, pathlib, collections

TOKEN = re.compile(r'''"(?:\\.|[^"\\])*"|\d+(?:\.\d+)?|[A-Za-z_$][\w$]*|->|[^\s\w]''')
ACTION = {'click', 'setValue', 'navigate', 'fillIn', 'clickOn'}
ASSERT = {'assertEquals', 'assertTrue', 'assertFalse'}
ROLE_FACTORIES = {'textField', 'button', 'notification', 'grid', 'container'}  # style D role nouns

def methods(src):
    for m in re.finditer(r'void (\w+)\(UnifiedPage page\) \{', src):
        i, depth = m.end(), 1
        while depth:
            depth += {'{': 1, '}': -1}.get(src[i], 0); i += 1
        yield m.group(1), src[m.end():i - 1]

def statements(body):
    out, cur, depth = [], '', 0
    for ch in body:
        depth += {'(': 1, ')': -1}.get(ch, 0)
        if ch == ';' and depth == 0:
            out.append(cur.strip()); cur = ''
        else:
            cur += ch
    return [s for s in out if s]

def ratio(stmt):
    toks = [t for t in TOKEN.findall(stmt) if re.match(r'["\w$]', t)]
    intent = plumbing = 0; seen_role = False
    for i, t in enumerate(toks):
        nxt = toks[i + 1] if i + 1 < len(toks) else ''
        if t[0] == '"' or t[0].isdigit(): intent += 1
        elif t in ACTION or t in ASSERT: intent += 1
        elif re.match(r'(is|has|shows|doesNot)[A-Z]', t): intent += 1
        elif t.endswith('View') and nxt == 'class': intent += 1
        elif nxt == 'class' and not seen_role: intent += 1; seen_role = True
        elif t in ROLE_FACTORIES and not seen_role: intent += 1; seen_role = True
        elif re.match(r'get[A-Z]', t): intent += 1
        else: plumbing += 1
    return intent, plumbing

root = pathlib.Path(sys.argv[1])
rows = collections.defaultdict(dict)
for f in sorted(root.glob('*/Scenarios*.java')):
    style = f.parent.name + ('+steps' if 'WithSteps' in f.name else '')
    for name, body in methods(f.read_text()):
        i = p = n = 0
        for s in statements(body):
            a, b = ratio(s); i += a; p += b; n += 1
        rows[name][style] = (i, p, n)

styles = sorted({s for r in rows.values() for s in r})
print('| Test | ' + ' | '.join(styles) + ' |')
print('|---|' + '---|' * len(styles))
tot = collections.defaultdict(lambda: [0, 0])
for name, r in rows.items():
    cells = []
    for s in styles:
        if s in r:
            i, p, n = r[s]; tot[s][0] += i; tot[s][1] += p
            cells.append(f'{100*i//(i+p)}% ({i}/{i+p})')
        else:
            cells.append('–')
    print(f'| `{name}` | ' + ' | '.join(cells) + ' |')
print('| **All four tests** | ' + ' | '.join(
    f'**{100*tot[s][0]//sum(tot[s])}%** ({tot[s][0]}/{sum(tot[s])})' if '+steps' not in s else '(2 tests)' for s in styles) + ' |')
