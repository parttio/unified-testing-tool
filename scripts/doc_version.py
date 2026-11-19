#!/usr/bin/env python3
"""Keeps the release version that the user documentation tells readers to use in step with the latest release.

The single source is the newest release notes file, docs/releases/<version>.md. The pages that pin a version
(the README's dependencies, the migration guides' recipe coordinates, the developer guide) must all name it.

  scripts/doc_version.py            check (run by scripts/build-docs.sh): fail if the pages name different versions;
                                    warn if they name an older one than the latest release notes, which is normal
                                    only between writing the notes and the release reaching Maven Central
  scripts/doc_version.py --update   rewrite every pinned version to the latest release

Release notes, research notes, ADRs and the spec are left alone: a version there is history, not an instruction.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
# the pages a reader copies a version from
PAGES = ["README.md", "CONTRIBUTING.md", "docs/comparison.md", "docs/migrating-from-*.md", "docs/guide/*.md"]
# a pre-release version, such as 0.1.0-alpha3: the shape of every release until the Unified API ships with Vaadin
# (spec/10-packaging.md). Plain versions such as 2.0.1 belong to other libraries the pages mention.
VERSION = re.compile(r"(?<![\w.-])\d+\.\d+\.\d+-(?:alpha|beta|rc)\d+(?![\w.-])")
STAGES = {"alpha": 0, "beta": 1, "rc": 2}


def order(version):
    """Sort key: 0.1.0-alpha10 after 0.1.0-alpha9, and a final release after its pre-releases."""
    match = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta|rc)(\d+))?", version)
    if not match:
        return None
    major, minor, patch, stage, number = match.groups()
    pre = (STAGES[stage], int(number)) if stage else (len(STAGES), 0)
    return (int(major), int(minor), int(patch)) + pre


def latest_release():
    versions = [f.stem for f in (ROOT / "docs/releases").glob("*.md") if order(f.stem)]
    if not versions:
        sys.exit("doc_version.py: no release notes in docs/releases/")
    return max(versions, key=order)


def pages():
    return sorted({path for pattern in PAGES for path in ROOT.glob(pattern)})


def main():
    update = sys.argv[1:] == ["--update"]
    if sys.argv[1:] and not update:
        sys.exit(__doc__)
    latest = latest_release()
    found = {}
    for page in pages():
        text = page.read_text(encoding="utf-8")
        for number, line in enumerate(text.splitlines(), 1):
            for version in VERSION.findall(line):
                found.setdefault(version, []).append(f"{page.relative_to(ROOT)}:{number}")
        if update:
            page.write_text(VERSION.sub(latest, text), encoding="utf-8")
    if update:
        print(f"doc_version.py: the pages name {latest}")
        return
    if len(found) > 1:
        print("doc_version.py: the pages name different versions:")
        for version, places in sorted(found.items(), key=lambda item: order(item[0])):
            print(f"  {version}: {', '.join(places)}")
        sys.exit(f"Run scripts/doc_version.py --update to name {latest}, the latest release (docs/releases/).")
    if found and latest not in found:
        named = next(iter(found))
        # a GitHub Actions annotation, shown on the run's page; plain text elsewhere
        print(f"::warning::The documentation names {named}, the latest release notes are for {latest}. "
              "Once it is on Maven Central, run scripts/doc_version.py --update.")


if __name__ == "__main__":
    main()
