#!/usr/bin/env python3
"""Intent share of one Unified UI Test file, for scripts/eval-writing-skill.sh.

Adapted from the PROTOTYPE scorer of the readability study (branch prototype/readable-styles,
spec/readability-prototype/score/score.py), whose token rules come from the noise baseline
(docs/research/unified-ui-test-noise-baseline.md). The token rules are kept as they were; only the input (one test
file instead of a directory of style variants) and the output (JSON) changed.

Every method taking `UnifiedPage page` (tests and User Tasks) is split into statements; each statement's tokens
count as intent (string and number literals, actions, assertions, `is/has/shows/doesNot...` checks, a view class,
the first Wrapper type of the statement, getters) or plumbing (everything else). The intent share is
intent / (intent + plumbing). Comments are ignored.

Run: python3 -I scripts/score_intent.py path/to/SomeTest.java
Prints: {"intent": N, "plumbing": N, "statements": N, "methods": N}
"""
import json
import re
import sys

TOKEN = re.compile(r'''"(?:\\.|[^"\\])*"|\d+(?:\.\d+)?|[A-Za-z_$][\w$]*|->|[^\s\w]''')
ACTION = {'click', 'setValue', 'navigate', 'fillIn', 'clickOn', 'selectItem'}
ASSERT = {'assertEquals', 'assertTrue', 'assertFalse'}
ROLE_FACTORIES = {'dialog', 'textField', 'button', 'notification', 'grid', 'container'}  # style D role nouns


def methods(src):
    for m in re.finditer(r"void (\w+)\(UnifiedPage page[^)]*\) \{", src):
        i, depth = m.end(), 1
        while depth:
            depth += {'{': 1, '}': -1}.get(src[i], 0)
            i += 1
        yield m.group(1), src[m.end():i - 1]


def statements(body):
    out, cur, depth = [], '', 0
    for ch in body:
        depth += {'(': 1, ')': -1}.get(ch, 0)
        if ch == ';' and depth == 0:
            out.append(cur.strip())
            cur = ''
        else:
            cur += ch
    return [s for s in out if s]


def ratio(stmt):
    toks = [t for t in TOKEN.findall(stmt) if re.match(r'["\w$]', t)]
    intent = plumbing = 0
    seen_role = False
    for i, t in enumerate(toks):
        nxt = toks[i + 1] if i + 1 < len(toks) else ''
        if t[0] == '"' or t[0].isdigit():
            intent += 1
        elif t in ACTION or t in ASSERT:
            intent += 1
        elif re.match(r'(is|has|shows|doesNot)[A-Z]', t):
            intent += 1
        elif t.endswith('View') and nxt == 'class':
            intent += 1
        elif nxt == 'class' and not seen_role:
            intent += 1
            seen_role = True
        elif t in ROLE_FACTORIES and not seen_role:
            intent += 1
            seen_role = True
        elif re.match(r'get[A-Z]', t):
            intent += 1
        else:
            plumbing += 1
    return intent, plumbing


def score(path):
    with open(path, encoding='utf-8') as f:
        src = re.sub(r'//[^\n]*', '', f.read())
    intent = plumbing = count = n = 0
    for _, body in methods(src):
        n += 1
        for s in statements(body):
            a, b = ratio(s)
            intent += a
            plumbing += b
            count += 1
    return {'intent': intent, 'plumbing': plumbing, 'statements': count, 'methods': n}


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit('usage: score_intent.py SomeTest.java')
    print(json.dumps(score(sys.argv[1])))
