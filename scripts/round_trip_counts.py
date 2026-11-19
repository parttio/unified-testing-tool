#!/usr/bin/env python3
"""Compares the round trips the Performance Pairs made with the committed round-trip counts, or writes them as the
new committed counts (#341). Run through scripts/bench-unified.sh.

A counts file has one line per user action and counter: "<Test>.<method>:<line> <counter> <count>", where the
committed count may be a range "min-max" for a user action whose count depends on timing (a lazy drop-down's
loading polls). Lines starting with # are comments.

  round_trip_counts.py compare --tool T --committed FILE --sources DIR [--scenario S] [--summary FILE] MEASURED
  round_trip_counts.py update  --tool T --committed FILE --sources DIR [--scenario S] MEASURED...
"""
import argparse
import re
import sys
from pathlib import Path

HEADER = """# Committed round-trip counts of the Performance Pairs on {tool} (#341): one line per user action (the test's
# source line) and counter. scripts/bench-unified.sh --tool {tool} --counts checks them; a count above the committed
# one fails, and one below it asks for this file to be lowered with --update-counts (a range, min-max, is a count that
# depends on timing and is checked against its max only). Raise a count only by editing
# this file, with the reason in the pull request.
"""

# reported but not checked: TestBench's waitForVaadin polls, whose number depends on how busy the page is
REPORT_ONLY = {"waitPolls"}

KEY = re.compile(r"^(?P<test>\w+)\.(?P<method>\w+):(?P<line>\d+)$")


def read(path):
    """{(action, counter): (min, max)} from a counts file."""
    counts = {}
    for raw in Path(path).read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        action, counter, value = line.split()
        low, _, high = value.partition("-")
        counts[(action, counter)] = (int(low), int(high or low))
    return counts


def in_scenario(action, scenario):
    return scenario is None or KEY.match(action).group("method") == scenario


def source(sources, action):
    """The test's source line for an action, to show what the user does."""
    m = KEY.match(action)
    path = Path(sources) / (m.group("test") + ".java")
    try:
        return path.read_text(encoding="utf-8").splitlines()[int(m.group("line")) - 1].strip()
    except (OSError, IndexError):
        return ""


def fmt(span):
    low, high = span
    return str(low) if low == high else f"{low}-{high}"


def compare(args):
    committed = {k: v for k, v in read(args.committed).items() if in_scenario(k[0], args.scenario)} \
        if Path(args.committed).exists() else {}
    measured = read(args.measured[0])
    rows, failures = [], 0
    for key in sorted(set(committed) | set(measured)):
        action, counter = key
        have = measured.get(key)
        want = committed.get(key)
        count = have[0] if have else 0
        if counter in REPORT_ONLY:
            rows.append((action, counter, "–", str(count), "not checked: depends on timing"))
            continue
        if want is None:
            result, failures = "new: not in the committed counts", failures + 1
        elif count > want[1]:
            result, failures = f"**more** (+{count - want[1]})", failures + 1
        elif count < want[0] and want[0] == want[1]:
            result, failures = f"fewer (−{want[0] - count}): lower the committed count", failures + 1
        else:
            result = "ok"
        rows.append((action, counter, fmt(want) if want else "–", str(count) if have else "–", result))

    def table(selected):
        lines = ["| Test | User action | Counter | Committed | Measured | Result |", "|---|---|---|---|---|---|"]
        for action, counter, want, have, result in selected:
            code = source(args.sources, action).replace("|", "\\|")
            lines.append(f"| `{action}` | `{code}` | {counter} | {want} | {have} | {result} |")
        return lines

    out = [f"### Round trips per user action on {args.tool}", ""]
    if not committed:
        out += [f"No committed counts in `{args.committed}`: write them with "
                f"`scripts/bench-unified.sh --tool {args.tool} --update-counts`.", ""]
    if failures:
        out += [f"{failures} of {len(rows)} counts differ from the committed ones:", ""]
        out += table([row for row in rows if row[4] != "ok" and row[1] not in REPORT_ONLY])
    else:
        out.append(f"All {len(rows)} counts match the committed ones.")
    out += ["", "<details><summary>Every user action's counts</summary>", ""] + table(rows) + ["", "</details>"]
    text = "\n".join(out) + "\n"
    print(text)
    if args.summary:
        with open(args.summary, "a", encoding="utf-8") as summary:
            summary.write(text + "\n")
    if failures:
        print(f"Round trips changed on {args.tool}. If the change is intended, run "
              f"scripts/bench-unified.sh --tool {args.tool} --update-counts and commit "
              f"{args.committed}; raise a count only with the reason in the pull request.", file=sys.stderr)
        return 1
    return 0


def update(args):
    runs = [read(path) for path in args.measured]
    measured = {}
    for key in set().union(*runs):
        if key[1] in REPORT_ONLY:
            continue
        values = [run.get(key, (0, 0))[0] for run in runs]
        measured[key] = (min(values), max(values))
    path = Path(args.committed)
    kept = {k: v for k, v in read(path).items() if not in_scenario(k[0], args.scenario)} if path.exists() else {}
    kept.update(measured)
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = [f"{action} {counter} {fmt(span)}" for (action, counter), span in sorted(kept.items())]
    path.write_text(HEADER.format(tool=args.tool) + "\n".join(lines) + "\n", encoding="utf-8")
    varying = sum(1 for low, high in measured.values() if low != high)
    print(f"Wrote {len(measured)} counts from {len(runs)} runs to {path}"
          + (f"; {varying} vary between runs and are written as ranges" if varying else ""))
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=["compare", "update"])
    parser.add_argument("--tool", required=True)
    parser.add_argument("--committed", required=True)
    parser.add_argument("--sources", required=True)
    parser.add_argument("--scenario")
    parser.add_argument("--summary")
    parser.add_argument("measured", nargs="+")
    args = parser.parse_args()
    return compare(args) if args.command == "compare" else update(args)


if __name__ == "__main__":
    sys.exit(main())
