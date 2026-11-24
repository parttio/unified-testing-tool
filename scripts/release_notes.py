#!/usr/bin/env python3
"""Assembles the release notes from one file per change, so that pull requests adding a note don't conflict.

Each change adds its note as docs/releases/unreleased/<issue>-<slug>.md, such as 564-unified-login.md:

    ---
    section: New
    ---
    - **Login** ([#564](https://github.com/parttio/unified-testing-tool/issues/564)): ...

The body is the bullet as the notes show it, with links written as from docs/releases/ (../adr/..., ../spec/...),
where the assembled page lives. The notes of a section are in issue order.

  scripts/release_notes.py check               fail on a note without a known section or a bullet
  scripts/release_notes.py unreleased [FILE]   write the Unreleased page (stdout without FILE); run by
                                               scripts/build-docs.sh, so the site's Unreleased page is always live
  scripts/release_notes.py release VERSION     write docs/releases/VERSION.md, add it to mkdocs.yml's nav and delete
                                               the notes (CONTRIBUTING.md, "Cutting a release")
"""
import re
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # no __pycache__ in scripts/
sys.path.insert(0, str(Path(__file__).resolve().parent))
from doc_version import latest_release, order  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
RELEASES = ROOT / "docs/releases"
NOTES = RELEASES / "unreleased"
# the headings of the notes, in the order a release shows them
SECTIONS = ["Breaking changes", "New", "Changed behaviour", "Faster", "New settings", "Fixed", "Migration",
            "Known limitations"]
NAME = re.compile(r"(\d+)-[a-z0-9]+(?:-[a-z0-9]+)*\.md")
FRONT_MATTER = re.compile(r"---\n(.*?)\n---\n", re.DOTALL)


def notes():
    """(section, issue, name, bullet) of every note, failing with every problem found."""
    found, problems = [], []
    for path in sorted(NOTES.glob("*.md")):
        if path.name == "README.md":
            continue
        name = NAME.fullmatch(path.name)
        if not name:
            problems.append(f"{path.name}: name it <issue>-<slug>.md, such as 564-unified-login.md")
            continue
        text = path.read_text(encoding="utf-8")
        front = FRONT_MATTER.match(text)
        fields = dict(re.findall(r"^(\w+):\s*(.*?)\s*$", front.group(1), re.MULTILINE)) if front else {}
        section = fields.get("section")
        if section not in SECTIONS:
            problems.append(f"{path.name}: start with front matter `section: <one of {', '.join(SECTIONS)}>`"
                            + (f", not `{section}`" if section else ""))
            continue
        bullet = text[front.end():].strip()
        if not bullet.startswith("- "):
            problems.append(f"{path.name}: after the front matter, write the note as one bullet (`- **...**`)")
            continue
        found.append((section, int(name.group(1)), path.name, bullet))
    if problems:
        sys.exit("release_notes.py: in docs/releases/unreleased/:\n  " + "\n  ".join(problems))
    return found


def sections():
    """The notes as Markdown sections, in the order of SECTIONS, each in issue order."""
    by_section = {}
    for section, issue, name, bullet in sorted(notes(), key=lambda note: (note[1], note[2])):
        by_section.setdefault(section, []).append(bullet)
    return "".join(f"\n## {section}\n\n" + "\n\n".join(by_section[section]) + "\n"
                   for section in SECTIONS if section in by_section)


def unreleased():
    latest = latest_release()
    return (f"# Unreleased\n\n"
            f"What is on `main` and not in a release yet: the changes since [{latest}]({latest}.md). The documentation\n"
            f"site describes `main`, so everything below is in the guide and the specification but not in `{latest}`. This\n"
            f"page becomes the notes of the next alpha.\n"
            + (sections() or "\nNothing yet.\n"))


def release(version):
    if not order(version):
        sys.exit(f"release_notes.py: {version} is not a version such as 0.1.0-alpha5")
    target = RELEASES / f"{version}.md"
    if target.exists():
        sys.exit(f"release_notes.py: {target.relative_to(ROOT)} exists already")
    body = sections()
    if not body:
        sys.exit("release_notes.py: no notes in docs/releases/unreleased/")
    previous = latest_release()
    target.write_text(f"# {version}\n\n"
                      f"<!-- What the release is about, then the \"Get it\" section and \"Known limitations\", as in\n"
                      f"     {previous}.md. The notes below are the changes since {previous}. -->\n" + body,
                      encoding="utf-8")
    mkdocs = ROOT / "mkdocs.yml"
    nav = mkdocs.read_text(encoding="utf-8")
    entry = "      - Unreleased: releases/unreleased.md\n"
    if entry not in nav:
        sys.exit(f"release_notes.py: mkdocs.yml has no `{entry.strip()}` to add {version} after")
    mkdocs.write_text(nav.replace(entry, f"{entry}      - {version}: releases/{version}.md\n"), encoding="utf-8")
    for section, issue, name, bullet in notes():
        (NOTES / name).unlink()
    print(f"release_notes.py: wrote {target.relative_to(ROOT)}, added it to mkdocs.yml, deleted the notes")


def main():
    args = sys.argv[1:]
    if args == ["check"]:
        print(f"release_notes.py: {len(notes())} notes in docs/releases/unreleased/")
    elif args[:1] == ["unreleased"] and len(args) <= 2:
        page = unreleased()
        if len(args) == 2:
            Path(args[1]).write_text(page, encoding="utf-8")
        else:
            sys.stdout.write(page)
    elif args[:1] == ["release"] and len(args) == 2:
        release(args[1])
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
